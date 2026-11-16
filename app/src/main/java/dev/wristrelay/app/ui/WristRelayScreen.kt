package dev.wristrelay.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.wristrelay.app.settings.RelaySettings
import dev.wristrelay.app.settings.SettingsGate
import dev.wristrelay.app.settings.SettingsStore
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

@Composable
internal fun WristRelayScreen(
    listenerAccessGranted: Boolean,
    postingAllowed: Boolean,
    openNotificationAccessSettings: () -> Unit,
    requestPostingPermission: () -> Unit,
    debugContent: @Composable () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val store = remember(context) { SettingsStore(context) }
    WristRelayTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
                    .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("WristRelay", style = MaterialTheme.typography.headlineLarge)
                RelaySettingsContent(store)
                SettingsSection("Advanced") {
                    PermissionControls(
                        listenerAccessGranted,
                        postingAllowed,
                        openNotificationAccessSettings,
                        requestPostingPermission,
                    )
                    GarminGuide()
                    debugContent()
                }
            }
        }
    }
}

@Composable
internal fun RelaySettingsContent(store: SettingsStore) {
    var loadedSettings by remember { mutableStateOf<RelaySettings?>(null) }
    var readFailed by remember { mutableStateOf(false) }
    var saveFailed by remember { mutableStateOf(false) }
    LaunchedEffect(store) {
        store.flow.catch { failure ->
            if (failure !is IOException) throw failure
            readFailed = true
            loadedSettings = null
        }.collect { loadedSettings = it }
    }
    val scope = rememberCoroutineScope()
    var saving by remember { mutableStateOf(false) }
    val settings = loadedSettings
    if (settings == null) {
        if (readFailed) {
            Text("Couldn’t load saved settings.", color = MaterialTheme.colorScheme.error)
        } else {
            Text("Loading saved settings…", style = MaterialTheme.typography.bodySmall)
        }
        return
    }
    if (saveFailed) {
        Text("Couldn’t save the change. Try again.", color = MaterialTheme.colorScheme.error)
    }
    RelaySettingsScreen(settings, enabled = !saving) { edit ->
        if (!saving) {
            saving = true
            saveFailed = false
            scope.launch {
                try {
                    val updated = edit(store.current())
                    store.save(updated)
                    SettingsGate.update(updated)
                } catch (_: IOException) {
                    saveFailed = true
                } finally {
                    saving = false
                }
            }
        }
    }
}
