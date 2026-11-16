package dev.wristrelay.app.debug

import android.os.Bundle
import android.service.notification.StatusBarNotification

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

    private var sequence = 0L

    /** Trace-only marker that the listener service connected (re)binding. */
    fun connect(ownPackage: String) {
        DebugEventStore.append {
            DebugEvent(
                seq = ++sequence,
                wallClockMs = System.currentTimeMillis(),
                action = "CONNECT",
                packageName = ownPackage,
                userId = 0,
                key = "self",
                isOngoing = false,
                postTimeMs = -1L,
                extras = emptyList(),
                relayTrace = "listener connected; pre-existing notifications get baselined",
            )
        }
    }

    /** Trace-only marker that a notification snapshot was absorbed as baseline. */
    fun baselineAbsorbed(key: String, snapshotSize: Int, telegramPackage: String) {
        DebugEventStore.append {
            DebugEvent(
                seq = ++sequence,
                wallClockMs = System.currentTimeMillis(),
                action = "BASELINE",
                packageName = telegramPackage,
                userId = 0,
                key = key,
                isOngoing = false,
                postTimeMs = -1L,
                extras = emptyList(),
                relayTrace = "non-relay absorber: cursor set to size=$snapshotSize",
            )
        }
    }

    fun record(action: String, sbn: StatusBarNotification) {
        DebugEventStore.append {
            val extras: Bundle = sbn.notification?.extras ?: Bundle.EMPTY
            val summarized = extras.keySet().orEmpty()
                .sorted()
                .mapNotNull { key -> DebugFormat.summarize(key, extras.get(key)) }
                .map { (key, kind, length) -> DebugFormat.summarizeExtra(key, kind, length) }

            DebugEvent(
                seq = ++sequence,
                wallClockMs = System.currentTimeMillis(),
                action = action,
                packageName = sbn.packageName,
                userId = sbn.userId,
                key = sbn.key,
                isOngoing = sbn.isOngoing,
                postTimeMs = sbn.postTime,
                extras = summarized,
            )
        }
    }

    fun relayTrace(key: String?, trace: String, telegramPackage: String) {
        DebugEventStore.append {
            DebugEvent(
                seq = ++sequence,
                wallClockMs = System.currentTimeMillis(),
                action = "RELAY",
                packageName = telegramPackage,
                userId = 0,
                key = key ?: "unknown",
                isOngoing = false,
                postTimeMs = -1L,
                extras = emptyList(),
                relayTrace = trace,
            )
        }
    }
}
