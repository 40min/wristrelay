package dev.wristrelay.app

import android.app.Notification
import dev.wristrelay.app.processing.SourceDismissalTracker
import dev.wristrelay.app.relay.RelayPublisher
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

    private data class PendingDismissal(val postTime: Long, val copies: List<Pair<String, Int>>)
    private var publicationFailed = false
    private val pendingDismissals = LinkedHashMap<String, PendingDismissal>()
    private val dismissals = SourceDismissalTracker()
    private var tracker: MessageTracker? = null
    private var publisher: RelayNotificationPublisher? = null
    private var settingsScope: CoroutineScope? = null

    override fun onListenerConnected() {
        pendingDismissals.clear()
        dismissals.clear()
        publicationFailed = false
        tracker = MessageTracker()
        publisher = RelayNotificationPublisher(this)
        // Load settings into the synchronous gate; activation re-baselines existing conversations.
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
        DebugRecorder.connect(packageName)
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

    override fun onNotificationPosted(sbn: StatusBarNotification) = record("POSTED", sbn)

    override fun onNotificationRemoved(
        sbn: StatusBarNotification,
        rankingMap: RankingMap,
        reason: Int,
    ) {
        if (reason == REASON_LISTENER_CANCEL && dismissals.removedByUs(sbn.key, sbn.postTime)) {
            tracker?.forgetDismissedSeries(sbn.key)
        }
        record("REMOVED", sbn)
    }

    private fun record(action: String, sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) {
            if (action == "POSTED") confirmPendingDismissals()
            return
        }
        DebugRecorder.record(action, sbn)
        if (action == "POSTED") handleRelayCandidate(sbn)
    }

    private fun handleRelayCandidate(sbn: StatusBarNotification) {
        if (!readyFor(sbn)) return
        val currentTracker = checkNotNull(tracker)
        val currentPublisher = checkNotNull(publisher)
        if (dismissals.posted(sbn.key, sbn.postTime)) currentTracker.forgetDismissedSeries(sbn.key)
        if (pendingDismissals[sbn.key]?.postTime != sbn.postTime) pendingDismissals.remove(sbn.key)
        val snapshot = TelegramExtractor.extract(sbn)
        val pipeline = TelegramRelayPipeline(currentTracker, RelayPublisher { messages ->
            currentPublisher.publish(messages, sbn.notification, sbn.packageName)
        })
        // 3.2 uses the same trace to accumulate processing-time evidence
        // from live use; the figure covers detection and publication.
        val started = System.nanoTime()
        when (val outcome = pipeline.run(sbn.key, snapshot)) {
            is RelayResult.Unextractable ->
                DebugRecorder.relayTrace(
                    sbn.key,
                    "extract=null (unsupported/missing messaging array)",
                    TelegramExtractor.TELEGRAM_PACKAGE,
                )
            is RelayResult.Published -> {
                queueDismissal(sbn, snapshot?.size, currentPublisher.lastPostedIdentities)
                DebugRecorder.relayTrace(
                    sbn.key,
                    "extracted=${snapshot?.size ?: 0} emitted=${outcome.count} published=${outcome.count}" +
                        " proc=${(System.nanoTime() - started) / 1_000_000}ms",
                    TelegramExtractor.TELEGRAM_PACKAGE,
                )
            }
            is RelayResult.Duplicate ->
                DebugRecorder.relayTrace(
                    sbn.key,
                    "extracted=${snapshot?.size ?: 0} emitted=0" +
                        " proc=${(System.nanoTime() - started) / 1_000_000}ms",
                    TelegramExtractor.TELEGRAM_PACKAGE,
                )
            is RelayResult.Failed -> {
                publicationFailed = true
                pendingDismissals.clear()
                DebugRecorder.relayTrace(
                    sbn.key,
                    outcome.reason,
                    TelegramExtractor.TELEGRAM_PACKAGE,
                )
            }
        }
    }

    private fun readyFor(sbn: StatusBarNotification): Boolean =
        SettingsGate.relayingAllowed() && tracker != null && publisher != null &&
            TelegramExtractor.matches(sbn.packageName) &&
            sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0

    private fun queueDismissal(sbn: StatusBarNotification, size: Int?, copies: List<Pair<String, Int>>) {
        val supported = copies.size <= 256 &&
            sbn.notification.category == Notification.CATEGORY_MESSAGE &&
            size == sbn.notification.extras.getParcelableArray(Notification.EXTRA_MESSAGES)?.size
        if (!publicationFailed && SettingsGate.current.hideTelegramOriginals && supported) {
            pendingDismissals[sbn.key] = PendingDismissal(sbn.postTime, copies)
            while (pendingDismissals.size > 16) pendingDismissals.remove(pendingDismissals.keys.first())
            confirmPendingDismissals()
        }
    }

    private fun confirmPendingDismissals() {
        if (publicationFailed || !SettingsGate.relayingAllowed() || !SettingsGate.current.hideTelegramOriginals) {
            pendingDismissals.clear()
            return
        }
        try {
            val active = activeNotifications ?: return
            val ready = pendingDismissals.filter { (key, pending) ->
                pending.copies.isNotEmpty() && pending.copies.all { (tag, id) ->
                    active.any { it.packageName == packageName && it.tag == tag && it.id == id }
                } && active.any { it.key == key && it.postTime == pending.postTime }
            }
            ready.forEach { (key, pending) ->
                pendingDismissals.remove(key)
                dismissals.requested(key, pending.postTime)
                try {
                    cancelNotification(key)
                } catch (_: SecurityException) {
                    dismissals.abandon(key)
                }
            }
        } catch (_: SecurityException) {
            // Access can be revoked between posting and dismissal. Keep successful copies.
            pendingDismissals.clear()
        }
    }

    private fun trackSeenBaseline(sbn: StatusBarNotification) {
        if (!TelegramExtractor.matches(sbn.packageName)) return
        val snapshot = TelegramExtractor.extract(sbn) ?: return
        tracker?.markBaseline(sbn.key, snapshot)
        DebugRecorder.baselineAbsorbed(sbn.key, snapshot.size, TelegramExtractor.TELEGRAM_PACKAGE)
    }
}
