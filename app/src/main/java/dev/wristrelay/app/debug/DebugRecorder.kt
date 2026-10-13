package dev.wristrelay.app.debug

import android.os.Bundle
import android.service.notification.StatusBarNotification
import java.util.concurrent.atomic.AtomicLong

/**
 * Metadata-only capture for Task 0.1 device inspection; the caller owns
 * source filtering (WristRelay's own notifications never reach this record
 * path).
 *
 * Every record is sanitized on capture: keys, package/user identity, timing,
 * extra types and lengths. Notification text, titles and sender names are
 * never read into retained state; only lengths are kept. Retention is bounded
 * in memory (oldest entries are dropped first) and disappears when the
 * process dies.
 */
object DebugRecorder {

    private val sequence = AtomicLong(0)

    fun record(action: String, sbn: StatusBarNotification) {
        val extras: Bundle = sbn.notification?.extras ?: Bundle.EMPTY
        val summarized = extras.keySet().orEmpty()
            .sorted()
            .mapNotNull { key -> DebugFormat.summarize(key, extras.get(key)) }
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

    fun relayTrace(key: String?, trace: String, telegramPackage: String) {
        DebugEventStore.append(
            DebugEvent(
                seq = sequence.incrementAndGet(),
                wallClockMs = System.currentTimeMillis(),
                action = "RELAY",
                packageName = telegramPackage,
                userId = 0,
                key = key ?: "unknown",
                isOngoing = false,
                postTimeMs = -1L,
                extras = emptyList(),
                relayTrace = trace,
            ),
        )
    }
}
