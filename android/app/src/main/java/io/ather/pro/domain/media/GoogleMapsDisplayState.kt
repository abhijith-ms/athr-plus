package io.ather.pro.domain.media

data class GoogleMapsDisplayState(
    val running: Boolean = false,
    val listenerConnected: Boolean = false,
    val notificationKey: String? = null,
    val instruction: GoogleMapsInstruction? = null,
    val receivedAtMs: Long? = null,
    val mapsText: String = "",
    val status: String = "Off",
    val playing: Boolean = true
) {
    fun posted(key: String, packageName: String, ongoing: Boolean, fields: List<String>, receivedAt: Long): GoogleMapsDisplayState {
        if (!running || packageName != GoogleMapsInstruction.PACKAGE) return this
        if (!ongoing) return if (key == notificationKey) stopped("Google Maps navigation ended") else this
        val parsed = GoogleMapsInstruction.parse(fields)
        return copy(notificationKey = key, instruction = parsed, receivedAtMs = receivedAt,
            mapsText = fields.take(3).joinToString("\n") { it.take(500) }.take(800),
            status = if (parsed.supported) "Reading Google Maps instructions" else "Unsupported instruction; check Google Maps")
    }

    fun removed(key: String, packageName: String): GoogleMapsDisplayState =
        if (running && packageName == GoogleMapsInstruction.PACKAGE && key == notificationKey)
            stopped("Google Maps navigation ended") else this

    fun disconnected(): GoogleMapsDisplayState = copy(listenerConnected = false,
        notificationKey = null, instruction = null, receivedAtMs = null, mapsText = "",
        status = "Notification listener disconnected; check Google Maps")

    fun stopped(reason: String = "Stopped"): GoogleMapsDisplayState = GoogleMapsDisplayState(status = reason)

    fun fresh(now: Long): Boolean = receivedAtMs?.let { now - it in 0 until MAX_AGE_MS } == true
    fun labelAt(now: Long): String = when {
        !running -> "NAV OFF"
        !playing -> "PAUSED"
        !listenerConnected -> "CHECK MAP"
        instruction == null -> "OPEN MAPS"
        !fresh(now) -> "CHECK MAP"
        else -> instruction.label
    }
    fun statusAt(now: Long): String = when {
        !running -> status
        !listenerConnected -> "Notification listener disconnected; check Google Maps"
        instruction == null -> "Start navigation in Google Maps"
        !fresh(now) -> "No fresh Maps instruction for 2 minutes; check Google Maps"
        instruction.supported -> "Reading Google Maps instructions"
        else -> "Unsupported instruction; check Google Maps"
    }

    companion object { const val MAX_AGE_MS = 120_000L }
}
