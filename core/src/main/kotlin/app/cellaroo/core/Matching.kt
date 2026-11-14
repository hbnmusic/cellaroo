package app.cellaroo.core

import java.text.Normalizer

data class WineRef(val wineId: Long, val producer: String, val name: String, val vintage: Int?, val sizeMl: Int = 750)

data class BottleRef(val bottleId: Long, val wineId: Long, val locationId: Long?)

data class Detection(
    val index: Int,
    val producer: String,
    val name: String,
    val vintage: Int?,
    val confidence: Double = 1.0,
    val legible: Boolean = true,
)

enum class MatchKind { MATCH, REVIEW, UNKNOWN }

data class MatchResult(val kind: MatchKind, val wineId: Long?, val score: Double, val reason: String? = null)

/** How the user resolved a detection that could not be matched automatically. */
sealed class Resolution {
    data class IsWine(val wineId: Long) : Resolution()
    data object NewWine : Resolution()
    data object Ignore : Resolution()
}

data class ExtraBottle(val detection: Detection, val wineId: Long, val moveFrom: BottleRef?)

data class ReviewItem(val detection: Detection, val suggestedWineId: Long?, val reason: String)

data class ReconcilePlan(
    val matchedCount: Int,
    val missing: List<BottleRef>,
    val extras: List<ExtraBottle>,
    val unknown: List<Detection>,
    val review: List<ReviewItem>,
) {
    val isClean: Boolean get() = unknown.isEmpty() && review.isEmpty()
}

object Matching {
    private val stop = setOf(
        "the", "de", "di", "du", "des", "del", "della", "la", "le", "les", "el", "los", "las", "y", "e", "et",
        "and", "of", "a", "d", "l", "chateau", "domaine", "bodegas", "bodega", "cantina", "tenuta", "weingut",
        "winery", "vineyards", "vineyard", "estate", "wines", "wine", "cellars", "vina", "vinedos", "cave",
        "caves", "maison", "azienda", "agricola", "fattoria",
    )
    private val marks = Regex("\\p{M}+")
    private val nonAlnum = Regex("[^a-z0-9]+")

    /** Lowercase, accents removed, punctuation collapsed to single spaces. */
    fun fold(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(marks, "").lowercase().replace(nonAlnum, " ").trim()

    fun normalizedKey(producer: String, name: String, vintage: Int?, sizeMl: Int): String =
        "${fold(producer)}|${fold(name)}|${vintage ?: "nv"}|$sizeMl"

    fun tokens(s: String): Set<String> = fold(s).split(' ').filter { it.isNotEmpty() && it !in stop }.toSet()

    private fun lev(a: String, b: String): Int {
        if (a == b) return 0
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            prev = cur
        }
        return prev[b.length]
    }

    private fun tokenEq(a: String, b: String): Boolean =
        a == b || (a.length >= 5 && b.length >= 5 && kotlin.math.abs(a.length - b.length) <= 1 && lev(a, b) <= 1)

    /** Token-set similarity in 0..1; tolerant of one-letter OCR slips and of one side being a subset. */
    fun similarity(a: Set<String>, b: Set<String>): Double {
        if (a.isEmpty() || b.isEmpty()) return 0.0
        val common = a.count { x -> b.any { y -> tokenEq(x, y) } }
        val dice = 2.0 * common / (a.size + b.size)
        val small = minOf(a.size, b.size)
        val containment = if (small >= 2) 0.9 * common / small else 0.0
        return maxOf(dice, containment).coerceAtMost(1.0)
    }

    fun similarity(a: String, b: String): Double = similarity(tokens(a), tokens(b))

    fun match(det: Detection, wines: List<WineRef>, threshold: Double = 0.85): MatchResult {
        val shaky = !det.legible || det.confidence < 0.6
        val dt = tokens(det.producer + " " + det.name)
        if (dt.isEmpty()) return MatchResult(MatchKind.REVIEW, null, 0.0, "Label not readable")
        val scored = wines.map { it to similarity(dt, tokens(it.producer + " " + it.name)) }
            .filter { it.second >= threshold }
            .sortedByDescending { it.second }
        if (scored.isEmpty()) {
            return if (shaky) MatchResult(MatchKind.REVIEW, null, 0.0, "Low-confidence read")
            else MatchResult(MatchKind.UNKNOWN, null, 0.0)
        }
        val best = scored.first()
        if (det.vintage != null) {
            val exact = scored.firstOrNull { it.first.vintage == det.vintage }
            if (exact != null) {
                return if (shaky) MatchResult(MatchKind.REVIEW, exact.first.wineId, exact.second, "Low-confidence read")
                else MatchResult(MatchKind.MATCH, exact.first.wineId, exact.second)
            }
            val reason = if (best.first.vintage == null) "Label shows ${det.vintage}; cellar entry has no vintage"
            else "Vintage differs: label ${det.vintage}, cellar ${best.first.vintage}"
            return MatchResult(MatchKind.REVIEW, best.first.wineId, best.second, reason)
        }
        if (best.first.vintage == null && !shaky) return MatchResult(MatchKind.MATCH, best.first.wineId, best.second)
        val reason = when {
            shaky -> "Low-confidence read"
            scored.map { it.first.wineId }.distinct().size > 1 -> "Vintage not readable; several vintages in cellar"
            else -> "Vintage not readable"
        }
        return MatchResult(MatchKind.REVIEW, best.first.wineId, best.second, reason)
    }

    /**
     * Compares what a shelf photo shows with what the cellar expects at [locationId].
     * [bottles] must be the bottles currently in the cellar (any location).
     * [resolved] holds the user's decisions for detections, keyed by detection index.
     */
    fun plan(
        detections: List<Detection>,
        wines: List<WineRef>,
        bottles: List<BottleRef>,
        locationId: Long,
        resolved: Map<Int, Resolution> = emptyMap(),
    ): ReconcilePlan {
        val seen = LinkedHashMap<Long, MutableList<Detection>>()
        val unknown = mutableListOf<Detection>()
        val review = mutableListOf<ReviewItem>()
        for (d in detections) {
            when (val r = resolved[d.index]) {
                is Resolution.IsWine -> seen.getOrPut(r.wineId) { mutableListOf() }.add(d)
                Resolution.NewWine -> unknown += d
                Resolution.Ignore -> Unit
                null -> {
                    val m = match(d, wines)
                    when (m.kind) {
                        MatchKind.MATCH -> seen.getOrPut(m.wineId!!) { mutableListOf() }.add(d)
                        MatchKind.UNKNOWN -> unknown += d
                        MatchKind.REVIEW -> review += ReviewItem(d, m.wineId, m.reason ?: "Needs review")
                    }
                }
            }
        }
        val expected = bottles.filter { it.locationId == locationId }.groupBy { it.wineId }
        val elsewhere = bottles.filter { it.locationId != locationId }.groupBy { it.wineId }
        var matched = 0
        val missing = mutableListOf<BottleRef>()
        val extras = mutableListOf<ExtraBottle>()
        for (wineId in (expected.keys + seen.keys)) {
            val e = expected[wineId].orEmpty()
            val s = seen[wineId].orEmpty()
            matched += minOf(e.size, s.size)
            if (e.size > s.size) missing += e.drop(s.size)
            if (s.size > e.size) {
                val movable = elsewhere[wineId].orEmpty()
                s.drop(e.size).forEachIndexed { i, d -> extras += ExtraBottle(d, wineId, movable.getOrNull(i)) }
            }
        }
        return ReconcilePlan(matched, missing, extras, unknown, review)
    }
}
