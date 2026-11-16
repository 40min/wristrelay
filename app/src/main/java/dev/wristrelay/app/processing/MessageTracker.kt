package dev.wristrelay.app.processing

import dev.wristrelay.app.model.RelayMessage

/**
 * Detects genuinely new messages against a per-conversation cursor of the
 * deepest snapshot extent already relayed. This is the primary identity
 * signal: Telegram message entries expose no stable per-message ID
 * (verified 0.1), and text alone cannot distinguish messages (ADR-004).
 *
 * State is a bounded map of conversation key to cursor (count of already
 * relayed entries + timestamp of the newest relayed message). Only positions
 * and timestamps are retained, never contents.
 *
 * Growth (snapshot larger than the cursor) emits the new positional slice.
 *
 * Shrink/repeat (snapshot equal or smaller) is where Telegram rebuilds its
 * cumulative array from scratch (mid-session resets verified on device;
 * owner repro 2026-10-13, docs/VALIDATION.md): the rebuilt array can hold a
 * genuinely NEW unread message while the cursor still counts the old
 * series, and canonical "shrink = read" would swallow it. Here the entries
 * are classified by per-message timestamp instead: an entry strictly newer
 * than everything already relayed is emitted; older-or-equal entries are
 * unread leftovers of already-relayed history and collapse silently.
 *
 * Publication-loss guard (Cf7Lga4n2Sx7): detection does not advance the
 * cursor by itself. Empty detections collapse into the cursor in place
 * (read evidence; no copies are at risk), but a nonempty fresh slice
 * advances the cursor only through the prefix the publisher reports as
 * successfully submitted ([advanceThroughSubmitted]). A full publication
 * failure therefore retries the same snapshot on the next qualifying
 * event, and a partial failure retries only the missing copies.
 */
class MessageTracker(private val maxTrackedConversations: Int = 16) {

    private data class Rebuild(val size: Int, val cutoff: Long)

    private data class Retry(val offset: Int, val timestamps: List<Long?>, val rebuild: Rebuild?)

    private data class Cursor(
        val count: Int,
        val lastTimestampMs: Long?,
        val retry: Retry? = null,
    )

    private val cursors = LinkedHashMap<String, Cursor>()

    fun newMessages(conversationKey: String, snapshot: List<RelayMessage>): List<RelayMessage> {
        if (snapshot.isEmpty()) return emptyList()
        val cursor = cursors[conversationKey] ?: Cursor(0, null)
        val (start, rebuild) = detectionRange(cursor, snapshot)
        val positions = freshPositions(snapshot, start, rebuild)
        if (positions.isEmpty()) remember(conversationKey, snapshot.size, cursor.lastTimestampMs)
        return positions.map(snapshot::get)
    }

    /**
     * Advance past submitted entries in array order. During a rebuild retry,
     * keep the original timestamp cutoff for the unprocessed portion of that
     * array. Appended entries use their positions, as in ordinary growth.
     */
    fun advanceThroughSubmitted(
        conversationKey: String,
        snapshot: List<RelayMessage>,
        fresh: List<RelayMessage>,
        submittedCount: Int,
    ) {
        if (fresh.isEmpty()) return
        val cursor = cursors[conversationKey] ?: Cursor(0, null)
        val (start, rebuild) = detectionRange(cursor, snapshot)
        val positions = freshPositions(snapshot, start, rebuild)
        val submitted = submittedCount.coerceIn(0, positions.size)
        val nextPosition = positions.getOrNull(submitted) ?: snapshot.size
        val retainedFrom = (snapshot.size - MAX_RETRY_TIMESTAMPS).coerceAtLeast(0)
        val retry = if (nextPosition < snapshot.size) Retry(
            retainedFrom,
            snapshot.subList(retainedFrom, snapshot.size).map { it.timestampMs },
            rebuild?.takeIf { nextPosition < it.size },
        ) else null
        remember(
            conversationKey, nextPosition,
            maxTimestamp(cursor.lastTimestampMs, fresh.take(submitted)),
            retry,
        )
    }

    private fun detectionRange(cursor: Cursor, snapshot: List<RelayMessage>): Pair<Int, Rebuild?> {
        val retry = cursor.retry
        return when {
            retry != null -> retryRange(cursor, retry, snapshot)
            snapshot.size > cursor.count -> cursor.count to null
            else -> 0 to Rebuild(snapshot.size, cursor.lastTimestampMs ?: Long.MAX_VALUE)
        }
    }

    private fun retryRange(
        cursor: Cursor,
        retry: Retry,
        snapshot: List<RelayMessage>,
    ): Pair<Int, Rebuild?> {
        // The retained suffix can start inside the new array after a small shift,
        // or straddle its left edge after a larger one.
        val retainedEnd = retry.offset + retry.timestamps.size
        for (dropped in (retainedEnd - snapshot.size).coerceAtLeast(0) until retainedEnd) {
            val oldStart = (dropped - retry.offset).coerceAtLeast(0)
            val newStart = (retry.offset - dropped).coerceAtLeast(0)
            val overlap = retry.timestamps.size - oldStart
            if ((0 until overlap).any {
                    retry.timestamps[oldStart + it] != snapshot[newStart + it].timestampMs
                }
            ) continue
            val start = (cursor.count - dropped).coerceAtLeast(0)
            val rebuild = retry.rebuild?.takeIf { it.size > dropped }
                ?.let { Rebuild(it.size - dropped, it.cutoff) }
            return start to rebuild
        }
        return 0 to cursor.lastTimestampMs?.let { Rebuild(snapshot.size, it) }
    }

    private fun freshPositions(snapshot: List<RelayMessage>, start: Int, rebuild: Rebuild?): List<Int> =
        (start until snapshot.size).filter { position ->
            rebuild == null || position >= rebuild.size ||
                snapshot[position].timestampMs?.let { it > rebuild.cutoff } == true
        }

    /** Only our confirmed source dismissal establishes a new snapshot series. */
    fun forgetDismissedSeries(conversationKey: String) {
        cursors.remove(conversationKey)
    }

    /**
     * Marks a conversation's current snapshot as already relayed without
     * emitting anything (startup baseline / reconnection policy prevents a
     * stale replay storm after process restart or listener reconnect).
     */
    fun markBaseline(conversationKey: String, snapshot: List<RelayMessage>) {
        if (snapshot.isNotEmpty()) {
            remember(conversationKey, snapshot.size, maxTimestamp(null, snapshot))
        }
    }

    private fun remember(
        conversationKey: String,
        size: Int,
        lastTimestampMs: Long?,
        retry: Retry? = null,
    ) {
        cursors.remove(conversationKey)
        cursors[conversationKey] = Cursor(size, lastTimestampMs, retry)
        while (cursors.size > maxTrackedConversations) {
            val eldest = cursors.keys.firstOrNull() ?: break
            cursors.remove(eldest)
        }
    }

    private companion object {
        const val MAX_RETRY_TIMESTAMPS = 64
    }

    private fun maxTimestamp(current: Long?, messages: List<RelayMessage>): Long? =
        messages.fold(current) { latest, message ->
            val timestamp = message.timestampMs
            if (timestamp != null && (latest == null || timestamp > latest)) timestamp else latest
        }
}
