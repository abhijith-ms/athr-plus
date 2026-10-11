package io.ather.pro.domain.charging

import kotlin.math.roundToInt

data class ChargeAlertEvent(
    val atMs: Long,
    val kind: Kind,
    val target: Int,
    val soc: Double? = null,
    val readingAtMs: Long? = null
) {
    enum class Kind { ENABLED, DISABLED, READING, TARGET_REACHED, POSTED, BLOCKED, FAILED }
    val label: String get() = when (kind) {
        Kind.ENABLED -> "Alert enabled at $target%"
        Kind.DISABLED -> "Alert disabled"
        Kind.READING -> "Battery report ${soc?.roundToInt()}%"
        Kind.TARGET_REACHED -> "$target% target reached"
        Kind.POSTED -> "Notification submitted to Android"
        Kind.BLOCKED -> "Notification blocked by Android settings"
        Kind.FAILED -> "Notification could not be submitted"
    }
    companion object {
        const val MAX_EVENTS = 40
        fun record(state: ChargeLimitController.Snapshot, event: ChargeAlertEvent): ChargeLimitController.Snapshot {
            if (event.kind == Kind.READING) {
                val last = state.alertHistory.lastOrNull { it.kind == Kind.READING }
                if (last != null && (last.readingAtMs == event.readingAtMs ||
                    event.atMs - last.atMs in 0 until 300_000L)) return state
            }
            return state.copy(alertHistory = (state.alertHistory + event).takeLast(MAX_EVENTS))
        }
    }
}
