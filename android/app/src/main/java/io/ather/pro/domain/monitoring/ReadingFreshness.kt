package io.ather.pro.domain.monitoring

import io.ather.pro.domain.charging.ChargeLimitController
import io.ather.pro.domain.model.ScooterDashboardState

object ReadingFreshness {
    fun batteryAt(state: ScooterDashboardState): Long? = state.batteryReportedAt
        ?: state.telemetry?.sourceTimestampMs ?: state.batteryUpdatedAt
    fun chargingAt(state: ScooterDashboardState): Long? = state.chargingReportedAt ?: state.chargingUpdatedAt
    fun fresh(at: Long?, now: Long): Boolean = at != null && at > 0 && ChargeLimitController.isFresh(at, now)
    fun label(name: String, at: Long?, now: Long): String {
        if (at == null || at <= 0) return "$name · no timestamp"
        val age = now - at
        if (age < 0) return "$name · timestamp ahead of phone"
        val elapsed = when {
            age < 60_000 -> "${age / 1_000}s"
            age < 3_600_000 -> "${age / 60_000}m"
            else -> "${age / 3_600_000}h"
        }
        return "$name · $elapsed ago" + if (fresh(at, now)) "" else " · stale"
    }
}
