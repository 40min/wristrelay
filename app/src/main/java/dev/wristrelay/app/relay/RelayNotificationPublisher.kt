package dev.wristrelay.app.relay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import dev.wristrelay.app.R
import dev.wristrelay.app.model.RelayMessage
import java.util.concurrent.atomic.AtomicInteger

/**
 * Publication boundary for the relay pipeline so detection is testable
 * without an Android NotificationManager; the only production
 * implementation posts real notifications.
 */
fun interface RelayPublisher {
    fun publish(messages: List<RelayMessage>)
}

/**
 * Publishes one independent Android notification per genuinely new message.
 *
 * Identities are unique per publication and never reused while entries are
 * untouched; there is no per-conversation update or replacement (ADR-002) and
 * source notifications are never modified or cancelled.
 *
 * The channel is silent (IMPORTANCE_LOW, no sound): WristRelay entries are
 * duplicated announcements of messages the phone already alerted for, and a
 * second alert on the phone would be noise (task 2.2). Watch mirroring of
 * silent notifications is a remaining device-validation item (2.2/3.1 in
 * VALIDATION.md); vibration on the watch is not affected by channel sound.
 */
class RelayNotificationPublisher(private val context: Context) : RelayPublisher {

    private val nextNotificationId = AtomicInteger(FIRST_NOTIFICATION_ID)

    override fun publish(messages: List<RelayMessage>) {
        if (messages.isEmpty()) return
        ensureChannel()
        val notificationManager = context.getSystemService(
            NotificationManager::class.java
        )
        if (notificationManager == null || !notificationManager.areNotificationsEnabled()) return

        messages.forEach { message ->
            val id = nextNotificationId.getAndIncrement()
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title(message))
                .setContentText(message.text)
                .setAutoCancel(false)
                .setOngoing(false)
                .setWhen(System.currentTimeMillis())
                .build()
            notificationManager.notify(tagFor(id), id, notification)
        }
    }

    private fun title(message: RelayMessage): String {
        val conversation = message.conversationTitle
        val sender = message.sender
        return when {
            message.isGroup && !sender.isNullOrBlank() && conversation != null ->
                "$conversation · $sender"
            !sender.isNullOrBlank() && conversation != null -> sender
            conversation != null -> conversation
            sender != null -> sender
            else -> FALLBACK_TITLE
        }
    }

    private fun ensureChannel() {
        val notificationManager = context.getSystemService(
            NotificationManager::class.java
        ) ?: return
        if (notificationManager.getNotificationChannel(CHANNEL_ID) != null) return
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Messages relayed to watch",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                setShowBadge(true)
                setLockscreenVisibility(Notification.VISIBILITY_PUBLIC)
                setSound(null, null)
            }
        )
    }

    private fun tagFor(id: Int): String = "$TAG_PREFIX$id"

    companion object {
        /** Starts far above low ids to avoid any collision with system allocations. */
        private const val FIRST_NOTIFICATION_ID = 0x5252_0000
        private const val TAG_PREFIX = "wristrelay-msg-"
        private const val CHANNEL_ID = "wristrelay.relayed_messages_silent"
        private const val FALLBACK_TITLE = "Message"
    }
}
