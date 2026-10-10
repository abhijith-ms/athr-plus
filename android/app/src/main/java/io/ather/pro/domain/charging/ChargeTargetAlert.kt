package io.ather.pro.domain.charging

import io.ather.pro.domain.model.ScooterTelemetry

/** A measured target reminder, never a remote command or an estimated cutoff. */
object ChargeTargetAlert {
    fun migrate(state: ChargeLimitController.Snapshot): ChargeLimitController.Snapshot =
        if (state.alertOnly) state else ChargeLimitController.applySettings(state, false, state.percent)
            .copy(alertOnly = true)

    fun configure(state: ChargeLimitController.Snapshot, enabled: Boolean, percent: Int): ChargeLimitController.Snapshot =
        ChargeLimitController.applySettings(migrate(state), enabled, percent).copy(
            alertOnly = true, estimate = null, message = null)

    data class Decision(val next: ChargeLimitController.Snapshot, val notify: Boolean = false)

    fun observe(state: ChargeLimitController.Snapshot, telemetry: ScooterTelemetry?,
                batteryAt: Long?, chargingAt: Long?, now: Long): Decision {
        if (!state.enabled || !state.alertOnly || state.status == ChargeLimitController.Status.ERROR) return Decision(state)
        val chargeFresh = ChargeLimitController.isFresh(chargingAt, now)
        if (chargeFresh && telemetry != null && ChargingEvidence.hasChargeReading(telemetry) &&
            !ChargingControl.isPluggedIn(telemetry)) {
            return Decision(state.copy(armed = true))
        }
        val soc = telemetry?.batterySoc?.takeIf { it.isFinite() && it in 0.0..100.0 }
        if (state.armed && chargeFresh && ChargeLimitController.isFresh(batteryAt, now) &&
            ChargingControl.isPluggedIn(telemetry) && soc != null && soc >= state.percent) {
            return Decision(state.copy(armed = false), notify = true)
        }
        return Decision(state)
    }
}
