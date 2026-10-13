package dev.wristrelay.app.model

/**
 * A normalized incoming message relayed to its own independent notification.
 * Instances exist only in memory for the duration of processing.
 */
data class RelayMessage(
    val conversationTitle: String?,
    val isGroup: Boolean,
    val sender: String?,
    val text: String?,
    val timestampMs: Long? = null,
)
