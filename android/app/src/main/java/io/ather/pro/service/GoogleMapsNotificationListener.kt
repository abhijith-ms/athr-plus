package io.ather.pro.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.app.NotificationManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import io.ather.pro.domain.media.GoogleMapsInstruction

class GoogleMapsNotificationListener : NotificationListenerService() {
    override fun onListenerConnected() {
        instance = this
        GoogleMapsDisplayService.listenerConnected()
        snapshot()
    }

    override fun onListenerDisconnected() {
        instance = null
        GoogleMapsDisplayService.listenerDisconnected()
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
            GoogleMapsDisplayService.listenerDisconnected()
        }
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = forward(sbn)

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName == GoogleMapsInstruction.PACKAGE)
            GoogleMapsDisplayService.removed(sbn.key, sbn.packageName)
    }

    private fun forward(sbn: StatusBarNotification, snapshot: Boolean = false) {
        if (!GoogleMapsDisplayService.state.value.running || sbn.packageName != GoogleMapsInstruction.PACKAGE) return
        val notification = sbn.notification
        if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return
        val ongoing = notification.flags and Notification.FLAG_ONGOING_EVENT != 0
        // Discard other apps before touching extras; Maps text is kept only in memory while enabled.
        val fields = if (ongoing) listOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT)
            .map { key -> runCatching { notification.extras.getCharSequence(key)?.toString().orEmpty() }.getOrDefault("") }
        else emptyList()
        val age = if (snapshot) (System.currentTimeMillis() - sbn.postTime).takeIf { it >= 0 }
            ?: io.ather.pro.domain.media.GoogleMapsDisplayState.MAX_AGE_MS else 0L
        GoogleMapsDisplayService.posted(sbn.key, sbn.packageName, ongoing, fields, age)
    }

    companion object {
        private var instance: GoogleMapsNotificationListener? = null
        val connected: Boolean get() = instance != null
        fun hasAccess(context: Context): Boolean = if (Build.VERSION.SDK_INT >= 27)
            context.getSystemService(NotificationManager::class.java)
                .isNotificationListenerAccessGranted(ComponentName(context, GoogleMapsNotificationListener::class.java))
        // shortcut: Android 8.0 uses a package check with one listener; check components if a second is added.
        else NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

        fun snapshot() {
            if (!GoogleMapsDisplayService.state.value.running) return
            val listener = instance ?: return
            try {
                listener.activeNotifications?.filter {
                    it.packageName == GoogleMapsInstruction.PACKAGE &&
                        it.notification.flags and Notification.FLAG_ONGOING_EVENT != 0 &&
                        it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0
                }?.maxByOrNull { it.postTime }?.let { listener.forward(it, snapshot = true) }
            } catch (_: RuntimeException) {
                GoogleMapsDisplayService.listenerDisconnected()
            }
        }
    }
}
