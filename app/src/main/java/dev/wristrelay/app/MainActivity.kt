package dev.wristrelay.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.app.NotificationManagerCompat
import dev.wristrelay.app.debug.DebugPanel
import dev.wristrelay.app.ui.WristRelayScreen

class MainActivity : ComponentActivity() {
    private val postingAllowed = mutableStateOf(false)
    private val listenerAccessGranted = mutableStateOf(false)
    private val debugPanel by lazy { DebugPanel(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshPermission()
        setContent {
            val permissionRequest = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { refreshPermission() }
            WristRelayScreen(
                listenerAccessGranted = listenerAccessGranted.value,
                postingAllowed = postingAllowed.value,
                openNotificationAccessSettings = {
                    startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                },
                requestPostingPermission = {
                    permissionRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                },
                debugContent = { if (BuildConfig.DEBUG) debugPanel.Content() },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermission()
        if (BuildConfig.DEBUG) debugPanel.refreshCapture()
    }

    private fun refreshPermission() {
        listenerAccessGranted.value = NotificationManagerCompat.getEnabledListenerPackages(this)
            .contains(packageName)
        postingAllowed.value = getSystemService(android.app.NotificationManager::class.java)
            .areNotificationsEnabled() && (Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    }
}
