package app.cellaroo.ai

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.cellaroo.CellarooApp
import app.cellaroo.core.Source
import app.cellaroo.data.AppJson
import app.cellaroo.data.CellarRepository
import app.cellaroo.data.Settings
import app.cellaroo.data.WineEntity
import app.cellaroo.data.decodeStrings
import app.cellaroo.data.encodePairings
import java.util.Calendar
import kotlinx.coroutines.delay

fun currentYear(): Int = Calendar.getInstance().get(Calendar.YEAR)

/** The three AI calls the app makes. Prompts and schemas live in assets/prompts so CI can reuse them. */
class AiService(private val context: Context, private val settings: Settings, private val client: GeminiClient) {

    private fun asset(name: String): String = context.assets.open("prompts/$name").bufferedReader().use { it.readText() }

    /** Returns the answer text and the model that produced it. */
    private suspend fun call(promptFile: String, schemaFile: String, extra: String, jpeg: ByteArray?): Pair<String, String> {
        val key = settings.apiKey() ?: throw GeminiException(401, "Add your Gemini API key in Settings first.")
        val prompt = asset(promptFile) + extra
        val schema = AppJson.parseToJsonElement(asset(schemaFile))
        val models = listOf(settings.state.value.model, Settings.FALLBACK_MODEL).distinct()
        var last: GeminiException? = null
        for (model in models) {
            try {
                return client.generate(key, model, prompt, schema, jpeg) to model
            } catch (e: GeminiException) {
                last = e
                when {
                    e.code == 404 -> continue
                    // A 400 that is not about the key usually means the schema was rejected: retry without it.
                    e.code == 400 && !e.message.orEmpty().contains("API key", true) -> {
                        try {
                            return client.generate(key, model, prompt, null, jpeg) to model
                        } catch (e2: GeminiException) {
                            last = e2
                            if (e2.code != 404) throw e2
                        }
                    }
                    else -> throw e
                }
            }
        }
        throw last ?: GeminiException(0, "No Gemini model is available.")
    }

    suspend fun scan(jpeg: ByteArray): Triple<List<DetectedBottle>, String, String> {
        val (text, model) = call("scan_prompt.txt", "scan_schema.json", "", jpeg)
        return Triple(AiParsers.scan(text), text, model)
    }

    suspend fun parsePaste(pasted: String): Pair<PasteAi, String> {
        val extra = "\n\nCurrent year: ${currentYear()}\n\nTEXT:\n" + pasted.take(12000)
        val (text, _) = call("paste_prompt.txt", "paste_schema.json", extra, null)
        return AiParsers.paste(text) to text
    }

    suspend fun estimate(w: WineEntity): EstimateAi {
        val extra = buildString {
            append("\n\nCurrent year: ${currentYear()}\n\nWINE:\n")
            append("Producer: ${w.producer}\nWine: ${w.wineName}\n")
            append("Vintage: ${w.vintage ?: "non-vintage"}\nRegion: ${w.appellationRegion}\nCountry: ${w.country}\n")
            append("Type: ${w.wineType}\nGrapes: ${decodeStrings(w.grapesJson).joinToString(", ")}\n")
            append("Bottle size: ${w.bottleSizeMl} ml\n")
        }
        val (text, _) = call("estimate_prompt.txt", "estimate_schema.json", extra, null)
        return AiParsers.estimate(text)
    }

    suspend fun listModels(): List<String> {
        val key = settings.apiKey() ?: throw GeminiException(401, "Add your Gemini API key in Settings first.")
        return client.listModels(key)
    }
}

/** Fills in AI estimates. Pasted or hand-entered values are never overwritten. */
class Enricher(private val repo: CellarRepository, private val ai: AiService) {

    fun needsEstimate(w: WineEntity): Boolean =
        w.drinkFromYear == null && w.drinkUntilYear == null && w.peakStartYear == null && w.windowSource == null

    suspend fun estimateAndApply(wineId: Long): Boolean {
        val w = repo.dao.wine(wineId) ?: return false
        val e = ai.estimate(w)
        val fresh = repo.dao.wine(wineId) ?: return false
        var out = fresh
        val canWindow = fresh.windowSource == null || fresh.windowSource == Source.AI_ESTIMATE
        if (canWindow && !e.window.isEmpty) {
            out = out.copy(
                drinkFromYear = e.window.from, peakStartYear = e.window.peakStart, peakEndYear = e.window.peakEnd,
                drinkUntilYear = e.window.until, windowSource = Source.AI_ESTIMATE, windowUpdatedAt = System.currentTimeMillis(),
            )
        }
        if ((fresh.servingSource == null || fresh.servingSource == Source.AI_ESTIMATE) && e.servingMode != null) {
            out = out.copy(
                servingMode = e.servingMode, servingReason = e.servingReason, servingTempC = e.servingTempC,
                decantMinutes = e.decantMinutes, servingSource = Source.AI_ESTIMATE,
            )
        }
        if ((fresh.pairingSource == null || fresh.pairingSource == Source.AI_ESTIMATE) && e.pairings.isNotEmpty()) {
            out = out.copy(pairingsJson = encodePairings(e.pairings), pairingNotes = e.pairingNotes, pairingSource = Source.AI_ESTIMATE)
        }
        if (e.styleSummary != null && fresh.styleSummary == null) out = out.copy(styleSummary = e.styleSummary)
        repo.updateWine(out)
        return true
    }
}

/** Background job: estimate every wine in the cellar that has no drinking window yet. */
class EstimateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val c = (applicationContext as CellarooApp).container
        if (c.settings.apiKey() == null || !c.settings.state.value.estimatesEnabled) return Result.success()
        val inCellar = c.repo.dao.bottles().filter { it.status == app.cellaroo.core.BottleStatus.IN_CELLAR }.map { it.wineId }.toSet()
        val todo = c.repo.dao.wines().filter { it.id in inCellar && c.enricher.needsEstimate(it) }
        for (w in todo) {
            try {
                c.enricher.estimateAndApply(w.id)
            } catch (e: GeminiException) {
                if (e.code == 429 || e.code == -1 || e.code >= 500) return if (runAttemptCount < 4) Result.retry() else Result.failure()
                if (e.code == 400 || e.code == 401 || e.code == 403 || e.code == 404) return Result.failure()
            } catch (e: Exception) {
                // Skip this wine and carry on with the rest.
            }
            delay(4500) // stay under the free tier's per-minute limit
        }
        return Result.success()
    }

    companion object {
        const val NAME = "estimate_all"

        fun enqueue(context: Context) {
            val req = OneTimeWorkRequestBuilder<EstimateWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NAME, ExistingWorkPolicy.KEEP, req)
        }
    }
}
