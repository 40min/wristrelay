package dev.wristrelay.app

import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.wristrelay.app.debug.DebugEvent
import dev.wristrelay.app.debug.DebugEventStore
import dev.wristrelay.app.debug.DebugFormat
import dev.wristrelay.app.debug.ExtraKind
import dev.wristrelay.app.processing.MessageTracker
import dev.wristrelay.app.relay.RelayNotificationPublisher
import dev.wristrelay.app.settings.PrototypeRelaySwitch
import dev.wristrelay.app.source.telegram.TelegramExtractor
import java.util.concurrent.atomic.AtomicLong

/**
 * Debug capture (metadata-only, bounded; never reads message contents) plus
 * the task 0.2 prototype relay: extracts MessagingStyle messages from
 * Telegram snapshots and publishes one independent notification per genuinely
 * new message. Filtering happens first and unrelated packages never proceed
 * beyond the gate. WristRelay's own notifications are always excluded.
 */
class RelayNotificationListener : NotificationListenerService() {

    private val debugSequence = AtomicLong(0)
    private var tracker: MessageTracker? = null
    private var publisher: RelayNotificationPublisher? = null

    override fun onListenerConnected() {
        tracker = MessageTracker()
        publisher = RelayNotificationPublisher(this)
        // Baseline current state without relaying, so a listener reconnect or
        // process restart never replays stale conversations (VALIDATION.md).
        activeNotifications?.forEach { sbn ->
            trackSeenBaseline(sbn)
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) = record("POSTED", sbn)

    override fun onNotificationRemoved(sbn: StatusBarNotification) = record("REMOVED", sbn)

    private fun record(action: String, sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        recordDebugCapture(action, sbn)
        if (action == "POSTED") handleRelayCandidate(sbn)
    }

    private fun recordDebugCapture(action: String, sbn: StatusBarNotification) {
        val extras: Bundle = sbn.notification?.extras ?: Bundle.EMPTY
        val summarized = extras.keySet().orEmpty()
            .sorted()
            .mapNotNull { key -> summarize(key, extras.get(key)) }
            .map { (key, kind, length) -> DebugFormat.summarizeExtra(key, kind, length) }

        DebugEventStore.append(
            DebugEvent(
                seq = debugSequence.incrementAndGet(),
                wallClockMs = System.currentTimeMillis(),
                action = action,
                packageName = sbn.packageName,
                userId = sbn.userId,
                key = sbn.key,
                isOngoing = sbn.isOngoing,
                postTimeMs = sbn.postTime,
                extras = summarized,
            ),
        )
    }

    private fun handleRelayCandidate(sbn: StatusBarNotification) {
        if (!TelegramExtractor.matches(sbn.packageName)) return
        if (!PrototypeRelaySwitch.enabled) return
        val tracker = tracker ?: return
        val publisher = publisher ?: return

        val key = sbn.key
        val snapshot = TelegramExtractor.extract(sbn)
        if (snapshot == null) {
            debugTrace(key, "extract=null (unsupported/missing messaging array)")
            return
        }
        val fresh = try {
            tracker.newMessages(key, snapshot)
        } catch (t: Throwable) {
            debugTrace(key, "tracker failed: ${t.javaClass.simpleName}")
            return
        }
        if (fresh.isEmpty()) {
            debugTrace(key, "extracted=${snapshot.size} emitted=0")
            return
        }
        try {
            publisher.publish(fresh)
            debugTrace(key, "extracted=${snapshot.size} emitted=${fresh.size} published=${fresh.size}")
        } catch (t: Throwable) {
            debugTrace(key, "publish failed after emitting ${fresh.size}: ${t.javaClass.simpleName}")
        }
    }

    private fun debugTrace(key: String?, trace: String) {
        DebugEventStore.append(
            DebugEvent(
                seq = debugSequence.incrementAndGet(),
                wallClockMs = System.currentTimeMillis(),
                action = "RELAY",
                packageName = TelegramExtractor.TELEGRAM_PACKAGE,
                userId = 0,
                key = key ?: "unknown",
                isOngoing = false,
                postTimeMs = -1L,
                extras = emptyList(),
                relayTrace = trace,
            ),
        )
    }

    private fun trackSeenBaseline(sbn: StatusBarNotification) {
        if (!TelegramExtractor.matches(sbn.packageName)) return
        val snapshot = TelegramExtractor.extract(sbn) ?: return
        tracker?.markBaseline(sbn.key, snapshot.size)
    }

    /** Returns (key, kind, length) or null when the value cannot be described. */
    private fun summarize(key: String, value: Any?): Triple<String, ExtraKind, Int?>? {
        if (value == null) return null
        return when (value) {
            is String -> Triple(key, ExtraKind.STRING, value.length)
            is CharSequence -> Triple(key, ExtraKind.CHARSEQ, value.length)
            is Array<*> -> Triple(key, ExtraKind.PARCELABLE_ARRAY, value.size)
            is Long, is Int -> Triple(key, ExtraKind.LONG, value.toString().length)
            is Boolean -> Triple(key, ExtraKind.BOOLEAN, null)
            else -> Triple(key, ExtraKind.OTHER, null)
        }
    }
}
