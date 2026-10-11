package io.ather.pro.domain.monitoring

import io.ather.pro.domain.model.ScooterDashboardState
import org.junit.Assert.*
import org.junit.Test

class ReadingFreshnessTest {
    private val now = 1_000_000L
    @Test fun freshPacketsDoNotRefreshBatteryOrChargingReports() {
        val state = ScooterDashboardState(lastUpdated = now, gpsUpdatedAt = now,
            batteryReportedAt = now - 120_000, chargingReportedAt = now - 300_000)
        assertEquals("Battery · 2m ago · stale", ReadingFreshness.label("Battery", ReadingFreshness.batteryAt(state), now))
        assertEquals("Charging status · 5m ago · stale", ReadingFreshness.label("Charging status", ReadingFreshness.chargingAt(state), now))
        assertNull(ReadingFreshness.batteryAt(ScooterDashboardState(lastUpdated = now)))
    }
    @Test fun missingFutureAndBoundaryTimestampsAreExplicit() {
        assertEquals("Battery · no timestamp", ReadingFreshness.label("Battery", null, now))
        assertEquals("Battery · timestamp ahead of phone", ReadingFreshness.label("Battery", now + 1, now))
        assertFalse(ReadingFreshness.fresh(now + 1, now))
        assertFalse(ReadingFreshness.fresh(0, now))
        assertTrue(ReadingFreshness.fresh(now - 29_999, now))
        assertFalse(ReadingFreshness.fresh(now - 30_000, now))
    }
}
