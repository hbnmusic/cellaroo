package app.cellaroo.core

enum class WineType(val label: String) {
    RED("Red"), WHITE("White"), ROSE("Rosé"), SPARKLING("Sparkling"),
    DESSERT("Dessert"), FORTIFIED("Fortified"), OTHER("Other");

    companion object {
        fun parse(s: String?): WineType {
            val t = s?.trim()?.uppercase()?.replace("É", "E") ?: return OTHER
            return entries.firstOrNull { it.name == t } ?: when {
                "ROS" in t -> ROSE
                "SPARK" in t || "CHAMP" in t -> SPARKLING
                "RED" in t -> RED
                "WHITE" in t -> WHITE
                "DESS" in t || "SWEET" in t -> DESSERT
                "FORT" in t || "PORT" in t || "SHERRY" in t -> FORTIFIED
                else -> OTHER
            }
        }
    }
}

enum class Readiness(val label: String) {
    UNKNOWN("NO WINDOW"), HOLD("HOLD"), READY("READY"), AT_PEAK("AT PEAK"), PAST_PEAK("PAST PEAK")
}

enum class ServingMode(val label: String) {
    SOLO("Best on its own"), FOOD("Best with food"), EITHER("Solo or with food");

    companion object {
        fun parse(s: String?): ServingMode? {
            val t = s?.trim()?.uppercase() ?: return null
            return entries.firstOrNull { it.name == t }
        }
    }
}

enum class BottleStatus { IN_CELLAR, CONSUMED, GIFTED, SOLD, LOST }

/** Controlled pairing vocabulary. The AI prompts and the paste normalizer may only emit these names. */
enum class PairingTag(val label: String) {
    FRENCH("French"), SPANISH("Spanish"), ITALIAN("Italian"), GREEK("Greek"),
    THAI("Thai"), VIETNAMESE("Vietnamese"), CHINESE("Chinese"), JAPANESE("Japanese"),
    NEW_AMERICAN("New American"), MIDDLE_EASTERN("Middle Eastern"), MEDITERRANEAN("Mediterranean"),
    CHEESE_CHARCUTERIE("Cheese & charcuterie"), SOLO("Solo sipping");

    companion object {
        fun parse(s: String?): PairingTag? {
            val t = s?.trim()?.uppercase()?.replace(' ', '_')?.replace('-', '_') ?: return null
            return entries.firstOrNull { it.name == t }
        }
    }
}

/** Where a stored value came from. */
object Source {
    const val SCAN = "SCAN"
    const val PASTE = "PASTE"
    const val AI_ESTIMATE = "AI_ESTIMATE"
    const val MANUAL = "MANUAL"
    const val HEURISTIC = "HEURISTIC"

    fun label(s: String?): String = when (s) {
        SCAN -> "from label scan"
        PASTE -> "pasted"
        AI_ESTIMATE -> "ESTIMATE"
        MANUAL -> "entered by you"
        HEURISTIC -> "rule of thumb"
        else -> ""
    }
}

data class DrinkWindow(
    val from: Int? = null,
    val peakStart: Int? = null,
    val peakEnd: Int? = null,
    val until: Int? = null,
) {
    val isEmpty: Boolean get() = from == null && peakStart == null && peakEnd == null && until == null

    fun display(): String = when {
        isEmpty -> "—"
        from != null && until != null -> "$from–$until"
        from != null -> "$from+"
        until != null -> "until $until"
        peakStart != null && peakEnd != null -> "peak $peakStart–$peakEnd"
        peakStart != null -> "peak from $peakStart"
        else -> "peak until $peakEnd"
    }
}

data class Pairing(val tag: PairingTag, val dish: String?)
