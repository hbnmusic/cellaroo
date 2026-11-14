package app.cellaroo.core

import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class ReadinessTests {
    private val w = DrinkWindow(from = 2025, peakStart = 2027, peakEnd = 2032, until = 2038)

    @Test fun states() {
        assertEquals(Readiness.UNKNOWN, ReadinessCalc.readiness(DrinkWindow(), 2026))
        assertEquals(Readiness.HOLD, ReadinessCalc.readiness(w, 2024))
        assertEquals(Readiness.READY, ReadinessCalc.readiness(w, 2025))
        assertEquals(Readiness.READY, ReadinessCalc.readiness(w, 2026))
        assertEquals(Readiness.AT_PEAK, ReadinessCalc.readiness(w, 2027))
        assertEquals(Readiness.AT_PEAK, ReadinessCalc.readiness(w, 2032))
        assertEquals(Readiness.READY, ReadinessCalc.readiness(w, 2033))
        assertEquals(Readiness.READY, ReadinessCalc.readiness(w, 2038))
        assertEquals(Readiness.PAST_PEAK, ReadinessCalc.readiness(w, 2039))
    }

    @Test fun partialWindows() {
        assertEquals(Readiness.READY, ReadinessCalc.readiness(DrinkWindow(until = 2030), 2026))
        assertEquals(Readiness.PAST_PEAK, ReadinessCalc.readiness(DrinkWindow(until = 2030), 2031))
        assertEquals(Readiness.HOLD, ReadinessCalc.readiness(DrinkWindow(from = 2028), 2026))
        assertEquals(Readiness.READY, ReadinessCalc.readiness(DrinkWindow(from = 2028), 2040))
        // No peak end: peak runs to the end of the window.
        assertEquals(Readiness.AT_PEAK, ReadinessCalc.readiness(DrinkWindow(2025, 2027, null, 2038), 2038))
    }

    @Test fun closingAndVerdicts() {
        assertTrue(ReadinessCalc.windowClosing(w, 2037))
        assertTrue(ReadinessCalc.windowClosing(w, 2038))
        assertFalse(ReadinessCalc.windowClosing(w, 2036))
        assertFalse(ReadinessCalc.windowClosing(w, 2039))
        assertEquals("Hold until 2025.", ReadinessCalc.verdict(w, 2024, 1))
        assertTrue(ReadinessCalc.verdict(w, 2026, 3).startsWith("Ready. Open one now"))
        assertTrue(ReadinessCalc.verdict(w, 2026, 1).contains("better from 2027"))
        assertEquals("At peak. Drink by 2032.", ReadinessCalc.verdict(w, 2030, 2))
        assertTrue(ReadinessCalc.verdict(w, 2038, 2).startsWith("Drink this year"))
        assertTrue(ReadinessCalc.verdict(w, 2040, 2).startsWith("Past its window"))
        assertTrue(ReadinessCalc.verdict(DrinkWindow(), 2026, 1).startsWith("No drinking window"))
    }

    @Test fun tonightRanking() {
        val closing = TonightInput(DrinkWindow(2020, 2022, 2026, 2027), 90.0, ServingMode.FOOD, setOf(PairingTag.ITALIAN))
        val young = TonightInput(DrinkWindow(2025, 2030, 2036, 2040), 95.0, ServingMode.EITHER, setOf(PairingTag.FRENCH))
        assertTrue(TonightRanker.score(closing, 2026, null) > TonightRanker.score(young, 2026, null))
        assertTrue(TonightRanker.score(young, 2026, PairingTag.FRENCH) > TonightRanker.score(young, 2026, PairingTag.THAI))
        assertEquals(0.5, TonightRanker.pairingFit(young, PairingTag.SOLO))
        // A modest wine in its last year outranks a top-rated wine at peak with years to go.
        val lastCall = TonightInput(DrinkWindow(2024, null, null, 2027), null, null, emptySet())
        val star = TonightInput(DrinkWindow(2022, 2024, 2030, 2034), 98.0, ServingMode.EITHER, emptySet())
        assertTrue(TonightRanker.score(lastCall, 2026, null) > TonightRanker.score(star, 2026, null))
        assertFalse(TonightRanker.isCandidate(DrinkWindow(from = 2030), 2026))
        assertTrue(TonightRanker.isCandidate(DrinkWindow(until = 2030), 2026))
    }

    @Test fun boxesAndCsv() {
        assertTrue(Boxes.isValid(listOf(120, 45, 890, 260)))
        assertFalse(Boxes.isValid(listOf(120, 45, 890)))
        assertFalse(Boxes.isValid(listOf(890, 45, 120, 260)))
        assertFalse(Boxes.isValid(listOf(0, 0, 1001, 10)))
        assertFalse(Boxes.isValid(null))
        assertEquals("a,\"b,c\",\"say \"\"hi\"\"\",", Csv.line(listOf("a", "b,c", "say \"hi\"", null)))
    }
}

