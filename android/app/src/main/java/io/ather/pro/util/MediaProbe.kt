package io.ather.pro.util

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import io.ather.pro.R
import io.ather.pro.domain.media.MediaProbeState
import io.ather.pro.domain.media.MediaProbeState.Command
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Foreground-only experiment; Android submission does not establish dashboard receipt. */
internal class MediaProbe(context: Context) {
    private val context = context.applicationContext
    private val audio = this.context.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(attributes)
        .setOnAudioFocusChangeListener({ change ->
            if (change < 0) stop("Stopped: audio focus lost")
        }, Handler(Looper.getMainLooper())).build()
    private var player: MediaPlayer? = null
    private var session: MediaSession? = null
    private val mutableState = MutableStateFlow(MediaProbeState())
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
            session = MediaSession(context, "Athr450SMediaProbe").apply {
                setCallback(object : MediaSession.Callback() {
                    override fun onPlay() = receive(Command.PLAY)
                    override fun onPause() = receive(Command.PAUSE)
                    override fun onSkipToNext() = receive(Command.NEXT)
                    override fun onSkipToPrevious() = receive(Command.PREVIOUS)
                    override fun onStop() = receive(Command.STOP)
                }, Handler(Looper.getMainLooper()))
                isActive = true
            }
            mutableState.value = MediaProbeState(active = true, playing = true, status = "Session active on phone")
            publish()
        } catch (_: RuntimeException) {
            stop("Could not start media test")
        }
    }

    fun nextTitle() = receive(Command.NEXT, remote = false)

    private fun receive(command: Command, remote: Boolean = true) {
        if (!state.value.active) return
        mutableState.value = state.value.receive(command, System.currentTimeMillis(), remote)
        if (command == Command.STOP) stop("Stopped by media command")
        else try { publish() } catch (_: RuntimeException) { stop("Stopped: media update failed") }
    }

    private fun publish() {
        val value = state.value
        session?.setMetadata(MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, value.title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, "450S PROBE")
            .putString(MediaMetadata.METADATA_KEY_ALBUM, "Athr+ display test")
            .build())
        if (value.playing) player?.start() else player?.pause()
        session?.setPlaybackState(PlaybackState.Builder()
            .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or
                PlaybackState.ACTION_PLAY_PAUSE or PlaybackState.ACTION_SKIP_TO_NEXT or
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or PlaybackState.ACTION_STOP)
            .setState(if (value.playing) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED,
                PlaybackState.PLAYBACK_POSITION_UNKNOWN, if (value.playing) 1f else 0f)
            .build())
    }

    fun stop(reason: String = "Stopped") {
        session?.release()
        session = null
        player?.release()
        player = null
        audio.abandonAudioFocusRequest(focus)
        mutableState.value = state.value.copy(active = false, playing = false, status = reason)
    }
}
