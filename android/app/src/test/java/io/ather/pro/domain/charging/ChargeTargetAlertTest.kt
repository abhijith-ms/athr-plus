package io.ather.pro.domain.charging

import io.ather.pro.domain.model.*
import io.ather.pro.domain.monitoring.MonitorNotice
import org.junit.Assert.*
import org.junit.Test

class ChargeTargetAlertTest {
    private val now = 1_000_000L
    private val enabled = ChargeTargetAlert.configure(ChargeLimitController.Snapshot(), true, 88)
    private fun charging(soc: Double?) = ScooterTelemetry(batterySoc = soc, chargingStatus = "Charging")
    private fun observe(state: ChargeLimitController.Snapshot = enabled, soc: Double? = 88.0,
                        batteryAt: Long? = now, chargingAt: Long? = now) =
        ChargeTargetAlert.observe(state, charging(soc), batteryAt, chargingAt, now)

    @Test fun measuredThresholdAlertsOnceWithoutStopping() {
        assertFalse(observe(soc = 87.99).notify)
        val reached = observe(soc = 88.13)
        assertTrue(reached.notify)
        assertFalse(reached.next.armed)
        assertEquals(ChargeLimitController.Status.MONITORING, reached.next.status)
        assertFalse(observe(reached.next, 90.0).notify)
        assertEquals(ChargeLimitController.Decision.None,
            ChargeLimitController.onTelemetry(enabled, charging(90.0), now, now))
        val deadline = ChargeTimeEstimate(readingAtMs = now - 600_000, batterySoc = 80.0,
            targetPercent = 88, chargerPowerW = 900, capacityWh = 2610.0,
            minutesFromReading = 10.0, targetAtMs = now, stopAtMs = now - 60_000,
            basisCode = 2, accuracyPercent = 20)
        val alertWithOldDeadline = enabled.copy(estimate = deadline)
        assertEquals(ChargeLimitController.Decision.None,
            EstimatedChargeCutoff.onDeadline(alertWithOldDeadline, charging(90.0), now))
    }

    @Test fun staleIncompleteInvalidAndFutureReadingsNeverAlert() {
        assertFalse(observe(batteryAt = null).notify)
        assertFalse(observe(chargingAt = null).notify)
        assertFalse(observe(batteryAt = now - 30_000).notify)
        assertFalse(observe(chargingAt = now - 30_000).notify)
        assertFalse(observe(batteryAt = now + 1).notify)
        listOf(null, Double.NaN, Double.POSITIVE_INFINITY, -1.0, 101.0).forEach {
            assertFalse(observe(soc = it).notify)
        }
        assertFalse(observe(enabled.copy(enabled = false)).notify)
        assertFalse(observe(enabled.copy(status = ChargeLimitController.Status.ERROR)).notify)
    }

    @Test fun onlyFreshUnplugRearmsTheNextSession() {
        val reached = observe().next
        val unplugged = ScooterTelemetry(charging = false, chargerConnected = false)
        assertFalse(ChargeTargetAlert.observe(reached, unplugged, now, now - 30_000, now).next.armed)
        val rearmed = ChargeTargetAlert.observe(reached, unplugged, now, now, now)
        assertFalse(rearmed.notify)
        assertTrue(rearmed.next.armed)
        assertTrue(observe(rearmed.next).notify)
    }

    @Test fun legacyCutoffIsDisabledAndRequiresExplicitOptIn() {
        val old = ChargeLimitController.Snapshot(enabled = true, percent = 88,
            status = ChargeLimitController.Status.PENDING, armed = true, attempts = 3,
            pendingSinceMs = now, learnedPercentPerMinute = 0.5, learnedMinutes = 30.0, learnedSessions = 2)
        val migrated = ChargeTargetAlert.migrate(old)
        assertTrue(migrated.alertOnly)
        assertFalse(migrated.enabled)
        assertFalse(migrated.armed)
        assertEquals(88, migrated.percent)
        assertNull(migrated.pendingSinceMs)
        assertEquals(0, migrated.attempts)
        assertEquals(old.learnedPercentPerMinute, migrated.learnedPercentPerMinute)
        assertEquals(old.learnedSessions, migrated.learnedSessions)
        assertTrue(observe(ChargeTargetAlert.configure(migrated, true, 88)).notify)
        val reached = observe().next
        assertEquals(reached, ChargeTargetAlert.migrate(reached))
        assertFalse(observe(ChargeTargetAlert.configure(reached, true, 88)).notify)
        assertTrue(ChargeTargetAlert.configure(reached, true, 90).armed)
    }

    @Test fun detected450SWinsOverSavedModelAndNoticeNeverClaimsAStop() {
        val state = ScooterDashboardState(vehicleProfile = VehicleProfile(modelType = "450s"),
            connection = ConnectionStatus.CONNECTED, batteryUpdatedAt = now, telemetry = charging(90.0))
        assertTrue(state.chargeAlertOnly)
        val notice = MonitorNotice.from(now, state, observe().next)
        assertEquals("Charge target reached", notice.title)
        assertTrue(notice.detail.contains("Unplug manually"))
        assertNull(notice.countdownToMs)
        val widget = io.ather.pro.widget.DashboardWidgetSnapshot.fromDashboard(state, observe().next)
        assertTrue(widget.alertOnly)
        assertEquals("Alert 88% · Unplug manually", widget.chargeLabel)
        assertNull(widget.estimatedStopAtMs)
        assertFalse(state.copy(vehicleProfile = VehicleProfile(modelType = "450x", modelCode = "xhr")).chargeAlertOnly)
    }
}
