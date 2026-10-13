package dev.wristrelay.app.debug

/**
 * Metadata-only capture for Task 0.1 device inspection.
 *
 * Every record is sanitized on capture: keys, package/user identity, timing,
 * extra types and lengths. Notification text, titles and sender names are
 * never read into retained state; only lengths are kept. Retention is bounded
 * in memory (oldest entries are dropped first) and disappears when the
 * process dies.
 */
data class DebugEvent(
    val seq: Long,
    val wallClockMs: Long,
    val action: String,
    val packageName: String,
    val userId: Int,
    val key: String,
    val isOngoing: Boolean,
    val postTimeMs: Long,
    val extras: List<ExtraSummary>,
    val relayTrace: String? = null,
)

/** Key plus sanitized shape of one notification extra. */
data class ExtraSummary(
    val key: String,
    val kind: String,
    val length: Int?,
)

enum class ExtraKind(internal val label: String) {
    STRING("String"),
    CHARSEQ("CharSequence"),
    PARCELABLE_ARRAY("Parcelable[]"),
    LONG("Long"),
    BOOLEAN("Boolean"),
    OTHER("Other"),
}

object DebugEventStore {
    const val MAX_EVENTS = 50

    private val events = ArrayDeque<DebugEvent>(MAX_EVENTS)
    private var observer: (() -> Unit)? = null

    fun append(event: DebugEvent) {
        synchronized(events) {
            // Bounded history: drop the oldest entry before adding a new one.
            if (events.size >= MAX_EVENTS) events.removeFirst()
            events.addLast(event)
        }
        observer?.invoke()
    }

    fun snapshot(): List<DebugEvent> = synchronized(events) { events.toList() }

    fun clear() {
        synchronized(events) { events.clear() }
        observer?.invoke()
    }

    fun observe(callback: () -> Unit) {
        observer = callback
    }

    fun stopObserving() {
        observer = null
    }
}

/** Pure formatting helpers; no Android dependencies. */
object DebugFormat {

    /** Maps an extra to its sanitized descriptor: key, type name and length. */
    fun summarizeExtra(key: String, kind: ExtraKind, length: Int?): ExtraSummary =
        ExtraSummary(key = key, kind = kind.label, length = length)

    fun describeTimestamps(posted: Long, captureMs: Long): String {
        val delay = captureMs - posted
        val postUtc = java.time.Instant.ofEpochMilli(posted)
            .atZone(java.time.ZoneOffset.UTC)
        val shown = "%02d:%02d:%02d.%03d".format(
            postUtc.hour, postUtc.minute, postUtc.second, postUtc.nano / 1_000_000,
        )
        return "postTimeMillis=$posted ($shown UTC, capture ${
            if (delay < 0) "${-delay}ms before postTime (clock skew)" else "+${delay}ms"
        })"
    }

    fun format(event: DebugEvent): List<String> = buildList {
        add("${event.action} pkg=${event.packageName} user=${event.userId} ongoing=${event.isOngoing}")
        add("key=${event.key}")
        if (event.relayTrace != null) {
            add("relayTrace=${event.relayTrace}")
            return@buildList
        }
        add(describeTimestamps(event.postTimeMs, event.wallClockMs))
        if (event.extras.isEmpty()) {
            add("extras=none")
        } else {
            event.extras.forEach { extra ->
                add(
                    "extra ${extra.key}: ${extra.kind}" +
                        (extra.length?.let { len -> " len=$len" } ?: ""),
                )
            }
        }
    }
}
