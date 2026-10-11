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

    @Test fun sampleDirectionsFitTheDisplayWithoutScrollingOrArrowGlyphs() {
        val samples = MediaProbeState.NAVIGATION_STEPS
        assertEquals(samples.size, samples.map { it.first }.distinct().size)
        samples.forEach { (title, instruction) ->
            assertTrue(title.length in 1..11)
            assertTrue(title.matches(Regex("[A-Z0-9 ]+")))
            assertTrue(instruction.isNotBlank())
        }
        val preview = MediaProbeState(navigationPreview = true)
        assertEquals("200M LEFT", preview.title)
        assertEquals("NAV DEMO", preview.artist)
    }

    @Test fun previewCyclesSamplesThroughMediaCommandsAndPreservesPause() {
        var state = MediaProbeState(active = true, playing = true, navigationPreview = true)
            .receive(Command.PAUSE, 1)
        MediaProbeState.NAVIGATION_STEPS.drop(1).forEach { (title, instruction) ->
            state = state.receive(Command.NEXT, 2)
            assertEquals(title, state.title)
            assertEquals(instruction, state.instruction)
            assertFalse(state.playing)
            assertEquals(Source.MEDIA_CALLBACK, state.events.last().source)
        }
        state = state.receive(Command.NEXT, 3)
        assertEquals("200M LEFT", state.title)
        state = state.receive(Command.PREVIOUS, 4, Source.PHONE_BUTTON)
        assertEquals("ARRIVED", state.title)
        state = state.receive(Command.STOP, 5)
        assertEquals(state, state.receive(Command.NEXT, 6))
    }

    @Test fun modeCanChangeOnlyWhenStoppedAndStartsAtFirstSample() {
        val active = MediaProbeState(active = true, navigationPreview = true, track = 3)
        assertEquals(active, active.selectNavigationPreview(false))
        val media = active.receive(Command.STOP, 1).selectNavigationPreview(false)
        assertFalse(media.active)
        assertEquals("ATHR TEST 001", media.title)
        assertNull(media.instruction)
        assertEquals("450S PROBE", media.artist)
        val preview = media.selectNavigationPreview(true)
        assertFalse(preview.active)
        assertEquals("200M LEFT", preview.title)
        assertTrue(preview.events.isEmpty())
    }
}
