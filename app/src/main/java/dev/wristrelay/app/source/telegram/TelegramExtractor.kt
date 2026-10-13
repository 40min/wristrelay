package dev.wristrelay.app.source.telegram

import android.app.Notification
import android.app.Person
import android.os.Bundle
import android.service.notification.StatusBarNotification
import dev.wristrelay.app.model.RelayMessage

/**
 * Extracts MessagingStyle messages from Telegram notification snapshots.
 *
 * Structure verified on device (docs/VALIDATION.md, 2026-10-13):
 * Telegram (`org.telegram.messenger`) posts ONE notification per chat and
 * updates it in place; each new message appends a Bundle entry to the
 * cumulative `EXTRA_MESSAGES` array. Message entries carry no stable message
 * identity key, so messages are identified positionally by the detector.
 *
 * Variant handling (fixture-tested; limitations in VALIDATION/ARCHITECTURE):
 * - Group conversations carry EXTRA_IS_GROUP_CONVERSATION and per-message
 *   sender persons; private chats may omit senders (null is tolerated).
 * - Message entries with blank/no text (media-only, redacted or stripped
 *   payloads) are skipped rather than relayed as empty notifications.
 * - Per-message timestamps are taken as delivered; the array order is
 *   authoritative and timestamps never re-sort messages, so out-of-order
 *   clock values cannot change positional identity.
 * - Whole-conversation summary notifications carry no EXTRA_MESSAGES array;
 *   they describe chat state, not a message, and remain unsupported here —
 *   the caller decides how to treat the null (no emit).
 * - Unrecognized snapshot shapes yield null and are skipped rather than
 *   guessed at.
 */
object TelegramExtractor {

    const val TELEGRAM_PACKAGE = "org.telegram.messenger"

    fun matches(packageName: String): Boolean = packageName == TELEGRAM_PACKAGE

    /**
     * Returns the message list of this snapshot, or null when the shape is
     * unsupported (no messaging array, unexpected element type).
     */
    fun extract(sbn: StatusBarNotification): List<RelayMessage>? {
        val extras: Bundle = sbn.notification?.extras ?: return null
        return extractBundle(extras)
    }

    /**
     * Bundle-level extraction entry point; identical shapes arrive both from
     * the listener and from synthetic fixtures.
     */
    fun extractBundle(chat: Bundle): List<RelayMessage>? {
        val conversationTitle = chat.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val messages = chat.getParcelableArray(Notification.EXTRA_MESSAGES) ?: return null

        val isGroup = chat.getBoolean(EXTRA_IS_GROUP_CONVERSATION)
        val extracted = messages.mapNotNull { entry ->
            if (entry !is Bundle) return@mapNotNull null
            toRelayMessage(entry, conversationTitle, isGroup)
        }
        return extracted.takeIf { it.isNotEmpty() }
    }

    private fun toRelayMessage(
        messageBundle: Bundle,
        conversationTitle: String?,
        isGroup: Boolean,
    ): RelayMessage? {
        val text = messageBundle.getCharSequence(KEY_MESSAGE_TEXT)?.toString()
        if (text.isNullOrBlank()) return null
        val sender = messageBundle.getParcelable<Person>(KEY_MESSAGE_SENDER_PERSON)?.name?.toString()
            ?: messageBundle.getString(KEY_MESSAGE_SENDER_NAME)
        val timestamp = when (val delivered = messageBundle.get(KEY_MESSAGE_TIME)) {
            is Long -> delivered
            is Int -> delivered.toLong()
            else -> null
        }
        return RelayMessage(
            conversationTitle = conversationTitle,
            isGroup = isGroup,
            sender = sender,
            text = text,
            timestampMs = timestamp,
        )
    }

    // Bundle keys mirror the framework MessagingStyle.Message keys (constants
    // are not public API); verified against real Telegram snapshots in 0.1.
    // sender_person is the modern per-message sender; "sender" is the legacy
    // plain-string key written by older MessagingStyle.Message payloads and
    // is accepted as a fallback so historic sender shapes do not vanish.
    private const val KEY_MESSAGE_TEXT = "text"
    private const val KEY_MESSAGE_SENDER_PERSON = "sender_person"
    private const val KEY_MESSAGE_SENDER_NAME = "sender"
    private const val KEY_MESSAGE_TIME = "time"
    private const val EXTRA_IS_GROUP_CONVERSATION = "android.isGroupConversation"
}
