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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import dev.wristrelay.app.debug.DebugEvent
import dev.wristrelay.app.debug.DebugEventStore
import dev.wristrelay.app.debug.DebugFormat
import dev.wristrelay.app.model.RelayMessage
import dev.wristrelay.app.relay.RelayNotificationPublisher
import dev.wristrelay.app.settings.RelaySettings
import dev.wristrelay.app.settings.SettingsGate
import dev.wristrelay.app.settings.SettingsStore
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
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                            .verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("WristRelay", style = MaterialTheme.typography.headlineLarge)
                        Text(
                            "Shows every new Telegram message on your watch as its own " +
                                "separate entry. You can optionally hide the Telegram originals " +
                                "after their copies are posted."
                        )

                        SettingsSection()

                        PermissionControls(permissionRequest)
                        StatusLines()
                        GarminGuide()

                        if (BuildConfig.DEBUG) {
                            DebugSection()
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
            )
        }) { Text("Send test relay notification") }
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
    private fun PermissionControls(permissionRequest: androidx.activity.result.ActivityResultLauncher<String>) {
        Button(onClick = {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }) { Text("Open notification access settings") }
        if (Build.VERSION.SDK_INT >= 33 && !postingAllowed.value) {
            Button(onClick = {
                permissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
            }) { Text("Allow showing notifications") }
        }
    }

    @Composable
    private fun StatusLines() {
        Text("Access to your phone notifications: " + if (listenerAccessGranted.value) "on" else "off")
        Text("Permission to show WristRelay notifications: " + if (postingAllowed.value) "on" else "off")
        Text(
            "Relay active: " + (if (SettingsGate.relayingAllowed()) "on" else "off") +
                " (Show Telegram messages must be on)",
            style = MaterialTheme.typography.bodySmall,
        )
    }

    @Composable
    private fun SettingsSection() {
        val store = remember { SettingsStore(applicationContext) }
        val settings by store.flow.collectAsState(initial = SettingsGate.current)
        val scope = rememberCoroutineScope()

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    modifier = Modifier.semantics { contentDescription = "Show Telegram messages on the watch" },
                    checked = settings.masterEnabled && settings.telegramEnabled,
                    onCheckedChange = { on ->
                        val updated = settings.copy(masterEnabled = on, telegramEnabled = on)
                        SettingsGate.update(updated)
                        scope.launch { store.save(updated) }
                    },
                )
                Text("Show Telegram messages on the watch")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    modifier = Modifier.semantics { contentDescription = "Hide Telegram originals" },
                    checked = settings.hideTelegramOriginals,
                    enabled = settings.masterEnabled && settings.telegramEnabled,
                    onCheckedChange = { hide ->
                        val updated = settings.copy(hideTelegramOriginals = hide)
                        SettingsGate.update(updated)
                        scope.launch { store.save(updated) }
                    },
                )
                Text("Hide Telegram originals")
            }
            Text(
                "Off by default. Dismisses each Telegram original only after its new message copies " +
                    "are confirmed posted. Existing notifications are not swept or restored. " +
                    "Telegram may briefly alert before dismissal.",
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                "Each new Telegram message appears on your watch as its own entry. " +
                    "Off by default; saved only on this device.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    @Composable
    private fun GarminGuide() {
        Text("Connect your watch", style = MaterialTheme.typography.titleMedium)
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
