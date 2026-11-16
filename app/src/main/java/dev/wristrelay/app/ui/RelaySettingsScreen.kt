package dev.wristrelay.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.wristrelay.app.settings.RelaySettings

@Composable
internal fun RelaySettingsScreen(
    settings: RelaySettings,
    enabled: Boolean,
    edit: ((RelaySettings) -> RelaySettings) -> Unit,
) {
    val relayOn = settings.masterEnabled && settings.telegramEnabled
    val cleanupEnabled = enabled && relayOn
    var editingDuration by rememberSaveable { mutableStateOf(false) }
    SettingsSection("Relay to watch") {
        SettingSwitch(
            "Show Telegram messages on the watch",
            "Each new Telegram message appears as a separate watch notification.",
            checked = relayOn,
            enabled = enabled,
            onCheckedChange = { on -> edit { it.copy(masterEnabled = on, telegramEnabled = on) } },
        )
    }
    SettingsSection("Telegram originals") {
        SettingSwitch(
            "Hide Telegram originals",
            "Dismisses a Telegram notification after its new copies are posted. Telegram may alert first.",
            checked = settings.hideTelegramOriginals,
            enabled = cleanupEnabled,
            onCheckedChange = { hide -> edit { it.copy(hideTelegramOriginals = hide) } },
        )
    }
    SettingsSection("Relay cleanup") {
        SettingSwitch(
            "Auto-clear relay notifications",
            "Removes new WristRelay copies from your phone after the duration. Their watch entries may also disappear. " +
                "Telegram originals are unaffected.",
            checked = settings.autoClearEnabled,
            enabled = cleanupEnabled,
            onCheckedChange = { on -> edit { it.copy(autoClearEnabled = on) } },
        )
        if (settings.autoClearEnabled) {
            val unit = if (settings.autoClearMinutes == 1) "minute" else "minutes"
            Text("Auto-clear after", style = MaterialTheme.typography.bodyMedium)
            OutlinedButton(
                onClick = { editingDuration = true },
                enabled = cleanupEnabled,
                modifier = Modifier.fillMaxWidth().semantics {
                    contentDescription = "Auto-clear after ${settings.autoClearMinutes} $unit"
                },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("${settings.autoClearMinutes} $unit", modifier = Modifier.weight(1f))
                    Text("⌄")
                }
            }
        }
    }
    SpotifyAdMuteSettings(settings, enabled, edit)
    if (editingDuration && relayOn && settings.autoClearEnabled) {
        AutoClearDurationDialog(
            savedMinutes = settings.autoClearMinutes,
            onDismiss = { editingDuration = false },
            onApply = { minutes ->
                editingDuration = false
                edit { it.copy(autoClearMinutes = minutes) }
            },
        )
    }
}

@Composable
private fun SpotifyAdMuteSettings(
    settings: RelaySettings,
    enabled: Boolean,
    edit: ((RelaySettings) -> RelaySettings) -> Unit,
) {
    var editingTimeout by rememberSaveable { mutableStateOf(false) }
    SettingsSection("Spotify") {
        SettingSwitch(
            "Mute Spotify ads",
            "Temporarily sets Android media volume to zero, including audio from other apps. " +
                "Requires Notification Access.",
            checked = settings.spotifyAdMuteEnabled,
            enabled = enabled,
            onCheckedChange = { on -> edit { it.copy(spotifyAdMuteEnabled = on) } },
        )
        if (settings.spotifyAdMuteEnabled) {
            OutlinedButton(onClick = { editingTimeout = true }, enabled = enabled) {
                Text("Restore after at most ${settings.spotifyAdMuteSeconds} s")
            }
        }
    }
    if (editingTimeout && settings.spotifyAdMuteEnabled) {
        SpotifyTimeoutDialog(
            settings.spotifyAdMuteSeconds,
            onDismiss = { editingTimeout = false },
            onApply = { seconds ->
                editingTimeout = false
                edit { it.copy(spotifyAdMuteSeconds = seconds) }
            },
        )
    }
}

@Composable
private fun SpotifyTimeoutDialog(savedSeconds: Int, onDismiss: () -> Unit, onApply: (Int) -> Unit) {
    var amount by rememberSaveable(savedSeconds) { mutableStateOf(savedSeconds.toString()) }
    val maximum = RelaySettings.MAX_SPOTIFY_AD_MUTE_SECONDS
    val seconds = amount.toIntOrNull()?.takeIf { it in 1..maximum }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Spotify mute timeout") },
        text = {
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Seconds") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                isError = seconds == null,
                supportingText = { Text("Enter 1–$maximum whole seconds. Applies to future mutes.") },
            )
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = { TextButton(enabled = seconds != null, onClick = { seconds?.let(onApply) }) { Text("Apply") } },
    )
}

@Composable
internal fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        HorizontalDivider()
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        content()
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Switch(
            modifier = Modifier.semantics { contentDescription = title },
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
        Column(
            modifier = Modifier.weight(1f).padding(top = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AutoClearDurationDialog(savedMinutes: Int, onDismiss: () -> Unit, onApply: (Int) -> Unit) {
    var hours by rememberSaveable(savedMinutes) { mutableStateOf(false) }
    var amount by rememberSaveable(savedMinutes) { mutableStateOf(savedMinutes.toString()) }
    val minutes = RelaySettings.durationMinutes(amount, hours)
    val maxAmount = if (hours) RelaySettings.MAX_AUTO_CLEAR_MINUTES / 60 else RelaySettings.MAX_AUTO_CLEAR_MINUTES
    val unit = if (hours) "hours" else "minutes"
    val selectUnit: (Boolean) -> Unit = { nextHours ->
        if (hours != nextHours) {
            amount = when {
                minutes == null -> ""
                !nextHours -> minutes.toString()
                minutes % 60 == 0 -> (minutes / 60).toString()
                else -> ""
            }
            hours = nextHours
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Auto-clear duration") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Applies to future WristRelay notifications.")
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Duration") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    isError = minutes == null,
                    supportingText = { Text("Enter 1–$maxAmount whole $unit.") },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !hours, onClick = { selectUnit(false) }, label = { Text("Minutes") })
                    FilterChip(selected = hours, onClick = { selectUnit(true) }, label = { Text("Hours") })
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = { TextButton(enabled = minutes != null, onClick = { minutes?.let(onApply) }) { Text("Apply") } },
    )
}
