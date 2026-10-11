package io.ather.pro.util

import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.Build
import android.view.KeyEvent
import io.ather.pro.MainActivity
import io.ather.pro.R
import io.ather.pro.domain.media.MediaProbeState
import io.ather.pro.domain.media.MediaProbeState.Command
import io.ather.pro.domain.media.MediaProbeState.Source
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Foreground-only experiment; Android submission does not establish dashboard receipt. */
internal class MediaProbe(context: Context) {
    private val context = context.applicationContext
    private val audio = this.context.getSystemService(AudioManager::class.java)
    private val notifications = this.context.getSystemService(NotificationManager::class.java)
    private val openApp = PendingIntent.getActivity(this.context, NOTIFICATION_ID,
        Intent(this.context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener({ change ->
            if (change < 0) stop("Stopped: audio focus lost")
        }, Handler(Looper.getMainLooper())).build()
    private var player: MediaPlayer? = null
    private var session: MediaSession? = null
    private val mutableState = MutableStateFlow(MediaProbeState(navigationPreview = true))
    val state = mutableState.asStateFlow()

    fun start() {
        if (state.value.active) return
        try {
            if (audio.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                stop("Could not start: audio focus denied")
                return
            }
            player = MediaPlayer.create(context, R.raw.media_probe_silence, attributes, 0)
                ?: error("Silent playback unavailable")
            player!!.apply {
                isLooping = true
                setVolume(0f, 0f)
                setOnErrorListener { _, _, _ -> stop("Stopped: playback failed"); true }
            }
            session = MediaSession(context, "Athr450SMediaProbe")
            session!!.apply {
                setSessionActivity(openApp)
                setPlaybackToLocal(attributes)
                if (Build.VERSION.SDK_INT >= 31) {
                    setMediaButtonBroadcastReceiver(ComponentName(context, MediaProbeButtonReceiver::class.java))
                } else {
                    @Suppress("DEPRECATION")
                    setMediaButtonReceiver(PendingIntent.getBroadcast(context, NOTIFICATION_ID,
                        Intent(Intent.ACTION_MEDIA_BUTTON).setClass(context, MediaProbeButtonReceiver::class.java),
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
                }
                setCallback(object : MediaSession.Callback() {
                    override fun onPlay() = callback(Command.PLAY)
                    override fun onPause() = callback(Command.PAUSE)
                    override fun onSkipToNext() = callback(Command.NEXT)
                    override fun onSkipToPrevious() = callback(Command.PREVIOUS)
                    override fun onStop() = callback(Command.STOP)
                }, Handler(Looper.getMainLooper()))
                isActive = true
            }
            MediaProbeButtonReceiver.session = session
            notifications.createNotificationChannel(NotificationChannel(CHANNEL_ID, "450S Media Test",
                NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
            mutableState.value = MediaProbeState(active = true, playing = true, status = "Session active on phone",
                navigationPreview = state.value.navigationPreview)
            publish()
        } catch (_: RuntimeException) {
            stop("Could not start media test")
        }
    }

    fun nextTitle() = receive(Command.NEXT, Source.PHONE_BUTTON)
    fun previousTitle() = receive(Command.PREVIOUS, Source.PHONE_BUTTON)
    fun selectNavigationPreview(enabled: Boolean) {
        mutableState.value = state.value.selectNavigationPreview(enabled)
    }

    fun androidNext() {
        if (!state.value.active) return
        try { session?.controller?.transportControls?.skipToNext() }
        catch (_: RuntimeException) { stop("Stopped: Android controller check failed") }
    }

    private fun callback(command: Command) {
        val localController = Build.VERSION.SDK_INT >= 28 &&
            session?.currentControllerInfo?.packageName == context.packageName
        receive(command, if (localController) Source.ANDROID_CONTROLLER else Source.MEDIA_CALLBACK)
    }

    private fun receive(command: Command, source: Source) {
        if (!state.value.active) return
        mutableState.value = state.value.receive(command, System.currentTimeMillis(), source)
        if (command == Command.STOP) stop("Stopped by media command")
        else try { publish() } catch (_: RuntimeException) { stop("Stopped: media update failed") }
    }

    private fun publish() {
        val value = state.value
        val metadata = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, value.title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, value.artist)
            .putString(MediaMetadata.METADATA_KEY_ALBUM,
                if (value.navigationPreview) "Athr+ sample navigation" else "Athr+ display test")
            .putLong(MediaMetadata.METADATA_KEY_DURATION, 1_000L)
            .build()
        session?.setMetadata(metadata)
        session?.setQueue(listOf(MediaSession.QueueItem(metadata.description, value.track.toLong())))
        session?.setQueueTitle("Now playing")
        if (value.playing) player?.start() else player?.pause()
        session?.setPlaybackState(PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_STOP)
            .setState(if (value.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                player?.currentPosition?.toLong() ?: 0L, if (value.playing) 1f else 0f)
            .setActiveQueueItemId(value.track.toLong())
            .build())
        postNotification()
    }

    private fun button(code: Int): PendingIntent = PendingIntent.getBroadcast(context, code,
        Intent(Intent.ACTION_MEDIA_BUTTON).setClass(context, MediaProbeButtonReceiver::class.java)
            .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(KeyEvent.ACTION_DOWN, code)),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    private fun postNotification() {
        val token = session?.sessionToken ?: return
        val playing = state.value.playing
        try {
            val notification = Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(state.value.title).setContentText(state.value.artist)
                .setContentIntent(openApp).setOnlyAlertOnce(true).setOngoing(true)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .addAction(Notification.Action.Builder(Icon.createWithResource(context, android.R.drawable.ic_media_previous), "Previous",
                    button(KeyEvent.KEYCODE_MEDIA_PREVIOUS)).build())
                .addAction(Notification.Action.Builder(Icon.createWithResource(context, if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play),
                    if (playing) "Pause" else "Play", button(if (playing) KeyEvent.KEYCODE_MEDIA_PAUSE else KeyEvent.KEYCODE_MEDIA_PLAY)).build())
                .addAction(Notification.Action.Builder(Icon.createWithResource(context, android.R.drawable.ic_media_next), "Next",
                    button(KeyEvent.KEYCODE_MEDIA_NEXT)).build())
                .setStyle(Notification.MediaStyle().setMediaSession(token).setShowActionsInCompactView(0, 1, 2))
                .build()
            notifications.notify(NOTIFICATION_ID, notification)
            mutableState.value = state.value.copy(notificationStatus = if (notifications.activeNotifications.any { it.id == NOTIFICATION_ID })
                "Media notification registered with Android" else "Media notification not registered; check Android settings")
        } catch (_: RuntimeException) {
            mutableState.value = state.value.copy(notificationStatus = "Media notification could not be posted")
        }
    }

    fun stop(reason: String = "Stopped") {
        MediaProbeButtonReceiver.session = null
        runCatching { notifications.cancel(NOTIFICATION_ID) }
        session?.release()
        session = null
        player?.release()
        player = null
        audio.abandonAudioFocusRequest(focus)
        mutableState.value = state.value.copy(active = false, playing = false, status = reason,
            notificationStatus = "Media notification removed")
    }

    private companion object {
        const val CHANNEL_ID = "450s_media_probe"
        const val NOTIFICATION_ID = 1201
    }
}
