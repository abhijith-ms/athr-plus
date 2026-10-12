package io.ather.pro.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.ather.pro.domain.media.GoogleMapsInstruction
import io.ather.pro.service.GoogleMapsDisplayService
import io.ather.pro.service.GoogleMapsNotificationListener
import android.os.SystemClock

@Composable
internal fun GoogleMapsDisplayCard() {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val state by GoogleMapsDisplayService.state.collectAsState()
    var access by remember { mutableStateOf(GoogleMapsNotificationListener.hasAccess(context)) }
    var explainAccess by remember { mutableStateOf(false) }
    var showSource by remember { mutableStateOf(false) }
    var launchError by remember { mutableStateOf<String?>(null) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) access = GoogleMapsNotificationListener.hasAccess(context)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Google Maps on 450S", style = MaterialTheme.typography.titleMedium)
            Text("Mirror English navigation instructions to the music display, including with the screen off. Keep the usual scooter pairing and official Ather app connected. Starting may pause music.",
                style = MaterialTheme.typography.bodySmall)
            Text(state.status)
            if (state.running) Text(state.labelAt(SystemClock.elapsedRealtime()), style = MaterialTheme.typography.titleLarge)
            Text(if (access) "Notification access enabled" else "Notification access required", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { explainAccess = true }) { Text("Notification access settings") }
            Button(enabled = state.running || access, onClick = {
                if (state.running) GoogleMapsDisplayService.stop(context) else GoogleMapsDisplayService.start(context)
            }) { Text(if (state.running) "Stop display" else "Start Maps display") }
            OutlinedButton(onClick = {
                val intent = context.packageManager.getLaunchIntentForPackage(GoogleMapsInstruction.PACKAGE)
                try {
                    if (intent == null) launchError = "Google Maps is not available on this phone"
                    else { context.startActivity(intent); launchError = null }
                } catch (_: RuntimeException) { launchError = "Could not open Google Maps" }
            }) { Text("Open Google Maps") }
            launchError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Text("Start a route in Google Maps. CHECK MAP means the instruction is unreadable or stale. Check Maps for turn timing when distance is missing. Joystick next/previous never advances a live turn; only Maps updates it. Play/pause controls the display.",
                style = MaterialTheme.typography.bodySmall)
            if (state.mapsText.isNotBlank()) {
                TextButton(onClick = { showSource = !showSource }) { Text(if (showSource) "Hide Maps text" else "Show latest Maps text") }
                if (showSource) Text(state.mapsText, style = MaterialTheme.typography.bodySmall)
            }
            Text("First test while parked: compare with Maps, lock the phone, then confirm updates continue. Stop from here or the notification. Street names stay on the phone and are cleared when stopped.",
                style = MaterialTheme.typography.bodySmall)
        }
    }
    if (explainAccess) AlertDialog(onDismissRequest = { explainAccess = false },
        title = { Text("Allow notification access?") },
        text = { Text("Android grants access to all notifications. Athr+ filters to ongoing Google Maps notifications while this display is enabled. Maps text stays in memory on this phone; it is not logged, saved, or uploaded. Enable Athr+ Google Maps display in Android settings, then return and start it.") },
        confirmButton = { TextButton(onClick = {
            explainAccess = false
            try { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            catch (_: RuntimeException) { launchError = "Could not open notification access settings" }
        }) { Text("Open Android settings") } },
        dismissButton = { TextButton(onClick = { explainAccess = false }) { Text("Cancel") } })
}
