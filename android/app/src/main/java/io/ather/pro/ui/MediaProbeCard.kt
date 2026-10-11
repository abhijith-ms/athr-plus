package io.ather.pro.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.ather.pro.util.MediaProbe
import io.ather.pro.domain.media.MediaProbeState.Source
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun MediaProbeCard() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val probe = remember(context) { MediaProbe(context) }
    val state by probe.state.collectAsState()
    val time = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }
    DisposableEffect(probe, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) probe.stop("Stopped: app left foreground")
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer); probe.stop() }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (state.navigationPreview) "450S navigation preview" else "450S media test",
                style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = state.navigationPreview, enabled = !state.active,
                    onClick = { probe.selectNavigationPreview(true) }, label = { Text("Navigation demo") })
                FilterChip(selected = !state.navigationPreview, enabled = !state.active,
                    onClick = { probe.selectNavigationPreview(false) }, label = { Text("Media test") })
            }
            if (state.navigationPreview) Text("Sample directions only. No real route or location.",
                color = MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.bodyMedium)
            Text("Test while parked with the scooter paired. Starting may pause other music. Keep this screen open and phone unlocked. Leaving stops the test.",
                style = MaterialTheme.typography.bodySmall)
            Text(state.status)
            Text(state.notificationStatus, style = MaterialTheme.typography.bodySmall)
            Text(state.title, style = MaterialTheme.typography.titleLarge)
            state.instruction?.let {
                Text("Sample ${state.track} of ${io.ather.pro.domain.media.MediaProbeState.NAVIGATION_STEPS.size} · $it")
            }
            Text("Artist: ${state.artist} · ${if (state.playing) "Playing" else "Paused"}")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { if (state.active) probe.stop() else probe.start() }) {
                    Text(if (state.active) "Stop test" else if (state.navigationPreview) "Start preview" else "Start test")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { probe.previousTitle() }, enabled = state.active) { Text("Previous") }
                OutlinedButton(onClick = { probe.nextTitle() }, enabled = state.active) { Text("Next") }
            }
            OutlinedButton(onClick = { probe.androidNext() }, enabled = state.active) { Text("Android next") }
            Text("Android next checks the phone’s media controller. Expect NEXT from Local Android controller (or Media callback on Android 8). Check the media notification too before testing the scooter.",
                style = MaterialTheme.typography.bodySmall)
            Text(if (state.navigationPreview) "Check each short direction on the scooter’s music panel. Joystick next/previous should change the sample; play/pause changes playback only. Directions do not advance automatically."
                else "Check the song title on the scooter. Try joystick play/pause and next/previous.",
                style = MaterialTheme.typography.bodySmall)
            Text("Callback labels identify the Android caller, not the physical button. Notification buttons use the local controller.",
                style = MaterialTheme.typography.bodySmall)
            if (state.events.none { it.source != Source.PHONE_BUTTON }) Text("No Android media callbacks received yet.", style = MaterialTheme.typography.bodySmall)
            state.events.asReversed().forEach { event ->
                Text("${time.format(Date(event.atMs))} · ${event.source.label}: ${event.command}",
                    style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
