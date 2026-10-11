package io.ather.pro.domain.media

import io.ather.pro.domain.media.MediaProbeState.Command
import io.ather.pro.domain.media.MediaProbeState.Source
import org.junit.Assert.*
import org.junit.Test

class MediaProbeStateTest {
    @Test fun commandsChangeMetadataAndPlaybackWithoutStartingAStoppedTest() {
        assertEquals(MediaProbeState(), MediaProbeState().receive(Command.PLAY, 1))
        var state = MediaProbeState(active = true, playing = true)
        state = state.receive(Command.PAUSE, 1)
        assertFalse(state.playing)
        state = state.receive(Command.NEXT, 2)
        assertEquals("ATHR TEST 002", state.title)
        assertFalse(state.playing)
        state = state.receive(Command.PLAY, 3)
        assertTrue(state.playing)
        state = state.receive(Command.STOP, 4)
        assertFalse(state.active)
        assertFalse(state.playing)
        assertEquals(state, state.receive(Command.NEXT, 5))
    }

    @Test fun trackWrapsAndHistoryIsBoundedAndDistinguishesPhoneButton() {
        var state = MediaProbeState(active = true).receive(Command.PREVIOUS, 1)
        assertEquals("ATHR TEST 999", state.title)
        state = state.receive(Command.NEXT, 2, Source.PHONE_BUTTON)
        assertEquals("ATHR TEST 001", state.title)
        assertEquals(Source.PHONE_BUTTON, state.events.last().source)
        repeat(30) { state = state.receive(Command.NEXT, it.toLong()) }
        assertEquals(20, state.events.size)
    }

    @Test fun localControllerChecksDoNotClaimExternalCallbacksOrDashboardDelivery() {
        val state = MediaProbeState(active = true, notificationStatus = "Media notification registered with Android")
            .receive(Command.NEXT, 1, Source.ANDROID_CONTROLLER)
            .receive(Command.PAUSE, 2)
        assertEquals(Source.ANDROID_CONTROLLER, state.events.first().source)
        assertEquals(Source.MEDIA_CALLBACK, state.events.last().source)
        assertEquals("Media notification registered with Android", state.notificationStatus)
        assertEquals("Session active on phone", state.status)
    }
}
