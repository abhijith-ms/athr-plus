package io.ather.pro.domain.media

import java.math.BigDecimal
import java.util.Locale

data class GoogleMapsInstruction(val label: String, val supported: Boolean) {
    companion object {
        const val PACKAGE = "com.google.android.apps.maps"
        private val action = Regex("\\b(?:(slight(?:ly)?|sharp(?:ly)?)\\s+(left|right)|(?:turn|keep)\\s+(?:slight(?:ly)?\\s+|sharp(?:ly)?\\s+)?(left|right)|(?:continue|head|go)\\s+straight|(?:make|take)\\s+(?:a\\s+)?u[- ]turn)\\b")
        private val distance = Regex("^(\\d+(?:[.,]\\d{1,2})?)\\s*(km|m|mi|ft)\\b")
        private val exit = Regex("\\b(?:take|use)\\s+(?:the\\s+)?(\\d{1,2})(?:st|nd|rd|th)?\\s+exit\\b")
        private val invalid = Regex("\\b(?:rerouting|re-routing|recalculating|calculating|searching|lost|paused|loading|offline|waiting|don't|do not|not|never|cannot|can't|avoid|cancelled|canceled|then|followed by|after that)\\b")
        private val eta = Regex("\\b(?:min|mins|minutes|hr|hrs|hours|eta|remaining|total|arrival|destination)\\b")

        fun parse(fields: List<String>): GoogleMapsInstruction {
            val unknown = GoogleMapsInstruction("CHECK MAP", false)
            if (fields.size > 3 || fields.any { it.length > 500 }) return unknown
            val lines = fields.map { it.replace('\u00a0', ' ').replace('\u202f', ' ').replace('\u2019', '\'')
                .lowercase(Locale.ROOT).trim() }.filter(String::isNotBlank).distinct()
            if (lines.isEmpty()) return unknown
            if (lines.any { invalid.containsMatchIn(it) }) return unknown
            val codes = lines.flatMap { line ->
                action.findAll(line).map { match ->
                    val phrase = match.value
                    when {
                        "u-turn" in phrase || "u turn" in phrase -> "UTURN"
                        "straight" in phrase -> "AHEAD"
                        phrase.startsWith("slight") || "slight" in phrase -> if ("left" in phrase) "SLEFT" else "SRIGHT"
                        phrase.startsWith("sharp") || "sharp" in phrase -> if ("left" in phrase) "HLEFT" else "HRIGHT"
                        phrase.startsWith("keep") -> if ("left" in phrase) "KLEFT" else "KRIGHT"
                        else -> if ("left" in phrase) "LEFT" else "RIGHT"
                    }
                }.toList()
            }.toMutableSet()
            val exits = lines.filter { "roundabout" in it }.flatMap { line ->
                exit.findAll(line).map { it.groupValues[1].toInt() }.filter { it in 1..20 }.toList()
            }.toSet()
            codes += exits.map { "EXIT$it" }
            if (codes.size != 1) return unknown
            val code = codes.single()
            val distances = lines.filterNot { eta.containsMatchIn(it) }.mapNotNull { distance.find(it) }.mapNotNull { match ->
                val value = match.groupValues[1].replace(',', '.').toBigDecimalOrNull() ?: return@mapNotNull null
                if (value < BigDecimal.ZERO || value > BigDecimal("100000")) return@mapNotNull null
                val unit = match.groupValues[2].uppercase(Locale.ROOT)
                if (unit == "KM" && value < BigDecimal.ONE) {
                    value.multiply(BigDecimal(1000)).stripTrailingZeros().toPlainString() + "M"
                } else value.stripTrailingZeros().toPlainString() + unit
            }.distinct()
            val short = if (distances.size == 1) "${distances.single()} $code" else ""
            val label = short.takeIf { it.length in 1..11 } ?: when (code) {
                "LEFT", "RIGHT" -> "TURN $code"
                "AHEAD" -> "GO STRAIGHT"
                "UTURN" -> "U TURN"
                else -> code
            }
            return GoogleMapsInstruction(label, true)
        }
    }
}
