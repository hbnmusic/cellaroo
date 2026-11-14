package app.cellaroo.core

object ReadinessCalc {

    fun readiness(w: DrinkWindow, year: Int): Readiness {
        if (w.isEmpty) return Readiness.UNKNOWN
        if (w.until != null && year > w.until) return Readiness.PAST_PEAK
        if (w.from != null && year < w.from) return Readiness.HOLD
        val peakEnd = w.peakEnd ?: w.until
        if (w.peakStart != null) {
            if (year >= w.peakStart && (peakEnd == null || year <= peakEnd)) return Readiness.AT_PEAK
        } else if (w.peakEnd != null && w.from != null && year <= w.peakEnd) {
            // Peak end known without a start: treat the span from drink-from to peak end as peak.
            return Readiness.AT_PEAK
        }
        return Readiness.READY
    }

    /** True when the wine is drinkable and its window ends this year or next. */
    fun windowClosing(w: DrinkWindow, year: Int): Boolean {
        val r = readiness(w, year)
        return (r == Readiness.READY || r == Readiness.AT_PEAK) && w.until != null && w.until - year <= 1
    }

    /** One-line drink/keep recommendation. [bottles] is the number in the cellar. */
    fun verdict(w: DrinkWindow, year: Int, bottles: Int): String {
        val r = readiness(w, year)
        if (windowClosing(w, year)) {
            return if (w.until == year) "Drink this year: the window closes in $year."
            else "Drink soon: the window closes in ${w.until}."
        }
        return when (r) {
            Readiness.UNKNOWN -> "No drinking window yet. Paste one or run an estimate."
            Readiness.HOLD -> "Hold until ${w.from}."
            Readiness.AT_PEAK -> {
                val end = w.peakEnd ?: w.until
                if (end != null) "At peak. Drink by $end." else "At peak now."
            }
            Readiness.PAST_PEAK -> "Past its window (ended ${w.until}). Open soon; it may be declining."
            Readiness.READY -> {
                val ps = w.peakStart
                when {
                    ps != null && year < ps && bottles >= 3 ->
                        "Ready. Open one now and keep the rest toward $ps."
                    ps != null && year < ps -> "Drinkable now, better from $ps."
                    w.until != null -> "Drink any time through ${w.until}."
                    else -> "Ready to drink."
                }
            }
        }
    }

    /** Rule-of-thumb serving mode when nothing better is known. */
    fun fallbackServingMode(type: WineType, vintage: Int?, year: Int): ServingMode = when (type) {
        WineType.SPARKLING, WineType.WHITE, WineType.ROSE, WineType.OTHER -> ServingMode.EITHER
        WineType.DESSERT, WineType.FORTIFIED -> ServingMode.SOLO
        WineType.RED -> if (vintage != null && year - vintage <= 5) ServingMode.FOOD else ServingMode.EITHER
    }
}

data class TonightInput(
    val window: DrinkWindow,
    /** Best available quality on a 0–100 scale: own rating, else mean critic score, else null. */
    val quality100: Double?,
    val servingMode: ServingMode?,
    val pairingTags: Set<PairingTag>,
)

object TonightRanker {
    /** Wines that belong on the shortlist at all. */
    fun isCandidate(w: DrinkWindow, year: Int): Boolean {
        val r = ReadinessCalc.readiness(w, year)
        return r == Readiness.READY || r == Readiness.AT_PEAK
    }

    fun pairingFit(input: TonightInput, chip: PairingTag?): Double = when {
        chip == null -> 0.0
        chip == PairingTag.SOLO -> when (input.servingMode) {
            ServingMode.SOLO -> 1.0
            ServingMode.EITHER -> 0.5
            else -> if (PairingTag.SOLO in input.pairingTags) 0.5 else 0.0
        }
        chip in input.pairingTags -> 1.0
        else -> 0.0
    }

    /** 0–100. Higher means open it sooner. */
    fun score(input: TonightInput, year: Int, chip: PairingTag?): Double {
        val w = input.window
        val yearsLeft = ((w.until ?: (year + 10)) - year).toDouble()
        val urgency = (1.0 - yearsLeft / 10.0).coerceIn(0.0, 1.0)
        val peak = if (ReadinessCalc.readiness(w, year) == Readiness.AT_PEAK) 1.0 else 0.5
        val quality = (((input.quality100 ?: 88.0) - 85.0) / 15.0).coerceIn(0.0, 1.0)
        return 40 * urgency + 25 * peak + 20 * quality + 15 * pairingFit(input, chip)
    }
}

object Boxes {
    /** Gemini-style box: [ymin, xmin, ymax, xmax], each 0–1000. */
    fun isValid(b: List<Int>?): Boolean {
        if (b == null || b.size != 4) return false
        if (b.any { it < 0 || it > 1000 }) return false
        return b[0] < b[2] && b[1] < b[3]
    }
}

object Csv {
    fun line(fields: List<Any?>): String = fields.joinToString(",") { f ->
        val s = f?.toString() ?: ""
        if (s.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + s.replace("\"", "\"\"") + "\"" else s
    }
}
