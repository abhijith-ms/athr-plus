package io.ather.pro.domain.media

data class MediaProbeState(
    val active: Boolean = false,
    val playing: Boolean = false,
    val track: Int = 1,
    val events: List<Event> = emptyList(),
    val status: String = "Not started",
    val notificationStatus: String = "Media notification not posted"
) {
    enum class Command { PLAY, PAUSE, NEXT, PREVIOUS, STOP }
    enum class Source(val label: String) {
        PHONE_BUTTON("Phone button"), ANDROID_CONTROLLER("Local Android controller"), MEDIA_CALLBACK("Media callback")
    }
    data class Event(val atMs: Long, val command: Command, val source: Source)
    val title: String get() = "ATHR TEST ${track.toString().padStart(3, '0')}"

    fun receive(command: Command, atMs: Long, source: Source = Source.MEDIA_CALLBACK): MediaProbeState {
        if (!active) return this
        return copy(
            active = command != Command.STOP,
            playing = when (command) {
                Command.PLAY -> true
                Command.PAUSE, Command.STOP -> false
                else -> playing
            },
            track = when (command) {
                Command.NEXT -> if (track == 999) 1 else track + 1
                Command.PREVIOUS -> if (track == 1) 999 else track - 1
                else -> track
            },
            events = (events + Event(atMs, command, source)).takeLast(20),
            status = if (command == Command.STOP) "Stopped" else "Session active on phone"
        )
    }
}
