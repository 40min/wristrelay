package dev.wristrelay.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import dev.wristrelay.app.debug.DebugEvent
import dev.wristrelay.app.debug.DebugEventStore
import dev.wristrelay.app.debug.DebugFormat
import dev.wristrelay.app.debug.SpotifyProbe
import dev.wristrelay.app.model.RelayMessage
import dev.wristrelay.app.relay.RelayNotificationPublisher
import dev.wristrelay.app.settings.SettingsGate
import dev.wristrelay.app.settings.SettingsStore
import dev.wristrelay.app.ui.RelaySettingsScreen
import dev.wristrelay.app.ui.SettingsSection
import dev.wristrelay.app.ui.WristRelayTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val postingAllowed = mutableStateOf(false)
    private val listenerAccessGranted = mutableStateOf(false)
    private val captured = mutableStateListOf<DebugEvent>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshPermission()
        setContent {
            val permissionRequest = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { refreshPermission() }
            WristRelayTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("WristRelay", style = MaterialTheme.typography.headlineLarge)
                        RelaySettingsContent()
                        SettingsSection("Advanced") {
                            PermissionControls(permissionRequest)
                            GarminGuide()
                            if (BuildConfig.DEBUG) DebugSection()
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermission()
        captured.clear()
        captured.addAll(DebugEventStore.snapshot())
    }

    private fun refreshPermission() {
        listenerAccessGranted.value = NotificationManagerCompat.getEnabledListenerPackages(this)
            .contains(packageName)
        postingAllowed.value = getSystemService(android.app.NotificationManager::class.java)
            .areNotificationsEnabled() && (Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    }

    @Composable
    private fun DebugSection() {
        var expanded by remember { mutableStateOf(false) }
        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Debug ▴" else "Debug ▾") }
        if (!expanded) return
        Button(onClick = {
            RelayNotificationPublisher(applicationContext).publish(
                listOf(
                    RelayMessage(
                        conversationTitle = "Test conversation",
                        isGroup = false,
                        sender = null,
                        text = "Test relay entry ONE",
                    ),
                ),
                null,
                null,
                SettingsGate.current,
            )
        }) { Text("Send test relay notification") }
        SpotifyProbeSection()
        Text("Debug capture (metadata only)", style = MaterialTheme.typography.titleMedium)
        Text("Sanitized notification metadata from external apps; no text content, " +
            "bounded in-memory history (max ${DebugEventStore.MAX_EVENTS}), " +
            "cleared when the app process dies.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = {
                captured.clear()
                captured.addAll(DebugEventStore.snapshot())
            }) { Text("Refresh") }
            OutlinedButton(onClick = {
                DebugEventStore.clear()
                captured.clear()
            }) { Text("Clear") }
            OutlinedButton(onClick = {
                val report = captured.joinToString("\n\n") { event ->
                    DebugFormat.format(event).joinToString("\n")
                }
                val clipboard = getSystemService(ClipboardManager::class.java)
                clipboard.setPrimaryClip(ClipData.newPlainText("wristrelay-debug", report))
            }) { Text("Copy all") }
        }
        captured.forEach { event ->
            val lines = DebugFormat.format(event)
            Column(
                modifier = Modifier.padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                lines.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        softWrap = false,
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    )
                }
            }
        }
    }

    @Composable
    private fun SpotifyProbeSection() {
        val enabled by SpotifyProbe.enabled.collectAsState()
        val events by SpotifyProbe.events.collectAsState()
        OutlinedButton(onClick = { SpotifyProbe.setEnabled(!enabled) }) {
            Text(if (enabled) "Stop Spotify probe" else "Start Spotify probe")
        }
        if (enabled) {
            Text(
                "Play Spotify until a track changes to an ad and back.",
                style = MaterialTheme.typography.bodySmall,
            )
            OutlinedButton(
                enabled = events.isNotEmpty(),
                onClick = {
                    val report = SpotifyProbe.events.value.joinToString("\n")
                    val clipboard = getSystemService(ClipboardManager::class.java)
                    clipboard.setPrimaryClip(ClipData.newPlainText("wristrelay-spotify", report))
                },
            ) { Text("Copy Spotify log") }
            if (events.isEmpty()) {
                Text("Waiting for notification listener…")
            } else {
                Text(
                    "Latest ${minOf(events.size, SpotifyProbe.TAIL_EVENTS)} of ${events.size} retained events",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            events.takeLast(SpotifyProbe.TAIL_EVENTS).forEach { event ->
                Text(event, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    @Composable
    private fun PermissionControls(permissionRequest: androidx.activity.result.ActivityResultLauncher<String>) {
        PermissionStatus("Notification access", listenerAccessGranted.value)
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
        ) { Text("Open notification access settings") }
        PermissionStatus("Post notifications", postingAllowed.value)
        if (Build.VERSION.SDK_INT >= 33 && !postingAllowed.value) {
            OutlinedButton(
                modifier = Modifier.fillMaxWidth(),
                onClick = { permissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS) },
            ) { Text("Allow showing notifications") }
        }
    }

    @Composable
    private fun PermissionStatus(label: String, allowed: Boolean) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            Text(
                if (allowed) "On" else "Off",
                color = if (allowed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    @Composable
    private fun RelaySettingsContent() {
        val store = remember { SettingsStore(applicationContext) }
        val loadedSettings by store.flow.collectAsState(initial = null)
        val scope = rememberCoroutineScope()
        var saving by remember { mutableStateOf(false) }
        val settings = loadedSettings
        if (settings == null) {
            Text("Loading saved settings…", style = MaterialTheme.typography.bodySmall)
            return
        }
        RelaySettingsScreen(settings, enabled = !saving) { edit ->
            if (!saving) {
                saving = true
                scope.launch {
                    try {
                        val updated = edit(store.current())
                        store.save(updated)
                        SettingsGate.update(updated)
                    } finally {
                        saving = false
                    }
                }
            }
        }
    }

    @Composable
    private fun GarminGuide() {
        var expanded by remember { mutableStateOf(false) }
        TextButton(onClick = { expanded = !expanded }) {
            Text(if (expanded) "Hide Garmin Connect guide" else "Garmin Connect guide")
        }
        if (!expanded) return
        Text(
            "1. Allow Telegram phone notifications so WristRelay can receive them.\n" +
                "2. In Garmin Connect, open the app notification settings (menu names " +
                "vary by Connect version).\n" +
                "3. Turn off forwarding for Telegram; turn it on for WristRelay.\n" +
                "4. Send yourself a test message without opening Telegram and check " +
                "your watch.\n" +
                "Only WristRelay should reach the watch; other apps keep their " +
                "existing settings.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
