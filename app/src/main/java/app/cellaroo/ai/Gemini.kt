package app.cellaroo.ai

import app.cellaroo.core.Boxes
import app.cellaroo.core.DrinkWindow
import app.cellaroo.core.Pairing
import app.cellaroo.core.PairingTag
import app.cellaroo.core.ParsedScore
import app.cellaroo.core.ScoreSources
import app.cellaroo.core.ServingMode
import app.cellaroo.core.WineType
import app.cellaroo.data.AppJson
import java.util.Base64
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class GeminiException(val code: Int, message: String) : Exception(message) {
    /** Text that can be shown to the user as is. */
    val userMessage: String
        get() = when {
            code == 429 -> "Gemini's free quota is used up for now. Per-minute limits clear within a minute; daily limits reset at midnight Pacific (3 AM Eastern)."
            code == 401 || code == 403 || (code == 400 && message.orEmpty().contains("API key", true)) ->
                "Gemini rejected the API key. Check it in Settings."
            code == 404 -> "The selected Gemini model is not available. Pick another model in Settings."
            code == 503 || code == 500 -> "Gemini is overloaded right now. Try again in a moment."
            code == -1 -> "No connection to Gemini. Check your internet and try again."
            else -> message ?: "Gemini request failed ($code)."
        }
}

/** Thin REST client for the Gemini generateContent API. */
class GeminiClient(
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(150, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build(),
    private val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
) {
    fun requestBody(prompt: String, schema: JsonElement?, jpeg: ByteArray?): JsonObject = buildJsonObject {
        putJsonArray("contents") {
            addJsonObject {
                put("role", "user")
                putJsonArray("parts") {
                    if (jpeg != null) addJsonObject {
                        putJsonObject("inline_data") {
                            put("mime_type", "image/jpeg")
                            put("data", Base64.getEncoder().encodeToString(jpeg))
                        }
                    }
                    addJsonObject { put("text", prompt) }
                }
            }
        }
        putJsonObject("generationConfig") {
            put("temperature", 0.1)
            put("responseMimeType", "application/json")
            if (schema != null) put("responseSchema", schema)
        }
    }

    /** Returns the model's text answer. Throws [GeminiException] on any failure. */
    suspend fun generate(apiKey: String, model: String, prompt: String, schema: JsonElement?, jpeg: ByteArray?): String =
        withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url("$baseUrl/models/${model.removePrefix("models/")}:generateContent")
                .header("x-goog-api-key", apiKey)
                .post(requestBody(prompt, schema, jpeg).toString().toRequestBody("application/json".toMediaType()))
                .build()
            val (code, text) = execute(req)
            if (code !in 200..299) throw GeminiException(code, errorMessage(text) ?: "HTTP $code")
            extractText(text)
        }

    /** Names of models that support generateContent, without the "models/" prefix. */
    suspend fun listModels(apiKey: String): List<String> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url("$baseUrl/models?pageSize=200").header("x-goog-api-key", apiKey).get().build()
        val (code, text) = execute(req)
        if (code !in 200..299) throw GeminiException(code, errorMessage(text) ?: "HTTP $code")
        parseModels(text)
    }

    private fun execute(req: Request): Pair<Int, String> = try {
        http.newCall(req).execute().use { it.code to (it.body?.string().orEmpty()) }
    } catch (e: java.io.IOException) {
        throw GeminiException(-1, e.message ?: "Network error")
    }

    companion object {
        fun errorMessage(body: String): String? = runCatching {
            (AppJson.parseToJsonElement(body) as? JsonObject)?.obj("error")?.str("message")
        }.getOrNull()

        fun parseModels(body: String): List<String> {
            val root = AppJson.parseToJsonElement(body) as? JsonObject ?: return emptyList()
            return root.arr("models").mapNotNull { it as? JsonObject }
                .filter { m -> m.arr("supportedGenerationMethods").any { (it as? JsonPrimitive)?.content == "generateContent" } }
                .mapNotNull { it.str("name")?.removePrefix("models/") }
                .filter { it.startsWith("gemini") }
        }

        /** Pulls the answer text out of a generateContent response, skipping thinking parts. */
        fun extractText(body: String): String {
            val root = runCatching { AppJson.parseToJsonElement(body) as? JsonObject }.getOrNull()
                ?: throw GeminiException(0, "Gemini returned an unreadable response.")
            val candidate = root.arr("candidates").firstOrNull() as? JsonObject
            if (candidate == null) {
                val reason = root.obj("promptFeedback")?.str("blockReason")
                throw GeminiException(0, if (reason != null) "Gemini blocked this request ($reason)." else "Gemini returned no answer.")
            }
            val text = candidate.obj("content")?.arr("parts").orEmpty()
                .mapNotNull { it as? JsonObject }
                .filter { it["thought"].bool() != true }
                .mapNotNull { it.str("text") }
                .joinToString("")
            if (text.isBlank()) {
                throw GeminiException(0, "Gemini returned an empty answer (${candidate.str("finishReason") ?: "no reason given"}).")
            }
            return text
        }
    }
}

