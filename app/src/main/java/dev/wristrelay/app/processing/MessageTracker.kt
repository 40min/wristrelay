package dev.wristrelay.app.processing

import dev.wristrelay.app.model.RelayMessage

/**
 * Detects genuinely new messages by comparing the position of the deepest
 * message entry that has already been relayed per conversation against
 * successive snapshot sizes. This is the only feasible identity signal:
 * Telegram message entries expose no stable per-message ID (verified 0.1),
 * and text alone cannot distinguish messages (ADR-004).
 *
 * State is a bounded map of conversation key to last relayed count. Only
 * counts are retained; message contents are never stored.
 */
class MessageTracker(private val maxTrackedConversations: Int = 16) {

    private val lastRelayedCount = LinkedHashMap<String, Int>(maxTrackedConversations)

    /**
     * Emits the messages beyond the last already-relayed count:
     * cumulative snapshots (ONE; ONE/TWO; ONE/TWO/THREE) emit exactly the
     * new suffix each time; a repeated snapshot emits nothing; two identical
     * texts are distinct positional entries and both get emitted.
     *
     * Growth beyond the retained cursor emits the missing slice even if it
     * spans more than one position (e.g. Telegram delivers several updates
     * while the listener was busy) to avoid losing arrivals.
     */
    fun newMessages(conversationKey: String, snapshot: List<RelayMessage>): List<RelayMessage> {
        if (snapshot.isEmpty()) {
            // No message entries: e.g. summary-style notification. Not evidence
            // that entries were read, so the cursor is left untouched.
            return emptyList()
        }
        val seen = lastRelayedCount[conversationKey] ?: 0
        if (snapshot.size <= seen) {
            // Sources may shrink on dismissal or read. Treat as a read: reset
            // the cursor to the current size; nothing older is re-emitted.
            if (snapshot.size < seen) remember(conversationKey, snapshot.size)
            return emptyList()
        }
        val fresh = snapshot.subList(seen, snapshot.size)
        remember(conversationKey, snapshot.size)
        return fresh.toList()
    }

    /**
     * Marks a conversation's current snapshot as already relayed without
     * emitting anything (startup baseline / reconnection policy prevents a
     * stale replay storm after process restart or listener reconnect).
     */
    fun markBaseline(conversationKey: String, snapshotSize: Int) {
        if (snapshotSize > 0) remember(conversationKey, snapshotSize)
    }

    private fun remember(conversationKey: String, size: Int) {
        lastRelayedCount.remove(conversationKey)
        lastRelayedCount[conversationKey] = size
        while (lastRelayedCount.size > maxTrackedConversations) {
            val eldest = lastRelayedCount.keys.firstOrNull() ?: break
            lastRelayedCount.remove(eldest)
        }
    }
}
