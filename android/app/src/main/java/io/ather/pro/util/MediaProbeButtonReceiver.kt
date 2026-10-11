package io.ather.pro.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.session.MediaSession
import android.os.Build
import android.view.KeyEvent

/** Only forwards to the currently running foreground test; never starts playback. */
internal class MediaProbeButtonReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MEDIA_BUTTON) return
        val event = if (Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT, KeyEvent::class.java)
        else @Suppress("DEPRECATION") (intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT) as? KeyEvent)
        if (event != null) session?.controller?.dispatchMediaButtonEvent(event)
    }

    companion object { internal var session: MediaSession? = null }
}