// ---- Lenient JSON helpers: models do not always honour the schema, so nothing here throws. ----

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
internal fun JsonObject.arr(key: String): List<JsonElement> = (this[key] as? JsonArray).orEmpty()
internal fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", true) }
internal fun JsonElement?.num(): Double? {
    val p = this as? JsonPrimitive ?: return null
    if (p is JsonNull) return null
    return p.doubleOrNull ?: Regex("-?\\d+(?:\\.\\d+)?").find(p.content)?.value?.toDoubleOrNull()
}
internal fun JsonElement?.bool(): Boolean? {
    val p = this as? JsonPrimitive ?: return null
    if (p is JsonNull) return null
    return p.booleanOrNull ?: when (p.content.lowercase()) { "true", "yes" -> true; "false", "no" -> false; else -> null }
}
internal fun JsonElement?.year(): Int? = num()?.toInt()?.takeIf { it in 1900..2150 }

data class DetectedBottle(
    val index: Int,
    /** [ymin, xmin, ymax, xmax] on a 0–1000 scale, or null when the model's box was unusable. */
    val box: List<Int>?,
    val producer: String,
    val wineName: String,
    val region: String,
    val country: String,
    val vintage: Int?,
    val type: WineType,
    val grapes: List<String>,
    val abv: Float?,
    val sizeMl: Int,
    val sticker: String?,
    val legible: Boolean,
    val confidence: Double,
)

data class PasteAi(
    val producer: String?,
    val wineName: String?,
    val region: String?,
    val country: String?,
    val vintage: Int?,
    val type: WineType?,
    val grapes: List<String>,
    val scores: List<ParsedScore>,
    val window: DrinkWindow,
    val priceAmount: Double?,
    val priceCurrency: String?,
    val pricePerBottle: Boolean,
    val referenceNotes: String?,
    val pairings: List<Pairing>,
    val servingMode: ServingMode?,
)

data class EstimateAi(
    val window: DrinkWindow,
    val servingMode: ServingMode?,
    val servingReason: String?,
    val servingTempC: Int?,
    val decantMinutes: Int?,
    val pairings: List<Pairing>,
    val pairingNotes: String?,
    val styleSummary: String?,
)

