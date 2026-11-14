package app.cellaroo.core

data class ParsedScore(
    val source: String,
    val raw: String,
    val score100: Double,
    val low100: Double? = null,
    val high100: Double? = null,
) {
    val sourceLabel: String get() = ScoreSources.label(source)
}

data class ParsedPrice(
    val amount: Double,
    val currency: String,
    val perBottle: Double,
    val isAverage: Boolean,
    val raw: String,
)

data class PasteExtract(
    val vintage: Int?,
    val nonVintage: Boolean,
    val window: DrinkWindow,
    val scores: List<ParsedScore>,
    val prices: List<ParsedPrice>,
) {
    /** Market price candidate: an average/market quote if present, else the first USD price. */
    val bestUsdPrice: ParsedPrice?
        get() {
            val usd = prices.filter { it.currency == "USD" }
            return usd.firstOrNull { it.isAverage } ?: usd.firstOrNull()
        }
}

object ScoreSources {
    val labels: Map<String, String> = linkedMapOf(
        "WA" to "Wine Advocate", "VINOUS" to "Vinous", "JS" to "James Suckling",
        "WS" to "Wine Spectator", "DECANTER" to "Decanter", "JR" to "Jancis Robinson",
        "JD" to "Jeb Dunnuck", "WE" to "Wine Enthusiast", "VIVINO" to "Vivino",
        "CT" to "CellarTracker", "OTHER" to "Other",
    )

    fun label(code: String): String = labels[code] ?: code

    private val longNames = listOf(
        "wine advocate" to "WA", "robert parker" to "WA", "parker" to "WA",
        "vinous" to "VINOUS", "galloni" to "VINOUS",
        "suckling" to "JS", "wine spectator" to "WS", "decanter" to "DECANTER",
        "jancis" to "JR", "dunnuck" to "JD", "wine enthusiast" to "WE",
        "vivino" to "VIVINO", "cellartracker" to "CT", "cellar tracker" to "CT", "community tasting" to "CT",
    )
    private val abbreviations = mapOf(
        "WA" to "WA", "RP" to "WA", "AG" to "VINOUS", "VM" to "VINOUS", "JS" to "JS",
        "WS" to "WS", "JD" to "JD", "WE" to "WE", "JR" to "JR", "CT" to "CT",
    )

    /** Maps a free-text source name or abbreviation to a source code; OTHER when unknown. */
    fun code(text: String?): String {
        val t = text?.trim().orEmpty()
        if (t.isEmpty()) return "OTHER"
        labels.keys.firstOrNull { it == t.uppercase() }?.let { return it }
        val l = t.lowercase()
        longNames.firstOrNull { l.contains(it.first) }?.let { return it.second }
        return abbreviations[t] ?: "OTHER"
    }

    /** Converts a raw score on its native scale to 0–100. */
    fun to100(value: Double, scaleMax: Int): Double = when (scaleMax) {
        20 -> value * 5.0
        5 -> value * 20.0
        else -> value
    }
}

object PasteParser {
    private const val SRC =
        "(?:(?i:Wine Advocate|Robert Parker|Parker|Vinous|Antonio Galloni|Galloni|James Suckling|Suckling|" +
            "Wine Spectator|Decanter|Jancis Robinson|Jeb Dunnuck|Dunnuck|Wine Enthusiast|Vivino|Cellar ?Tracker)" +
            "|WA|RP|AG|VM|JS|WS|JD|WE|JR|CT)"
    private const val Y = "(?:19|20)\\d{2}"
    private const val YF = "(?:20|21)\\d{2}"

    private val twentyRe = Regex("(?<![\\d.])(\\d{1,2}(?:\\.\\d{1,2})?)\\s*/\\s*20\\b")
    private val starsRe = Regex("(?i)(?<![\\d.])([1-5]\\.\\d)\\s*(?:/\\s*5\\b|stars?\\b|★|out of 5)")
    private val vivinoRatingsRe = Regex("(?i)(?<![\\d.])([1-5]\\.\\d)\\s*\\n?\\s*[\\d,.]+\\s*k?\\s*ratings")
    private val hundredRe = Regex("(?<![\\d.])(\\d{2,3})\\s*/\\s*100\\b")
    private val sourceFirstRe = Regex(
        "\\b($SRC)(?![a-zA-Z])(?:'s)?\\s*(?i:score|rating)?\\s*[:\\-–]?\\s*\\(?" +
            "(\\d{2,3}(?:\\.\\d)?)(?!\\d)(?:\\s*[-–]\\s*(\\d{2,3})(?!\\d))?(\\+)?\\)?(?:\\s*(?i:pts?|points?))?"
    )
    private val scoreFirstRe = Regex(
        "(?<![\\d.€£\\$])(\\d{2,3}(?:\\.\\d)?)(?:\\s*[-–]\\s*(\\d{2,3}))?(\\+)?\\s*(?i:pts?\\.?|points?)" +
            "(?![a-zA-Z])(?:\\s*(?:[-–,:|(]|(?i:from|by))?\\s*($SRC)\\b)?"
    )

