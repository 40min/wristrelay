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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import dev.wristrelay.app.debug.DebugEvent
import dev.wristrelay.app.debug.DebugEventStore
import dev.wristrelay.app.debug.DebugFormat

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
                        Text("Project skeleton. Telegram notification relay is not yet implemented.")
                        Text("Notification access allows Android to connect the listener. This skeleton does not read or relay notifications.")
                        Button(onClick = {
                            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        }) { Text("Open notification access settings") }
                        Text("Notification access: " + if (listenerAccessGranted.value) "granted" else "not granted")
                        Text("Notification posting: " + if (postingAllowed.value) "allowed" else "not allowed")
                        if (Build.VERSION.SDK_INT >= 33 && !postingAllowed.value) {
                            Button(onClick = {
                                permissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }) { Text("Allow notification posting") }
                        }
                        RelayToggle()

                        Text("Debug capture (metadata only)", style = MaterialTheme.typography.titleMedium)
                        Text("Sanitized notification metadata from external apps; no text content, bounded in-memory history (max ${DebugEventStore.MAX_EVENTS}), cleared when the app process dies.")
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
}

@Composable
private fun RelayToggle() {
    val enabled = remember { mutableStateOf(dev.wristrelay.app.settings.PrototypeRelaySwitch.enabled) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Switch(checked = enabled.value, onCheckedChange = { checked ->
                enabled.value = checked
                dev.wristrelay.app.settings.PrototypeRelaySwitch.enabled = checked
            })
            Text("Prototype relay (task 0.2)")
        }
        Text(
            "Off by default. While on, each genuinely new Telegram message is published as its own independent WristRelay notification. Telegram's notifications are never touched.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