object AiParsers {
    /** Finds the JSON object inside a model answer, tolerating code fences and stray prose. */
    fun jsonObject(text: String): JsonObject {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) throw GeminiException(0, "Gemini's answer was not JSON.")
        return runCatching { AppJson.parseToJsonElement(text.substring(start, end + 1)) as JsonObject }
            .getOrElse { throw GeminiException(0, "Gemini's answer was not valid JSON.") }
    }

    private fun pairings(list: List<JsonElement>): List<Pairing> = list.mapNotNull { it as? JsonObject }
        .mapNotNull { o -> PairingTag.parse(o.str("tag"))?.takeIf { it != PairingTag.SOLO }?.let { Pairing(it, o.str("dish")) } }
        .distinctBy { it.tag }

    private fun window(o: JsonObject?): DrinkWindow {
        if (o == null) return DrinkWindow()
        var from = o["drink_from"].year()
        var until = o["drink_until"].year()
        var ps = o["peak_start"].year()
        var pe = o["peak_end"].year()
        if (from != null && until != null && until < from) until = null
        if (ps != null && pe != null && pe < ps) pe = null
        if (from != null && ps != null && ps < from) ps = from
        if (until != null && pe != null && pe > until) pe = until
        if (from == null && until == null && ps == null && pe == null) from = null
        return DrinkWindow(from, ps, pe, until)
    }

    fun scan(text: String): List<DetectedBottle> {
        val root = jsonObject(text)
        return root.arr("bottles").mapNotNull { it as? JsonObject }.mapIndexedNotNull { i, o ->
            val producer = o.str("producer").orEmpty()
            val name = o.str("wine_name").orEmpty()
            val box = o.arr("box_2d").mapNotNull { it.num()?.toInt() }.takeIf { Boxes.isValid(it) }
            if (producer.isEmpty() && name.isEmpty() && box == null) return@mapIndexedNotNull null
            DetectedBottle(
                index = i + 1,
                box = box,
                producer = producer,
                wineName = name,
                region = o.str("appellation_region").orEmpty(),
                country = o.str("country").orEmpty(),
                vintage = o["vintage"].year(),
                type = WineType.parse(o.str("wine_type")),
                grapes = o.arr("primary_grapes").mapNotNull { (it as? JsonPrimitive)?.content?.trim()?.takeIf { s -> s.isNotEmpty() } },
                abv = o["abv_percent"].num()?.toFloat()?.takeIf { it in 0.5f..25f },
                sizeMl = o["bottle_size_ml"].num()?.toInt()?.takeIf { it in 100..18000 } ?: 750,
                sticker = o.str("sticker_text"),
                legible = o["label_legible"].bool() ?: true,
                confidence = (o["confidence"].num() ?: 0.5).coerceIn(0.0, 1.0),
            )
        }
    }

    fun paste(text: String): PasteAi {
        val root = jsonObject(text)
        val id = root.obj("identity")
        val price = root.obj("market_price")
        val scores = root.arr("scores").mapNotNull { it as? JsonObject }.mapNotNull { o ->
            val s = o["score100"].num() ?: return@mapNotNull null
            if (s < 50 || s > 100) return@mapNotNull null
            ParsedScore(ScoreSources.code(o.str("source")), o.str("raw") ?: s.toString(), s)
        }
        return PasteAi(
            producer = id?.str("producer"),
            wineName = id?.str("wine_name"),
            region = id?.str("appellation_region"),
            country = id?.str("country"),
            vintage = id?.get("vintage").year(),
            type = id?.str("wine_type")?.let { WineType.parse(it) },
            grapes = id?.arr("primary_grapes").orEmpty().mapNotNull { (it as? JsonPrimitive)?.content?.trim()?.takeIf { s -> s.isNotEmpty() } },
            scores = scores,
            window = window(root.obj("window")),
            priceAmount = price?.get("amount").num()?.takeIf { it > 0 },
            priceCurrency = price?.str("currency")?.uppercase(),
            pricePerBottle = price?.get("per_bottle").bool() ?: true,
            referenceNotes = root.str("reference_notes"),
            pairings = pairings(root.arr("pairings")),
            servingMode = ServingMode.parse(root.str("serving_mode")),
        )
    }

    fun estimate(text: String): EstimateAi {
        val root = jsonObject(text)
        return EstimateAi(
            window = window(root.obj("window")),
            servingMode = ServingMode.parse(root.str("serving_mode")),
            servingReason = root.str("serving_reason"),
            servingTempC = root["serving_temp_c"].num()?.toInt()?.takeIf { it in 2..25 },
            decantMinutes = root["decant_minutes"].num()?.toInt()?.takeIf { it in 0..600 },
            pairings = pairings(root.arr("pairings")),
            pairingNotes = root.str("pairing_notes"),
            styleSummary = root.str("style_summary"),
        )
    }
}
