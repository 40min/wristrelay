package dev.wristrelay.app.debug

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import dev.wristrelay.app.model.RelayMessage
import dev.wristrelay.app.relay.RelayNotificationPublisher
import dev.wristrelay.app.settings.SettingsGate

internal class DebugPanel(private val context: Context) {
    private val captured = mutableStateListOf<DebugEvent>()

    @Composable
    fun Content() {
        var expanded by remember { mutableStateOf(false) }
        TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Debug ▴" else "Debug ▾") }
        if (!expanded) return
        Button(onClick = {
            RelayNotificationPublisher(context.applicationContext).publish(
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
        val capturing by DebugEventStore.enabled.collectAsState()
        Text("Captures notification metadata from external apps while enabled. " +
            "Keeps up to ${DebugEventStore.MAX_EVENTS} events in memory until cleared or the process ends.")
        OutlinedButton(onClick = {
            DebugEventStore.setEnabled(!capturing)
            refreshCapture()
        }) { Text(if (capturing) "Stop capture" else "Start capture") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = ::refreshCapture) { Text("Refresh") }
            OutlinedButton(onClick = {
                DebugEventStore.clear()
                captured.clear()
            }) { Text("Clear") }
            OutlinedButton(onClick = {
                val report = captured.joinToString("\n\n") { event ->
                    DebugFormat.format(event).joinToString("\n")
                }
                val clipboard = context.getSystemService(ClipboardManager::class.java)
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

    fun refreshCapture() {
        captured.clear()
        captured.addAll(DebugEventStore.snapshot())
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
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
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
}
