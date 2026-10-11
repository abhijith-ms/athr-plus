package io.ather.pro.domain.charging

import org.junit.Assert.*
import org.junit.Test

class ChargeAlertEventTest {
    private fun reading(at: Long, source: Long = at) = ChargeAlertEvent(at, ChargeAlertEvent.Kind.READING, 90, 51.0, source)
    @Test fun duplicateAndFrequentBatteryReportsAreNotLoggedAgain() {
        val first = ChargeAlertEvent.record(ChargeLimitController.Snapshot(), reading(1_000_000))
        assertEquals(first, ChargeAlertEvent.record(first, reading(1_000_001)))
        assertEquals(first, ChargeAlertEvent.record(first, reading(1_300_000, 1_000_000)))
        assertEquals(2, ChargeAlertEvent.record(first, reading(1_300_000)).alertHistory.size)
    }
    @Test fun historyIsBoundedAndSettingsKeepIt() {
        var state = ChargeLimitController.Snapshot()
        repeat(60) { state = ChargeAlertEvent.record(state, ChargeAlertEvent(it + 1L, ChargeAlertEvent.Kind.ENABLED, 90)) }
        assertEquals(40, state.alertHistory.size)
        assertEquals(21L, state.alertHistory.first().atMs)
        val enabled = ChargeTargetAlert.configure(state, true, 90)
        assertEquals(state.alertHistory, enabled.alertHistory)
        assertEquals(state.alertHistory, ChargeTargetAlert.configure(enabled, false, 90).alertHistory)
    }
    @Test fun submittedNotificationDoesNotClaimDelivery() {
        val event = ChargeAlertEvent(1L, ChargeAlertEvent.Kind.POSTED, 90)
        assertEquals("Notification submitted to Android", event.label)
        assertFalse(event.label.contains("delivered", true))
    }
}
