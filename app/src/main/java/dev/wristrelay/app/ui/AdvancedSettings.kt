package dev.wristrelay.app.ui

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun PermissionControls(
    listenerAccessGranted: Boolean,
    postingAllowed: Boolean,
    openNotificationAccessSettings: () -> Unit,
    requestPostingPermission: () -> Unit,
) {
    PermissionStatus("Notification access", listenerAccessGranted)
    OutlinedButton(
        modifier = Modifier.fillMaxWidth(),
        onClick = openNotificationAccessSettings,
    ) { Text("Open notification access settings") }
    PermissionStatus("Post notifications", postingAllowed)
    if (Build.VERSION.SDK_INT >= 33 && !postingAllowed) {
        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = requestPostingPermission,
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
internal fun GarminGuide() {
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
