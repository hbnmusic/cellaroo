package app.cellaroo

import app.cellaroo.ai.AiParsers
import app.cellaroo.ai.GeminiClient
import app.cellaroo.ai.GeminiException
import app.cellaroo.core.BottleStatus
import app.cellaroo.core.PairingTag
import app.cellaroo.core.ServingMode
import app.cellaroo.core.WineType
import app.cellaroo.data.Backup
import app.cellaroo.data.BackupData
import app.cellaroo.data.BottleEntity
import app.cellaroo.data.LocationEntity
import app.cellaroo.data.WineEntity
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class AiParsersTest {
    private val scanJson = """
        ```json
        {"bottles": [
          {"index": 1, "box_2d": [60, 200, 440, 410], "producer": "Viu Manent", "wine_name": "La Capilla Single Vineyard Cabernet Sauvignon",
           "appellation_region": "Valle de Colchagua", "country": "Chile", "vintage": 2022, "wine_type": "RED",
           "primary_grapes": ["Cabernet Sauvignon"], "abv_percent": null, "bottle_size_ml": 750, "sticker_text": null,
           "label_legible": true, "confidence": 0.95},
          {"index": 2, "box_2d": [70, 420, 450, 590], "producer": "Marchesi di Barolo", "wine_name": "Gavi",
           "appellation_region": "Gavi DOCG", "country": "Italy", "vintage": null, "wine_type": "white",
           "primary_grapes": ["Cortese"], "bottle_size_ml": 750, "label_legible": true, "confidence": 0.8},
          {"index": 3, "box_2d": [900, 100, 300, 200], "producer": "Viu Manent", "wine_name": "ViBo Viñedo Centenario",
           "vintage": "NV", "wine_type": "RED", "sticker_text": "93", "label_legible": "false", "confidence": "0.4"},
          {"index": 4, "box_2d": [1, 2, 3], "producer": null, "wine_name": null}
        ]}
        ```
    """.trimIndent()

    @Test fun multiBottleScan() {
        val b = AiParsers.scan(scanJson)
        assertEquals(3, b.size)
        assertEquals(listOf(60, 200, 440, 410), b[0].box)
        assertEquals(2022, b[0].vintage)
        assertEquals(WineType.RED, b[0].type)
        assertNull(b[1].vintage)
        assertEquals(WineType.WHITE, b[1].type)
        // Inverted box is rejected, the bottle is kept without a box.
        assertNull(b[2].box)
        assertNull(b[2].vintage)
        assertEquals("93", b[2].sticker)
        assertEquals(false, b[2].legible)
        assertEquals(0.4, b[2].confidence)
        assertEquals(750, b[2].sizeMl)
    }

    @Test fun emptyAndBrokenAnswers() {
        assertTrue(AiParsers.scan("""{"bottles": []}""").isEmpty())
        assertFailsWith<GeminiException> { AiParsers.scan("Sorry, I cannot help with that.") }
        assertFailsWith<GeminiException> { AiParsers.scan("{ not json }") }
    }

    @Test fun estimateAnswer() {
        val e = AiParsers.estimate(
            """{"window": {"drink_from": 2025, "peak_start": 2024, "peak_end": 2040, "drink_until": 2034},
               "serving_mode": "FOOD", "serving_reason": "Firm tannins.", "serving_temp_c": 17, "decant_minutes": 45,
               "pairings": [{"tag": "ITALIAN", "dish": "Bistecca alla fiorentina"}, {"tag": "Vietnamese", "dish": "Bò lúc lắc"},
                            {"tag": "MARTIAN", "dish": "?"}, {"tag": "ITALIAN", "dish": "dup"}],
               "pairing_notes": "Avoid delicate fish.", "style_summary": "Cherry, leather."}"""
        )
        // Out-of-order peak values are clamped into the drinking window.
        assertEquals(2025, e.window.from)
        assertEquals(2025, e.window.peakStart)
        assertEquals(2034, e.window.peakEnd)
        assertEquals(2034, e.window.until)
        assertEquals(ServingMode.FOOD, e.servingMode)
        assertEquals(listOf(PairingTag.ITALIAN, PairingTag.VIETNAMESE), e.pairings.map { it.tag })
        assertEquals(45, e.decantMinutes)
    }

    @Test fun pasteAnswer() {
        val p = AiParsers.paste(
            """{"identity": {"producer": "Château Léoville-Barton", "wine_name": null, "appellation_region": "Saint-Julien",
                 "country": "France", "vintage": 2016, "wine_type": "RED", "primary_grapes": ["Cabernet Sauvignon", "Merlot"]},
                "scores": [{"source": "WA", "raw": "96+", "score100": 96}, {"source": "Jancis Robinson", "raw": "18.5/20", "score100": 92.5},
                           {"source": "X", "raw": "bad", "score100": 12}],
                "window": {"drink_from": 2028, "peak_start": null, "peak_end": null, "drink_until": 2060},
                "market_price": {"amount": 125.5, "currency": "usd", "per_bottle": true},
                "reference_notes": "Graphite and cassis.", "pairings": [], "serving_mode": null}"""
        )
        assertEquals("Château Léoville-Barton", p.producer)
        assertNull(p.wineName)
        assertEquals(listOf("WA", "JR"), p.scores.map { it.source })
        assertEquals(2060, p.window.until)
        assertEquals(125.5, p.priceAmount)
        assertEquals("USD", p.priceCurrency)
        assertNull(p.servingMode)
    }

    @Test fun geminiEnvelope() {
        val body = """{"candidates": [{"content": {"parts": [{"text": "thinking", "thought": true}, {"text": "{\"bottles\": []}"}]},
            "finishReason": "STOP"}]}"""
        assertEquals("{\"bottles\": []}", GeminiClient.extractText(body))
        assertFailsWith<GeminiException> { GeminiClient.extractText("""{"promptFeedback": {"blockReason": "SAFETY"}}""") }
        assertEquals("API key not valid.", GeminiClient.errorMessage("""{"error": {"code": 400, "message": "API key not valid."}}"""))
        val models = GeminiClient.parseModels(
            """{"models": [{"name": "models/gemini-2.5-flash", "supportedGenerationMethods": ["generateContent"]},
               {"name": "models/embedding-001", "supportedGenerationMethods": ["embedContent"]}]}"""
        )
        assertEquals(listOf("gemini-2.5-flash"), models)
        val req = GeminiClient().requestBody("hi", null, byteArrayOf(1, 2, 3)).toString()
        assertTrue(req.contains("\"inline_data\"") && req.contains("AQID") && req.contains("application/json"))
    }

    @Test fun csvExport() {
        val data = BackupData(
            wines = listOf(WineEntity(id = 1, producer = "Marqués de Riscal", wineName = "Reserva, \"Gran\"", vintage = 2019, drinkUntilYear = 2032)),
            bottles = listOf(BottleEntity(id = 7, wineId = 1, locationId = 3, purchasePriceUsd = 21.99, status = BottleStatus.IN_CELLAR)),
            locations = listOf(LocationEntity(id = 3, name = "Default")),
        )
        val lines = Backup.buildCsv(data).trim().lines()
        assertEquals(2, lines.size)
        assertTrue(lines[0].startsWith("bottle_id,status,producer"))
        assertTrue(lines[1].startsWith("7,IN_CELLAR,Marqués de Riscal,\"Reserva, \"\"Gran\"\"\",2019,RED"))
        assertTrue(lines[1].contains(",Default,21.99,"))
    }
}
