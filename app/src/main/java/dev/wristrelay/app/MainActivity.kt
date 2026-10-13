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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
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
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                            .verticalScroll(rememberScrollState()).padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text("WristRelay", style = MaterialTheme.typography.headlineLarge)
                        Text("Relays each genuinely new Telegram message to its own " +
                            "notification for Garmin Connect mirroring. " +
                            "Telegram's phone notifications are never touched.")

                    SettingsSection()

                        PermissionControls(permissionRequest)
                        StatusLines()
                        TestRelayButton()
                        GarminGuide()

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
    private fun PermissionControls(permissionRequest: androidx.activity.result.ActivityResultLauncher<String>) {
        Button(onClick = {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }) { Text("Open notification access settings") }
        if (Build.VERSION.SDK_INT >= 33 && !postingAllowed.value) {
            Button(onClick = {
                permissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
            }) { Text("Allow notification posting") }
        }
    }

    @Composable
    private fun StatusLines() {
        Text("Notification access: " + if (listenerAccessGranted.value) "granted" else "not granted")
        Text("Notification posting: " + if (postingAllowed.value) "allowed" else "not allowed")
        Text(
            "Relay active: " + (if (SettingsGate.relayingAllowed()) "on" else "off") +
                " (both toggles must be on)",
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
                    checked = settings.masterEnabled,
                    onCheckedChange = { master ->
                        val updated = settings.copy(masterEnabled = master)
                        SettingsGate.update(updated)
                        scope.launch { store.save(updated) }
                    },
                )
                Text("Master relay switch")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = settings.telegramEnabled,
                    onCheckedChange = { telegram ->
                        val updated = settings.copy(telegramEnabled = telegram)
                        SettingsGate.update(updated)
                        scope.launch { store.save(updated) }
                    },
                )
                Text("Relay Telegram messages")
            }
            Text(
                "Both switches stay off by default and are saved on this device. " +
                    "While both are on, each genuinely new Telegram message is published " +
                    "as its own independent WristRelay notification. Telegram's " +
                    "notifications are never touched. No message contents are stored " +
                    "anywhere by WristRelay.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }

    @Composable
    private fun TestRelayButton() {
        OutlinedButton(onClick = {
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
    }

    @Composable
    private fun GarminGuide() {
        Text("Garmin Connect app filtering", style = MaterialTheme.typography.titleMedium)
        Text(
            "1. Keep Telegram's phone notifications as they are (delete nothing).\n" +
                "2. In Garmin Connect, open app notification settings (menu path varies by " +
                "Connect version).\n" +
                "3. Disable forwarding for Telegram; enable forwarding for WristRelay.\n" +
                "4. Send a test message on the phone without opening Telegram and check the " +
                "watch entry.\n" +
                "Only WristRelay should be forwarded to the watch; other apps keep their " +
                "existing forwarding settings.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
