package dev.wristrelay.app.relay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.core.app.NotificationCompat
import dev.wristrelay.app.R
import dev.wristrelay.app.model.RelayMessage
import dev.wristrelay.app.settings.RelaySettings
import java.util.concurrent.atomic.AtomicInteger

/**
 * Publication boundary for the relay pipeline so detection is testable
 * without an Android NotificationManager; the only production
 * implementation posts real notifications.
 */
fun interface RelayPublisher {
    fun publish(messages: List<RelayMessage>): RelayPublication
}

/** Android identity shared by one relayed notification's tag and integer ID. */
data class RelayNotificationIdentity(val tag: String, val id: Int)

/**
 * Result of one publication attempt: the identities Android accepted,
 * in requested order, stopping at the first submission failure.
 * Copies after that failure are never attempted in the same call, so
 * [submittedIdentities] is exactly the successfully submitted prefix
 * (publication-loss guard, Cf7Lga4n2Sx7): the tracker advances only
 * through it, and unsubmitted copies retry on the next qualifying event.
 */
data class RelayPublication(
    val submittedIdentities: List<RelayNotificationIdentity>,
    val requestedCount: Int,
    val failureReason: String? = null,
) {
    val submittedCount: Int get() = submittedIdentities.size

    /** True when notify() accepted every requested copy. */
    val fullySubmitted: Boolean get() = submittedCount == requestedCount

    companion object {
        /** Nothing was submitted: posting disabled or channel blocked. */
        fun refused(requestedCount: Int, reason: String): RelayPublication =
            RelayPublication(emptyList(), requestedCount, reason)
    }
}

/**
 * Publishes one independent Android notification per genuinely new message.
 *
 * Identities are unique per publication and never reused while a copy is
 * still active: publisher instances in one process share a process-wide
 * counter, because notify() is asynchronous and a second publisher may
 * scan activeNotifications before an earlier copy becomes visible; every
 * publication additionally starts above any active WristRelay copy so a
 * reconnected listener or process restart cannot replace an earlier entry
 * by reallocating the same tag/ID pair. There is no per-conversation update
 * or replacement (ADR-002) and non-activity source actions dismiss only
 * their originating copy after handoff.
 *
 * Submission stops at the first copy Android refuses (Cf7Lga4n2Sx7):
 * the returned [RelayPublication] reports the successfully submitted
 * prefix, so detection never advances past copies Android did not accept;
 * the missing copies retry on the next qualifying event for the
 * conversation. Telegram originals wait for a fully confirmed batch.
 *
 * The channel is silent (IMPORTANCE_LOW, no sound): WristRelay entries are
 * duplicated announcements of messages the phone already alerted for, and a
 * second alert on the phone would be noise (task 2.2). Watch mirroring of
 * silent notifications is a remaining device-validation item (2.2/3.1 in
 * VALIDATION.md); vibration on the watch is not affected by channel sound.
 */
class RelayNotificationPublisher internal constructor(
    private val context: Context,
    private val submit: (NotificationManager, String, Int, Notification) -> Unit,
) : RelayPublisher {
    constructor(context: Context) : this(context, NotificationManager::notify)

    override fun publish(messages: List<RelayMessage>): RelayPublication = publish(messages, null, null)

    fun publish(
        messages: List<RelayMessage>,
        source: Notification?,
        sourcePackage: String?,
        settings: RelaySettings = RelaySettings(),
    ): RelayPublication {
        if (messages.isNotEmpty()) ensureChannel()
        val notificationManager = context.getSystemService(
            NotificationManager::class.java
        )
        val postingEnabled = notificationManager?.areNotificationsEnabled() == true
        val channelBlocked = notificationManager?.getNotificationChannel(CHANNEL_ID)?.importance ==
            NotificationManager.IMPORTANCE_NONE
        if (messages.isEmpty() || !postingEnabled || channelBlocked) {
            return if (messages.isEmpty()) RelayPublication(emptyList(), 0)
            else RelayPublication.refused(messages.size, "publication not confirmed")
        }
        checkNotNull(notificationManager)
        skipActiveIdentities(notificationManager)
        val submitted = mutableListOf<RelayNotificationIdentity>()
        val failureReason = try {
            for (message in messages) {
                val id = nextNotificationId.getAndIncrement()
                val tag = tagFor(id)
                submit(notificationManager, tag, id, buildCopy(message, source, sourcePackage, id, settings))
                submitted.add(RelayNotificationIdentity(tag, id))
            }
            null
        } catch (failure: SecurityException) {
            "publish denied: ${failure.javaClass.simpleName}"
        } catch (failure: IllegalArgumentException) {
            "publish rejected: ${failure.javaClass.simpleName}"
        }
        return RelayPublication(submitted.toList(), messages.size, failureReason)
    }

    private fun buildCopy(
        message: RelayMessage,
        source: Notification?,
        sourcePackage: String?,
        id: Int,
        settings: RelaySettings,
    ): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title(message))
            .setContentText(message.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.text))
            .setTimeoutAfter(settings.timeoutMillis)
            .setAutoCancel(false)
            .setOngoing(false)
            .setWhen(System.currentTimeMillis())
            .apply {
                source?.contentIntent?.takeIf { it.creatorPackage == sourcePackage }?.let {
                    setContentIntent(it)
                }

            }
            .build().apply {
                actions = source?.actions?.mapIndexedNotNull { index, action ->
                    action.takeIf { it.actionIntent?.creatorPackage == sourcePackage }?.let {
                        RelayActionReceiver.wrap(context, it, sourcePackage, id, index)
                    }
                }?.toTypedArray()
            }
    }

    /**
     * Publisher instances in one process already share a process-wide counter,
     * because notify() is asynchronous: a second publisher may read
     * activeNotifications before the first copy becomes visible and would
     * otherwise allocate the same tag/ID pair. A process restart resets the
     * shared counter, so every publication also starts above any still-active
     * copy (ADR-002).
     */
    private fun skipActiveIdentities(notificationManager: NotificationManager) {
        val highestActive = notificationManager.activeNotifications
            .filter { it.tag?.startsWith(TAG_PREFIX) == true }
            .maxOfOrNull { it.id } ?: return
        if (highestActive < Int.MAX_VALUE) {
            nextNotificationId.updateAndGet { maxOf(it, highestActive + 1) }
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

        /**
         * Shared by every publisher instance in the process: notify() is
         * asynchronous, so a second publisher whose active-copy scan runs
         * before an earlier copy becomes visible must still allocate a
         * fresh identity.
         */
        private val nextNotificationId = AtomicInteger(FIRST_NOTIFICATION_ID)

        /** Simulates the process death that resets the counter; tests only. */
        @VisibleForTesting
        internal fun resetIdentitiesForTest() = nextNotificationId.set(FIRST_NOTIFICATION_ID)

        private const val TAG_PREFIX = "wristrelay-msg-"
        private const val CHANNEL_ID = "wristrelay.relayed_messages_silent"
        private const val FALLBACK_TITLE = "Message"
    }
}
