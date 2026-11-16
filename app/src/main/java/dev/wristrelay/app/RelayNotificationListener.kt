package dev.wristrelay.app

import android.app.Notification
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.wristrelay.app.debug.DebugRecorder
import dev.wristrelay.app.debug.SpotifyProbe
import dev.wristrelay.app.debug.SpotifyProbeCoordinator
import dev.wristrelay.app.processing.TelegramRelaySession
import dev.wristrelay.app.relay.RelayNotificationPublisher
import dev.wristrelay.app.settings.RelaySettings
import dev.wristrelay.app.settings.SettingsGate
import dev.wristrelay.app.settings.SettingsStore
import dev.wristrelay.app.source.spotify.SPOTIFY_PACKAGE
import dev.wristrelay.app.source.spotify.SpotifyAdMute
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import java.io.IOException

/**
 * Relay listener: extracts MessagingStyle messages from Telegram snapshots
 * and publishes one independent notification per genuinely new message.
 * Filtering happens first; WristRelay's own notifications and unrelated
 * packages never enter the Telegram relay pipeline. Debug probes run separately.
 */
class RelayNotificationListener : NotificationListenerService() {

    private var telegram: TelegramRelaySession? = null
    private var settingsScope: CoroutineScope? = null

    internal var openSettings = { SettingsStore(applicationContext) }
    private var settingsAvailable = false
    private var spotifyMuteSettings = RelaySettings()
    private var spotifyAdMute: SpotifyAdMute? = null
    private val spotifyProbe by lazy { SpotifyProbeCoordinator(this) { activeNotifications } }

    override fun onListenerConnected() {
        settingsScope?.cancel()
        settingsAvailable = false
        spotifyMuteSettings = RelaySettings()
        spotifyAdMute?.stop()
        val telegramSession = TelegramRelaySession(
            packageName,
            RelayNotificationPublisher(this),
            relaySettings = { SettingsGate.current.takeIf { settingsAvailable && SettingsGate.relayingAllowed() } },
            activeNotifications = { activeNotifications },
            cancelNotification = ::cancelNotification,
        )
        telegram = telegramSession
        settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scope ->
            scope.launch {
                openSettings().flow.catch { failure ->
                    if (failure !is IOException) throw failure
                    settingsAvailable = false
                    telegramSession.settingsUnavailable()
                    spotifyAdMute?.stop()
                }.collect { settings ->
                    SettingsGate.update(settings)
                    telegramSession.settingsChanged(settings)
                    spotifyMuteSettings = settings
                    settingsAvailable = true
                    if (!settings.spotifyAdMuteEnabled) spotifyAdMute?.stop()
                }
            }
        }
        DebugRecorder.connect(packageName)
        if (BuildConfig.DEBUG) spotifyProbe.start(checkNotNull(settingsScope))
        // Baseline current state without relaying, so a listener reconnect or
        // process restart never replays stale conversations (VALIDATION.md).
        telegramSession.baselineActiveNotifications()
    }

    override fun onListenerDisconnected() {
        telegram = null
        settingsScope?.cancel()
        settingsAvailable = false
        spotifyMuteSettings = RelaySettings()
        spotifyAdMute?.stop()
        if (BuildConfig.DEBUG) {
            spotifyProbe.stop()
            SpotifyProbe.record("Notification listener disconnected")
        }
        super.onListenerDisconnected()
    }

    override fun onDestroy() {
        telegram = null
        spotifyAdMute?.stop()
        if (BuildConfig.DEBUG) spotifyProbe.stop()
        settingsScope?.cancel()
        settingsScope = null
        super.onDestroy()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = record("POSTED", sbn)

    override fun onNotificationRemoved(
        sbn: StatusBarNotification,
        rankingMap: RankingMap,
        reason: Int,
    ) {
        if (reason == REASON_LISTENER_CANCEL) telegram?.removedByListener(sbn)
        record("REMOVED", sbn)
    }

    private fun record(action: String, sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) {
            if (action == "POSTED") telegram?.confirmPendingDismissals()
            return
        }
        if (sbn.packageName == SPOTIFY_PACKAGE) {
            val posted = action == "POSTED"
            if (BuildConfig.DEBUG) spotifyProbe.notificationChanged(sbn, posted)
            if (posted) {
                val settings = spotifyMuteSettings
                val extras = sbn.notification.extras
                if (settingsAvailable && settings.spotifyAdMuteEnabled && extras.containsKey(Notification.EXTRA_MEDIA_SESSION)) {
                    val mute = spotifyAdMute ?: SpotifyAdMute(
                        Handler(Looper.getMainLooper()),
                        getSystemService(AudioManager::class.java),
                    ).also { spotifyAdMute = it }
                    mute.posted(sbn.key, extras.getCharSequence(Notification.EXTRA_TEXT), settings.spotifyAdMuteSeconds)
                }
            } else {
                spotifyAdMute?.removed(sbn.key)
            }
            DebugRecorder.record(action, sbn)
            return
        }
        DebugRecorder.record(action, sbn)
        if (action == "POSTED") telegram?.posted(sbn)
    }
}
