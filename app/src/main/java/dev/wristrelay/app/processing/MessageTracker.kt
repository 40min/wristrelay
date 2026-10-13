package dev.wristrelay.app.processing

import dev.wristrelay.app.model.RelayMessage

/**
 * Detects genuinely new messages against a per-conversation cursor of the
 * deepest snapshot extent already relayed. This is the primary identity
 * signal: Telegram message entries expose no stable per-message ID
 * (verified 0.1), and text alone cannot distinguish messages (ADR-004).
 *
 * State is a bounded map of conversation key to cursor (count of already
 * relayed entries + timestamp of the newest relayed message). Only counts
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
 */
class MessageTracker(private val maxTrackedConversations: Int = 16) {

    private data class Cursor(val count: Int, val lastTimestampMs: Long?)

    private val cursors = LinkedHashMap<String, Cursor>()

    fun newMessages(conversationKey: String, snapshot: List<RelayMessage>): List<RelayMessage> = when {
        // No message entries: e.g. summary-style notification. Not evidence
        // that entries were read, so the cursor is left untouched.
        snapshot.isEmpty() -> emptyList()
        snapshot.size <= count(conversationKey) -> shrunkOrRepeated(conversationKey, snapshot)
        else -> grew(conversationKey, snapshot)
    }

    private fun count(conversationKey: String): Int = cursors[conversationKey]?.count ?: 0

    private fun grew(conversationKey: String, snapshot: List<RelayMessage>): List<RelayMessage> {
        val cursor = cursors[conversationKey] ?: Cursor(0, null)
        val fresh = snapshot.subList(cursor.count, snapshot.size)
        remember(conversationKey, snapshot.size, maxTimestamp(cursor.lastTimestampMs, fresh))
        return fresh.toList()
    }

    private fun shrunkOrRepeated(conversationKey: String, snapshot: List<RelayMessage>): List<RelayMessage> {
        val cursor = cursors[conversationKey] ?: Cursor(0, null)
        val latestRelayed = cursor.lastTimestampMs
        val genuinelyNew = snapshot.filter { message ->
            latestRelayed != null && message.timestampMs != null && message.timestampMs > latestRelayed
        }
        if (genuinelyNew.isEmpty()) {
            // Unread leftovers / repeated snapshot: already relayed history.
            // The cursor follows the smaller snapshot; timestamps are kept as
            // the previous watermark, entries cannot be newer than it.
            remember(conversationKey, snapshot.size, cursor.lastTimestampMs)
            return emptyList()
        }
        // Telegram rebuilt the array and it contains messages that were never
        // relayed: emit exactly those, positionally newer history collapses.
        remember(conversationKey, snapshot.size, maxTimestamp(latestRelayed, genuinelyNew))
        return genuinelyNew
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
    ) {
        cursors.remove(conversationKey)
        cursors[conversationKey] = Cursor(size, lastTimestampMs)
        while (cursors.size > maxTrackedConversations) {
            val eldest = cursors.keys.firstOrNull() ?: break
            cursors.remove(eldest)
        }
    }

    private fun maxTimestamp(current: Long?, messages: List<RelayMessage>): Long? =
        messages.mapNotNull { it.timestampMs }.maxOrNull()?.let { newest ->
            if (current != null && current > newest) current else newest
        } ?: current
}