class PasteParserTests {
    private val cellarTracker = """
        2019 Ciacci Piccolomini d'Aragona Brunello di Montalcino Pianrosso
        Sangiovese from Italy, Tuscany, Montalcino, Brunello di Montalcino
        CT93.4 (212 notes)   WA 95   Vinous 96   JS 97   WS 94
        Drinking Windows: Drink between 2026 and 2040 (based on 31 user opinions)
        Community Tasting Notes (212) average 93.4 points
        Auction price: ${'$'}68.50   Community average value: ${'$'}72.14
    """.trimIndent()

    private val wineSearcher = """
        2016 Chateau Leoville-Barton, Saint-Julien, France
        Critics Score 96/100 · User Rating 4.5 stars
        Avg Price (ex-tax) ${'$'}1,250 / 750ml
        Wine Advocate: 96+ points. Best 2028 through 2060.
        Jancis Robinson 18.5/20
    """.trimIndent()

    private val vivino = """
        Marqués de Riscal Reserva Rioja 2019
        4.2
        18,450 ratings
        Add to cart ${'$'}24.99
        Pairs well with beef, lamb and veal.
    """.trimIndent()

    private val retailer = """
        Your order: Domaine Tempier Bandol Rosé 2023, case of 6 ... ${'$'}270.00
        93-95 pts Vinous. Drink now through 2028. Hold until 2025 for best results.
        Also: NV Krug Grande Cuvée €230.
    """.trimIndent()

    @Test fun cellarTrackerSnippet() {
        val r = PasteParser.parse(cellarTracker, 2026)
        assertEquals(2019, r.vintage)
        assertEquals(2026, r.window.from)
        assertEquals(2040, r.window.until)
        val bySource = r.scores.associateBy { it.source }
        assertEquals(95.0, bySource["WA"]?.score100)
        assertEquals(96.0, bySource["VINOUS"]?.score100)
        assertEquals(97.0, bySource["JS"]?.score100)
        assertEquals(94.0, bySource["WS"]?.score100)
        assertEquals(93.4, bySource["CT"]?.score100)
        assertEquals(72.14, r.bestUsdPrice?.perBottle)
    }

    @Test fun wineSearcherSnippet() {
        val r = PasteParser.parse(wineSearcher, 2026)
        assertEquals(2016, r.vintage)
        assertEquals(2028, r.window.from)
        assertEquals(2060, r.window.until)
        assertEquals(1250.0, r.bestUsdPrice?.perBottle)
        assertTrue(r.bestUsdPrice!!.isAverage)
        val jr = r.scores.first { it.source == "JR" }
        assertEquals(92.5, jr.score100)
        val wa = r.scores.first { it.source == "WA" }
        assertEquals(96.0, wa.score100)
        assertTrue(r.scores.any { it.source == "VIVINO" && it.score100 == 90.0 })
        // The unattributed "96/100" repeats the Wine Advocate score, so it is dropped.
        assertFalse(r.scores.any { it.source == "OTHER" })
    }

    @Test fun vivinoSnippet() {
        val r = PasteParser.parse(vivino, 2026)
        assertEquals(2019, r.vintage)
        assertEquals(84.0, r.scores.single().score100)
        assertEquals("VIVINO", r.scores.single().source)
        assertEquals(24.99, r.bestUsdPrice?.perBottle)
        assertTrue(r.window.isEmpty)
    }

    @Test fun retailerSnippet() {
        val r = PasteParser.parse(retailer, 2026)
        assertTrue(r.nonVintage)
        assertNull(r.vintage)
        assertEquals(2026, r.window.from)
        assertEquals(2028, r.window.until)
        val s = r.scores.single()
        assertEquals("VINOUS", s.source)
        assertEquals(94.0, s.score100)
        assertEquals(93.0, s.low100)
        assertEquals(95.0, s.high100)
        assertEquals(45.0, r.prices.first { it.currency == "USD" }.perBottle)
        assertEquals("EUR", r.prices.last().currency)
    }

    @Test fun windowPhrases() {
        fun w(s: String) = PasteParser.parseWindow(s, 2026)
        assertEquals(DrinkWindow(2026, null, null, 2038), w("Drink 2026-2038"))
        assertEquals(DrinkWindow(2026, null, null, 2038), w("Drinking window: 2026 – 2038"))
        assertEquals(DrinkWindow(2027, null, null, 2045), w("Best from 2027 through 2045"))
        assertEquals(DrinkWindow(2026, null, null, 2030), w("Drink now–2030"))
        assertEquals(DrinkWindow(2027, null, null, null), w("Hold until 2027"))
        assertEquals(DrinkWindow(null, null, null, 2031), w("Drink by 2031"))
        assertEquals(DrinkWindow(2024, 2027, 2032, 2040), w("Drink 2024-2040, peak 2027-2032"))
        assertTrue(w("Vintages 2019-2021 were excellent").isEmpty)
        assertEquals(2026, w("Drink now.").from)
    }

