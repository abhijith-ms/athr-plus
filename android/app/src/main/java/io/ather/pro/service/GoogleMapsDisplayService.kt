package io.ather.pro.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.IBinder
import android.os.Build
import android.os.SystemClock
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import io.ather.pro.MainActivity
import io.ather.pro.domain.media.GoogleMapsDisplayState
import io.ather.pro.util.MediaProbe
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class GoogleMapsDisplayService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var probe: MediaProbe
    private var ending = false

    override fun onCreate() {
        super.onCreate()
        probe = MediaProbe(this)
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) { finish("Stopped"); return START_NOT_STICKY }
        if (intent?.action != START || ending) { stopSelf(); return START_NOT_STICKY }
        if (state.value.running) return START_NOT_STICKY
        if (!GoogleMapsNotificationListener.hasAccess(this)) {
            finish("Allow notification access before starting")
            return START_NOT_STICKY
        }
        try {
            ServiceCompat.startForeground(this, NOTIFICATION_ID, notification("Starting Google Maps display"),
                if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0)
            probe.startLiveNavigation()
            if (!probe.state.value.active) {
                finish(probe.state.value.status)
                return START_NOT_STICKY
            }
            mutableState.value = GoogleMapsDisplayState(running = true,
                listenerConnected = GoogleMapsNotificationListener.connected, status = "Start navigation in Google Maps")
            scope.launch {
                probe.state.collect { media ->
                    if (!media.active) finish(media.status) else render()
                }
            }
            GoogleMapsNotificationListener.snapshot()
            scope.launch {
                while (isActive && !ending) {
                    if (!GoogleMapsNotificationListener.hasAccess(this@GoogleMapsDisplayService)) {
                        finish("Notification access removed")
                        break
                    }
                    render()
                    delay(5_000)
                }
            }
        } catch (_: RuntimeException) {
            finish("Could not start navigation display")
        }
        return START_NOT_STICKY
    }

    private fun render() {
        if (ending || !state.value.running) return
        val now = SystemClock.elapsedRealtime()
        val previous = state.value
        val current = previous.copy(playing = probe.state.value.playing)
        val status = if (!current.playing) "Display paused; use play to resume" else current.statusAt(now)
        val next = current.copy(status = status)
        mutableState.value = next
        probe.updateNavigationTitle(next.labelAt(now))
        if (status != previous.status) getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification(status))
    }

    private fun accept(next: GoogleMapsDisplayState) {
        if (ending) return
        if (!next.running) { finish(next.status); return }
        mutableState.value = next
        render()
    }

    private fun notification(message: String): Notification {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Google Maps display", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false); setSound(null, null)
            })
        val open = PendingIntent.getActivity(this, NOTIFICATION_ID, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, NOTIFICATION_ID, Intent(this, GoogleMapsDisplayService::class.java).setAction(STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CHANNEL_ID).setSmallIcon(android.R.drawable.ic_menu_directions)
            .setContentTitle("Athr+ Google Maps display").setContentText(message)
            .setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true)
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                "Stop display", stop).build()).build()
    }

    private fun finish(reason: String) {
        if (ending) return
        ending = true
        mutableState.value = state.value.stopped(reason)
        scope.cancel()
        probe.stop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        instance = null
        scope.cancel()
        probe.stop()
        if (state.value.running) mutableState.value = state.value.stopped("Navigation service stopped")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val START = "io.ather.pro.START_MAPS_DISPLAY"
        private const val STOP = "io.ather.pro.STOP_MAPS_DISPLAY"
        private const val CHANNEL_ID = "google_maps_display"
        const val NOTIFICATION_ID = 1302
        private var instance: GoogleMapsDisplayService? = null
        private val mutableState = MutableStateFlow(GoogleMapsDisplayState())
        val state = mutableState.asStateFlow()

        fun start(context: Context) {
            if (state.value.running) return
            try {
                ContextCompat.startForegroundService(context, Intent(context, GoogleMapsDisplayService::class.java).setAction(START))
            } catch (_: RuntimeException) {
                mutableState.value = GoogleMapsDisplayState(status = "Could not start navigation display")
            }
        }
        fun stop(context: Context) {
            instance?.finish("Stopped") ?: context.stopService(Intent(context, GoogleMapsDisplayService::class.java))
        }
        fun listenerConnected() {
            if (state.value.running) mutableState.value = state.value.copy(listenerConnected = true, status = "Waiting for Google Maps instruction")
        }
        fun listenerDisconnected() {
            if (state.value.running) instance?.accept(state.value.disconnected())
        }
        fun posted(key: String, packageName: String, ongoing: Boolean, fields: List<String>, ageMs: Long) {
            if (!state.value.running) return
            val receivedAt = SystemClock.elapsedRealtime() - ageMs
            instance?.accept(state.value.posted(key, packageName, ongoing, fields, receivedAt))
        }
        fun removed(key: String, packageName: String) {
            if (state.value.running) instance?.accept(state.value.removed(key, packageName))
        }
    }
}