    private val rangeRe = Regex(
        "(?i)\\b(drink(?:ing)?(?:\\s+window)?|best|enjoy|maturity|mature|window|peak)\\b[^0-9\\n]{0,30}?" +
            "\\b($Y|now)\\s*(?:-|–|—|to|through|thru|until|till|and)\\s*($YF)\\b"
    )
    private val untilRe = Regex("(?i)\\bdrink\\b[^0-9\\n]{0,20}?\\b(?:through|thru|until|till|by|before)\\s+($YF)\\b")
    private val fromRe = Regex(
        "(?i)\\b(?:hold(?:\\s+(?:until|till))?|best\\s+after|drink\\s+(?:from|after)|cellar\\s+until|wait\\s+until|" +
            "(?:ready|approachable)\\s+(?:from|in|by))\\s+($YF)\\b"
    )
    private val drinkNowRe = Regex("(?i)\\bdrink(?:ing)?\\s+now\\b")

    private val yearRe = Regex("\\b($Y)\\b")
    private val nvRe = Regex("\\bNV\\b|(?i:non[- ]vintage)")

    private val priceRe = Regex("(US\\$|\\$|USD|€|EUR|£|GBP)\\s?(\\d{1,3}(?:,\\d{3})+(?:\\.\\d{1,2})?|\\d+(?:\\.\\d{1,2})?)")
    private val caseRes = listOf(
        Regex("(?i)case of (\\d{1,2})"),
        Regex("(?i)(\\d{1,2})\\s*[x×]\\s*75"),
        Regex("(?i)(\\d{1,2})[- ]pack"),
        Regex("(?i)(\\d{1,2})\\s+bottles?\\b"),
    )

    fun parse(text: String, currentYear: Int): PasteExtract {
        val windowSpans = mutableListOf<IntRange>()
        val window = parseWindow(text, currentYear, windowSpans)
        val nv = nvRe.containsMatchIn(text)
        val vintage = if (nv) null else findVintage(text, currentYear, windowSpans)
        return PasteExtract(vintage, nv, window, parseScores(text), parsePrices(text))
    }

    fun parseWindow(text: String, currentYear: Int, spans: MutableList<IntRange> = mutableListOf()): DrinkWindow {
        var from: Int? = null
        var until: Int? = null
        var peakStart: Int? = null
        var peakEnd: Int? = null
        for (m in rangeRe.findAll(text)) {
            val a = m.groupValues[2].let { if (it.equals("now", true)) currentYear else it.toInt() }
            val b = m.groupValues[3].toInt()
            if (b < a) continue
            spans += m.range
            if (m.groupValues[1].equals("peak", true)) {
                if (peakStart == null) { peakStart = a; peakEnd = b }
            } else if (from == null && until == null) {
                from = a; until = b
            }
        }
        if (until == null) untilRe.find(text)?.let { m ->
            spans += m.range
            until = m.groupValues[1].toInt()
            if (from == null && m.value.contains("now", true)) from = currentYear
        }
        if (from == null) fromRe.find(text)?.let { m ->
            spans += m.range
            from = m.groupValues[1].toInt()
        }
        if (from == null && drinkNowRe.containsMatchIn(text)) from = currentYear
        val f = from
        val u = until
        if (f != null && u != null && u < f) until = null
        return DrinkWindow(from, peakStart, peakEnd, until)
    }

    private fun findVintage(text: String, currentYear: Int, exclude: List<IntRange>): Int? {
        for (m in yearRe.findAll(text)) {
            val y = m.value.toInt()
            if (y < 1900 || y > currentYear) continue
            if (exclude.any { m.range.first >= it.first && m.range.last <= it.last }) continue
            val before = text.getOrNull(m.range.first - 1)
            if (before == '$' || before == '€' || before == '£' || before == ',' || before == '.') continue
            val after = text.getOrNull(m.range.last + 1)
            if (after == '.' && text.getOrNull(m.range.last + 2)?.isDigit() == true) continue
            return y
        }
        return null
    }

