package io.ather.pro.domain.media

data class MediaProbeState(
    val active: Boolean = false,
    val playing: Boolean = false,
    val track: Int = 1,
    val events: List<Event> = emptyList(),
    val status: String = "Not started",
    val notificationStatus: String = "Media notification not posted",
    val navigationPreview: Boolean = false
) {
    enum class Command { PLAY, PAUSE, NEXT, PREVIOUS, STOP }
    enum class Source(val label: String) {
        PHONE_BUTTON("Phone button"), ANDROID_CONTROLLER("Local Android controller"), MEDIA_CALLBACK("Media callback")
    }
    data class Event(val atMs: Long, val command: Command, val source: Source)
    val title: String get() = if (navigationPreview) NAVIGATION_STEPS[track - 1].first
        else "ATHR TEST ${track.toString().padStart(3, '0')}"
    val instruction: String? get() = if (navigationPreview) NAVIGATION_STEPS[track - 1].second else null
    val artist: String get() = if (navigationPreview) "NAV DEMO" else "450S PROBE"

    fun selectNavigationPreview(enabled: Boolean): MediaProbeState =
        if (active || navigationPreview == enabled) this else MediaProbeState(navigationPreview = enabled)

    fun receive(command: Command, atMs: Long, source: Source = Source.MEDIA_CALLBACK): MediaProbeState {
        if (!active) return this
        val lastTrack = if (navigationPreview) NAVIGATION_STEPS.size else 999
        return copy(
            active = command != Command.STOP,
            playing = when (command) {
                Command.PLAY -> true
                Command.PAUSE, Command.STOP -> false
                else -> playing
            },
            track = when (command) {
                Command.NEXT -> if (track == lastTrack) 1 else track + 1
                Command.PREVIOUS -> if (track == 1) lastTrack else track - 1
                else -> track
            },
            events = (events + Event(atMs, command, source)).takeLast(20),
            status = if (command == Command.STOP) "Stopped" else "Session active on phone"
        )
    }

    companion object {
        // Short ASCII labels avoid depending on scrolling or unsupported arrow glyphs.
        val NAVIGATION_STEPS = listOf(
            "200M LEFT" to "Turn left in 200 metres",
            "50M RIGHT" to "Turn right in 50 metres",
            "GO STRAIGHT" to "Continue straight",
            "RNDBT EXIT2" to "Take exit 2 at the roundabout",
            "1KM LEFT" to "Turn left in 1 kilometre",
            "ARRIVED" to "You have arrived"
        )
    }
}
