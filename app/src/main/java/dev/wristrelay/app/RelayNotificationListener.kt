package dev.wristrelay.app

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.wristrelay.app.debug.DebugRecorder
import dev.wristrelay.app.processing.MessageTracker
import dev.wristrelay.app.processing.RelayActivationWatcher
import dev.wristrelay.app.processing.RelayResult
import dev.wristrelay.app.processing.TelegramRelayPipeline
import dev.wristrelay.app.relay.RelayNotificationPublisher
import dev.wristrelay.app.settings.SettingsGate
import dev.wristrelay.app.settings.SettingsStore
import dev.wristrelay.app.source.telegram.TelegramExtractor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Relay listener: extracts MessagingStyle messages from Telegram snapshots
 * and publishes one independent notification per genuinely new message.
 * Filtering happens first; WristRelay's own notifications and unrelated
 * packages never proceed beyond this gate (the debug capture side is the
 * only other consumer, and it never touches message contents).
 */
class RelayNotificationListener : NotificationListenerService() {

    private var tracker: MessageTracker? = null
    private var publisher: RelayNotificationPublisher? = null
    private var settingsScope: CoroutineScope? = null

    override fun onListenerConnected() {
        tracker = MessageTracker()
        publisher = RelayNotificationPublisher(this)
        watchSettings()
        // Baseline current state without relaying, so a listener reconnect or
        // process restart never replays stale conversations (VALIDATION.md).
        activeNotifications?.forEach { sbn ->
            trackSeenBaseline(sbn)
        }
    }

    override fun onDestroy() {
        settingsScope?.cancel()
        settingsScope = null
        super.onDestroy()
    }

    /**
     * Settings are loaded from DataStore into the synchronous gate; enabling
     * the relay while conversations already exist re-baselines via the
     * activation callback so pre-existing entries are never replayed.
     */
    private fun watchSettings() {
        settingsScope?.cancel()
        val activationWatcher = RelayActivationWatcher {
            activeNotifications?.forEach { sbn ->
                trackSeenBaseline(sbn)
            }
        }
        settingsScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).also { scope ->
            SettingsStore(applicationContext).observe { settings ->
                SettingsGate.update(settings)
                activationWatcher.update(settings)
            }
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = record("POSTED", sbn)

    override fun onNotificationRemoved(sbn: StatusBarNotification) = record("REMOVED", sbn)

    private fun record(action: String, sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        DebugRecorder.record(action, sbn)
        if (action == "POSTED") handleRelayCandidate(sbn)
    }

    private fun handleRelayCandidate(sbn: StatusBarNotification) {
        val pipeline = relayPipeline() ?: return
        if (!TelegramExtractor.matches(sbn.packageName)) return
        val snapshot = TelegramExtractor.extract(sbn) ?: emptyList()
        when (val outcome = pipeline.run(sbn.key, snapshot)) {
            is RelayResult.Unextractable ->
                DebugRecorder.relayTrace(
                    sbn.key,
                    "extract=null (unsupported/missing messaging array)",
                    TelegramExtractor.TELEGRAM_PACKAGE,
                )
            is RelayResult.Published ->
                DebugRecorder.relayTrace(
                    sbn.key,
                    "extracted=${snapshot.size} emitted=${outcome.count} published=${outcome.count}",
                    TelegramExtractor.TELEGRAM_PACKAGE,
                )
            is RelayResult.Duplicate ->
                DebugRecorder.relayTrace(
                    sbn.key,
                    "extracted=${snapshot.size} emitted=0",
                    TelegramExtractor.TELEGRAM_PACKAGE,
                )
            is RelayResult.Failed ->
                DebugRecorder.relayTrace(
                    sbn.key,
                    outcome.reason,
                    TelegramExtractor.TELEGRAM_PACKAGE,
                )
        }
    }

    /** Source gate plus readiness check; null means do not touch this event. */
    private fun relayPipeline(): TelegramRelayPipeline? {
        val ready = SettingsGate.relayingAllowed() && tracker != null && publisher != null
        return when {
            ready -> TelegramRelayPipeline(tracker!!, publisher!!)
            else -> null
        }
    }

    private fun trackSeenBaseline(sbn: StatusBarNotification) {
        if (!TelegramExtractor.matches(sbn.packageName)) return
        val snapshot = TelegramExtractor.extract(sbn) ?: return
        tracker?.markBaseline(sbn.key, snapshot.size)
    }
}
