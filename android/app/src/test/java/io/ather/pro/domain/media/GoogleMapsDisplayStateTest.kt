package io.ather.pro.domain.media

import org.junit.Assert.*
import org.junit.Test

class GoogleMapsDisplayStateTest {
    private val maps = GoogleMapsInstruction.PACKAGE
    private val start = GoogleMapsDisplayState(running = true, listenerConnected = true)
    private fun read() = start.posted("route", maps, true, listOf("200 m", "Turn left"), 1_000)

    @Test fun filtersOtherAppsAndDoesNotKeepTextWhileStopped() {
        val stopped = GoogleMapsDisplayState()
        assertEquals(stopped, stopped.posted("route", maps, true, listOf("private"), 1))
        assertEquals(start, start.posted("other", "com.example.messages", true, listOf("secret"), 1))
        assertEquals(start, start.posted("maps-review", maps, false, listOf("review"), 1))
        assertTrue(start.mapsText.isEmpty())
    }
    @Test fun staleAndFutureReceiptTimesCannotDisplayAnOldTurn() {
        val state = read()
        assertEquals("200M LEFT", state.labelAt(1_000))
        assertEquals("200M LEFT", state.labelAt(120_999))
        assertEquals("CHECK MAP", state.labelAt(121_000))
        assertEquals("CHECK MAP", state.labelAt(999))
        assertEquals("OPEN MAPS", start.labelAt(1))
    }
    @Test fun unsupportedUpdatesReplaceThePreviousTurnImmediately() {
        val state = read().posted("route", maps, true, listOf("Rerouting"), 2_000)
        assertEquals("CHECK MAP", state.labelAt(2_000))
        assertFalse(state.instruction!!.supported)
    }
    @Test fun theNextMapsUpdateReplacesTheInitialHeading() {
        val heading = start.posted("route", maps, true, listOf("Head toward Example Road"), 1_000)
        assertEquals("HEAD TOWARD", heading.labelAt(1_000))
        val turn = heading.posted("route", maps, true, listOf("200 m", "Turn left"), 2_000)
        assertEquals("200M LEFT", turn.labelAt(2_000))
        assertEquals("CHECK MAP", turn.labelAt(122_000))
    }
    @Test fun onlyRemovalOfTheCurrentMapsNotificationEndsTheDisplay() {
        val state = read()
        assertEquals(state, state.removed("other", maps))
        assertEquals(state, state.removed("route", "com.example.messages"))
        val stopped = state.removed("route", maps)
        assertFalse(stopped.running)
        assertNull(stopped.instruction)
        assertNull(stopped.receivedAtMs)
        assertTrue(stopped.mapsText.isEmpty())
        assertFalse(state.posted("route", maps, false, emptyList(), 2_000).running)
    }
    @Test fun losingListenerClearsPrivateTextAndGuidance() {
        val state = read().disconnected()
        assertTrue(state.running)
        assertTrue(state.mapsText.isEmpty())
        assertEquals("CHECK MAP", state.labelAt(1_001))
        assertNull(state.notificationKey)
    }
    @Test fun stoppingClearsDataAndDiagnosticTextIsBounded() {
        val state = start.posted("route", maps, true, List(3) { "x".repeat(10_000) }, 1_000)
        assertTrue(state.mapsText.length <= 800)
        assertFalse(state.instruction!!.supported)
        assertTrue(state.stopped().mapsText.isEmpty())
        assertFalse(state.stopped().running)
    }
    @Test fun newSourceRestoresFreshnessWithoutUsingThePhoneUiClockAsEvidence() {
        val stale = read()
        assertEquals("CHECK MAP", stale.labelAt(200_000))
        val fresh = stale.posted("route", maps, true, listOf("50 m", "Turn right"), 200_000)
        assertEquals("50M RIGHT", fresh.labelAt(200_000))
        assertEquals("Reading Google Maps instructions", fresh.statusAt(200_000))
    }
    @Test fun pausedDisplayDoesNotShowAnOldTurnAndResumeRechecksFreshness() {
        val state = read().copy(playing = false)
        assertEquals("PAUSED", state.labelAt(1_001))
        assertEquals("PAUSED", state.labelAt(121_000))
        assertEquals("CHECK MAP", state.copy(playing = true).labelAt(121_000))
    }
}