    @Test fun scoresDoNotEatYearsOrStates() {
        assertTrue(PasteParser.parseScores("Columbia Valley, WA 2019").isEmpty())
        assertTrue(PasteParser.parseScores("We bought 12 bottles in 2020").isEmpty())
        assertEquals(95.0, PasteParser.parseScores("95 points").single().score100)
        assertEquals("JD", PasteParser.parseScores("97 pts Jeb Dunnuck").single().source)
        assertEquals("OTHER", ScoreSources.code("Some Blog"))
        assertEquals("WA", ScoreSources.code("Robert Parker's Wine Advocate"))
    }
}

class MatchingTests {
    private val wines = listOf(
        WineRef(1, "Viu Manent", "La Capilla Single Vineyard Cabernet Sauvignon", 2022),
        WineRef(2, "Viu Manent", "ViBo Viñedo Centenario", 2021),
        WineRef(3, "Marqués de Riscal", "Reserva", 2021),
        WineRef(4, "Marqués de Riscal", "Reserva", 2019),
        WineRef(5, "Marchesi di Barolo", "Gavi", null),
    )

    @Test fun keysAndFolding() {
        assertEquals("marques de riscal|reserva|2021|750", Matching.normalizedKey("Marqués de Riscal", "Reserva", 2021, 750))
        assertEquals("krug|grande cuvee|nv|750", Matching.normalizedKey("Krug", "Grande Cuvée", null, 750))
        assertTrue(Matching.similarity("Château Léoville-Barton", "Chateau Leoville Barton") > 0.99)
    }

    @Test fun exactAndFuzzy() {
        val m = Matching.match(Detection(1, "VIU MANENT", "La Capilla Cabernet Sauvignon", 2022), wines)
        assertEquals(MatchKind.MATCH, m.kind); assertEquals(1L, m.wineId)
        val typo = Matching.match(Detection(2, "Marques de Riscol", "Rioja Reserva", 2019), wines)
        assertEquals(MatchKind.MATCH, typo.kind); assertEquals(4L, typo.wineId)
        assertEquals(MatchKind.UNKNOWN, Matching.match(Detection(3, "Luis R", "Rioja Crianza", 2021), wines).kind)
    }

    @Test fun vintageRules() {
        val wrong = Matching.match(Detection(1, "Marqués de Riscal", "Reserva", 2020), wines)
        assertEquals(MatchKind.REVIEW, wrong.kind)
        assertNotNull(wrong.wineId)
        val none = Matching.match(Detection(2, "Marqués de Riscal", "Reserva", null), wines)
        assertEquals(MatchKind.REVIEW, none.kind)
        val nv = Matching.match(Detection(3, "Marchesi di Barolo", "Gavi", null), wines)
        assertEquals(MatchKind.MATCH, nv.kind); assertEquals(5L, nv.wineId)
        val shaky = Matching.match(Detection(4, "Viu Manent", "ViBo Vinedo Centenario", 2021, confidence = 0.4), wines)
        assertEquals(MatchKind.REVIEW, shaky.kind); assertEquals(2L, shaky.wineId)
    }

    @Test fun reconcilePlan() {
        val bottles = listOf(
            BottleRef(10, 1, 100), BottleRef(11, 1, 100),   // two La Capilla on shelf 100
            BottleRef(12, 2, 100),                          // one ViBo on shelf 100
            BottleRef(13, 3, 200),                          // Riscal 2021 on another shelf
            BottleRef(14, 5, null),
        )
        val dets = listOf(
            Detection(1, "Viu Manent", "La Capilla Single Vineyard Cabernet Sauvignon", 2022),
            Detection(2, "Marqués de Riscal", "Reserva", 2021),
            Detection(3, "Luis R", "Rioja Crianza", 2021),
            Detection(4, "Marqués de Riscal", "Reserva", null),
        )
        val p = Matching.plan(dets, wines, bottles, 100)
        assertEquals(1, p.matchedCount)
        assertEquals(setOf(11L, 12L), p.missing.map { it.bottleId }.toSet())
        assertEquals(1, p.extras.size)
        assertEquals(13L, p.extras.single().moveFrom?.bottleId)
        assertEquals(listOf(3), p.unknown.map { it.index })
        assertEquals(listOf(4), p.review.map { it.detection.index })
        assertFalse(p.isClean)

        val p2 = Matching.plan(dets, wines, bottles, 100, mapOf(4 to Resolution.IsWine(3), 3 to Resolution.Ignore))
        assertEquals(2, p2.extras.size)
        assertNull(p2.extras[1].moveFrom)
        assertTrue(p2.isClean)
    }
}