    fun parseScores(text: String): List<ParsedScore> {
        data class Hit(val range: IntRange, val score: ParsedScore)
        val hits = mutableListOf<Hit>()
        fun free(r: IntRange) = hits.none { r.first <= it.range.last && it.range.first <= r.last }
        fun nearbySource(start: Int, default: String): String {
            val ctx = text.substring(maxOf(0, start - 30), start)
            val c = ScoreSources.code(ctx)
            return if (c == "OTHER") default else c
        }

        for (m in twentyRe.findAll(text)) {
            val v = m.groupValues[1].toDouble()
            if (v < 10 || v > 20) continue
            hits += Hit(m.range, ParsedScore(nearbySource(m.range.first, "JR"), m.value.trim(), v * 5))
        }
        for (re in listOf(starsRe, vivinoRatingsRe)) for (m in re.findAll(text)) {
            if (!free(m.range)) continue
            val v = m.groupValues[1].toDouble()
            hits += Hit(m.range, ParsedScore("VIVINO", m.groupValues[1] + " stars", v * 20))
        }
        for (m in hundredRe.findAll(text)) {
            if (!free(m.range)) continue
            val v = m.groupValues[1].toDouble()
            if (v < 50 || v > 100) continue
            hits += Hit(m.range, ParsedScore(nearbySource(m.range.first, "OTHER"), m.value.trim(), v))
        }
        for (m in sourceFirstRe.findAll(text)) {
            if (!free(m.range)) continue
            val s = build(ScoreSources.code(m.groupValues[1]), m.value.trim(), m.groupValues[2], m.groupValues[3]) ?: continue
            hits += Hit(m.range, s)
        }
        for (m in scoreFirstRe.findAll(text)) {
            if (!free(m.range)) continue
            val src = if (m.groupValues[4].isNotEmpty()) ScoreSources.code(m.groupValues[4]) else nearbySource(m.range.first, "OTHER")
            val s = build(src, m.value.trim(), m.groupValues[1], m.groupValues[2]) ?: continue
            hits += Hit(m.range, s)
        }
        val all = hits.sortedBy { it.range.first }.map { it.score }
        // Drop unattributed repeats of a score that is already attributed to a source.
        return all.filterIndexed { i, s ->
            all.indexOfFirst { it.source == s.source && it.score100 == s.score100 } == i &&
                !(s.source == "OTHER" && all.any { it.source != "OTHER" && it.score100 == s.score100 })
        }
    }

    private fun build(source: String, raw: String, a: String, b: String): ParsedScore? {
        val lo = a.toDoubleOrNull() ?: return null
        val hi = b.toDoubleOrNull()
        if (lo < 50 || lo > 100) return null
        if (hi != null && (hi < lo || hi > 100)) return ParsedScore(source, raw, lo)
        return if (hi != null) ParsedScore(source, raw, (lo + hi) / 2, lo, hi) else ParsedScore(source, raw, lo)
    }

    fun parsePrices(text: String): List<ParsedPrice> {
        val out = mutableListOf<ParsedPrice>()
        for (m in priceRe.findAll(text)) {
            val amount = m.groupValues[2].replace(",", "").toDoubleOrNull() ?: continue
            if (amount <= 0) continue
            val currency = when (m.groupValues[1]) {
                "€", "EUR" -> "EUR"
                "£", "GBP" -> "GBP"
                else -> "USD"
            }
            // Context is limited to the price's own line so a neighbouring line cannot relabel it.
            val before = text.substring(maxOf(0, m.range.first - 60), m.range.first).substringAfterLast('\n').lowercase()
            val after = text.substring(m.range.last + 1, minOf(text.length, m.range.last + 41)).substringBefore('\n').lowercase()
            val isAverage = "avg" in before || "average" in before || "market" in before
            val perBottleStated = listOf("per bottle", "/bottle", "/ bottle", "a bottle", "each", "/750", "/ 750", "per 750")
                .any { it in after }
            var divisor = 1
            if (!perBottleStated) {
                val ctx = "$before ${m.value} $after"
                for (re in caseRes) {
                    val n = re.find(ctx)?.groupValues?.get(1)?.toIntOrNull()
                    if (n != null && n in 2..24) { divisor = n; break }
                }
            }
            out += ParsedPrice(amount, currency, amount / divisor, isAverage, m.value)
        }
        return out
    }
}
