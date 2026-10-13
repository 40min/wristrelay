package dev.wristrelay.app

import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import dev.wristrelay.app.debug.DebugEvent
import dev.wristrelay.app.debug.DebugEventStore
import dev.wristrelay.app.debug.DebugFormat
import dev.wristrelay.app.debug.ExtraKind
import java.util.concurrent.atomic.AtomicLong

/**
 * Task 0.1 fixture collector: records sanitized, metadata-only snapshots of
 * external notifications (WristRelay's own are ignored) while the process
 * runs. Notification and extra values are never read — only extra keys,
 * discriminated types and string/array lengths — so no message content can
 * leak into retained state. Retention is bounded in memory and cleared when
 * the process dies.
 *
 * The production filtering/extraction pipeline is a later task; this listener
 * performs no parsing, publication or cleanup on captured notifications.
 */
class RelayNotificationListener : NotificationListenerService() {

    override fun onNotificationPosted(sbn: StatusBarNotification) = record("POSTED", sbn)

    override fun onNotificationRemoved(sbn: StatusBarNotification) = record("REMOVED", sbn)

    private fun record(action: String, sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return

        val extras: Bundle = sbn.notification?.extras ?: Bundle.EMPTY
        val summarized = extras.keySet().orEmpty()
            .sorted()
            .mapNotNull { key -> summarize(key, extras.get(key)) }
            .map { (key, kind, length) -> DebugFormat.summarizeExtra(key, kind, length) }

        DebugEventStore.append(
            DebugEvent(
                seq = sequence.incrementAndGet(),
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

    private val sequence = AtomicLong(0)
}
