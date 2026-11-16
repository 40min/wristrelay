package dev.wristrelay.app.processing

import android.app.Notification
import android.service.notification.StatusBarNotification
import dev.wristrelay.app.debug.DebugRecorder
import dev.wristrelay.app.relay.RelayNotificationPublisher
import dev.wristrelay.app.relay.RelayPublisher
import dev.wristrelay.app.settings.RelaySettings
import dev.wristrelay.app.source.telegram.TelegramExtractor

/** Telegram relay and original-dismissal state for one listener connection. */
internal class TelegramRelaySession(
    private val packageName: String,
    private val publisher: RelayNotificationPublisher,
    private val relaySettings: () -> RelaySettings?,
    private val activeNotifications: () -> Array<StatusBarNotification>?,
    private val cancelNotification: (String) -> Unit,
) {
    private data class PendingDismissal(val postTime: Long, val copies: List<Pair<String, Int>>)

    private val tracker = MessageTracker()
    private val dismissals = SourceDismissalTracker()
    private val pendingDismissals = LinkedHashMap<String, PendingDismissal>()
    private var publicationFailed = false
    private val activationWatcher = RelayActivationWatcher(::baselineActiveNotifications)

    fun baselineActiveNotifications() {
        activeNotifications()?.forEach(::trackSeenBaseline)
    }

    fun settingsChanged(settings: RelaySettings) = activationWatcher.update(settings)

    fun settingsUnavailable() = pendingDismissals.clear()

    fun removedByListener(sbn: StatusBarNotification) {
        if (dismissals.removedByUs(sbn.key, sbn.postTime)) tracker.forgetDismissedSeries(sbn.key)
    }

    fun posted(sbn: StatusBarNotification) {
        val settings = relaySettings() ?: return
        if (!TelegramExtractor.matches(sbn.packageName) ||
            sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0
        ) return
        if (dismissals.posted(sbn.key, sbn.postTime)) tracker.forgetDismissedSeries(sbn.key)
        if (pendingDismissals[sbn.key]?.postTime != sbn.postTime) pendingDismissals.remove(sbn.key)
        val snapshot = TelegramExtractor.extract(sbn)
        val pipeline = TelegramRelayPipeline(tracker, RelayPublisher { messages ->
            publisher.publish(messages, sbn.notification, sbn.packageName, settings)
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
                queueDismissal(sbn, snapshot?.size, outcome.identities, settings.hideTelegramOriginals)
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
                // A failed batch may have posted some copies, so suspend original hiding until reconnect (ADR-014).
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

    private fun queueDismissal(
        sbn: StatusBarNotification,
        size: Int?,
        copies: List<Pair<String, Int>>,
        hideOriginals: Boolean,
    ) {
        val supported = copies.size <= 256 &&
            sbn.notification.category == Notification.CATEGORY_MESSAGE &&
            size == sbn.notification.extras.getParcelableArray(Notification.EXTRA_MESSAGES)?.size
        if (!publicationFailed && hideOriginals && supported) {
            pendingDismissals[sbn.key] = PendingDismissal(sbn.postTime, copies)
            while (pendingDismissals.size > 16) pendingDismissals.remove(pendingDismissals.keys.first())
            confirmPendingDismissals()
        }
    }

    fun confirmPendingDismissals() {
        if (publicationFailed || relaySettings()?.hideTelegramOriginals != true) {
            pendingDismissals.clear()
            return
        }
        try {
            val active = activeNotifications() ?: return
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
        tracker.markBaseline(sbn.key, snapshot)
        DebugRecorder.baselineAbsorbed(sbn.key, snapshot.size, TelegramExtractor.TELEGRAM_PACKAGE)
    }
}
