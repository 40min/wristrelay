package dev.wristrelay.app.relay

import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import dev.wristrelay.app.model.RelayMessage

/**
 * Publication behavior verified against Robolectric's NotificationManager
 * shadow: uniqueness, presentation and permission failure. Watch mirroring
 * and vibration remain device evidence (VALIDATION.md), never unit test facts.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RelayNotificationPublisherTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var publisher: RelayNotificationPublisher

    private val notificationManager: NotificationManager
        get() = context.getSystemService(NotificationManager::class.java)

    private fun posted() = notificationManager.activeNotifications.asIterable().map { status -> status }

    @Before
    fun setUp() {
        shadowOf(notificationManager).setNotificationsEnabled(true)
        publisher = RelayNotificationPublisher(context)
    }

    private fun message(
        text: String = "one",
        title: String? = "fixture-chat",
        sender: String? = "sender-a",
        isGroup: Boolean = false,
    ) = RelayMessage(
        conversationTitle = title,
        isGroup = isGroup,
        sender = sender,
        text = text,
    )

    @Test
    fun `each message becomes one independent notification`() {
        publisher.publish(listOf(message(text = "one"), message(text = "two", sender = null)))
        val posted = posted()
        assertEquals(2, posted.size)
    }

    @Test
    fun `expanded notification retains the full multiline message`() {
        val text = "First line 🌿\nSecond line with more detail\nThird line 🙂"
        publisher.publish(listOf(message(text), message("separate copy")))

        val posted = posted().associateBy { it.notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString() }
        val longMessage = posted.getValue(text).notification.extras
        assertEquals(text, longMessage.getCharSequence(android.app.Notification.EXTRA_BIG_TEXT).toString())
        assertEquals(text, longMessage.getCharSequence(android.app.Notification.EXTRA_TEXT).toString())
        assertEquals(2, posted.size)
    }

    @Test
    fun `identities are unique across repeated identical publications`() {
        val batch = listOf(message(text = "one"))
        publisher.publish(batch)
        publisher.publish(batch)
        val posted = posted()
        val tags: Set<String?> = posted.map { status -> status.tag }.toSet()
        val ids: Set<Int> = posted.map { status -> status.id }.toSet()
        assertEquals(2, posted.size)
        assertEquals(2, tags.size)
        assertEquals(2, ids.size)
    }

    @Test
    fun `posted notifications are never ongoing or grouped`() {
        publisher.publish(listOf(message(text = "one")))
        val posted = posted()
        assertEquals(1, posted.size)
        assertTrue(!posted[0].isOngoing)
        assertEquals(0, posted[0].notification.flags and android.app.Notification.FLAG_GROUP_SUMMARY)
        assertEquals(0, posted[0].notification.flags and android.app.Notification.FLAG_AUTO_CANCEL)
    }

    @Test
    fun `group message title shows conversation and sender`() {
        publisher.publish(
            listOf(
                message(text = "group-msg", title = "fixture-group", sender = "sender-a", isGroup = true),
            ),
        )
        val posted = posted()
        val title = posted[0].notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString()
        val body = posted[0].notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT).toString()
        assertEquals("fixture-group · sender-a", title)
        assertEquals("group-msg", body)
    }

    @Test
    fun `private chat title uses the sender`() {
        publisher.publish(
            listOf(message(text = "one", title = "fixture-chat", sender = "sender-a")),
        )
        val posted = posted()
        assertEquals("sender-a", posted[0].notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString())
    }

    @Test
    fun `title falls back gracefully when metadata is missing`() {
        publisher.publish(
            listOf(message(text = "anon", title = null, sender = null)),
        )
        val posted = posted()
        assertEquals("Message", posted[0].notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE).toString())
    }

    @Test
    fun `relay channel is silent on the phone`() {
        publisher.publish(listOf(message(text = "one")))
        val channel = notificationManager.getNotificationChannel(
            "wristrelay.relayed_messages_silent"
        )!!
        assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
        assertTrue(channel.sound == null)
    }

    @Test
    fun `legacy loud channel is left untouched`() {
        notificationManager.createNotificationChannel(
            android.app.NotificationChannel(
                "wristrelay.relayed_messages",
                "old prototype channel",
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        publisher.publish(listOf(message(text = "one")))
        val legacy = notificationManager.getNotificationChannel("wristrelay.relayed_messages")!!
        assertEquals(NotificationManager.IMPORTANCE_DEFAULT, legacy.importance)
        // New entries use the silent channel only.
        assertEquals(
            setOf("wristrelay.relayed_messages_silent"),
            notificationManager.activeNotifications.mapTo(mutableSetOf()) { it.notification.channelId },
        )
    }

    @Test
    fun `empty publication does nothing`() {
        publisher.publish(emptyList())
        assertEquals(0, posted().size)
    }

    @Test
    fun `posting permission failure publishes nothing and does not crash`() {
        shadowOf(notificationManager).setNotificationsEnabled(false)
        publisher.publish(listOf(message(text = "one")))
        assertEquals(0, posted().size)
    }

    @Test
    fun `burst of many messages produces the same number of entries`() {
        val burst = (1..10).map { message(text = "burst-$it") }
        publisher.publish(burst)
        val posted = posted()
        assertEquals(10, posted.size)
        val ids: Set<Int> = posted.map { status -> status.id }.toSet()
        assertEquals(10, ids.size)
    }
    @Test
    fun `copies wrap supplied action token and retain remote input without delete intent`() {
        val intent = android.app.PendingIntent.getBroadcast(
            context, 42, android.content.Intent("fixture.reply").setPackage(context.packageName),
            android.app.PendingIntent.FLAG_MUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val remote = android.app.RemoteInput.Builder("reply").setLabel("Reply").build()
        val action = android.app.Notification.Action.Builder(null, "Reply", intent)
            .addRemoteInput(remote).build()
        val source = android.app.Notification.Builder(context, "source")
            .setSmallIcon(dev.wristrelay.app.R.drawable.ic_notification)
            .setContentIntent(intent).setDeleteIntent(intent).addAction(action).build()
        assertTrue(publisher.publish(listOf(message("one"), message("two")), source, context.packageName))
        posted().forEach {
            assertNotEquals(intent, it.notification.actions.single().actionIntent)
            assertEquals("reply", it.notification.actions.single().remoteInputs.single().resultKey)
            assertEquals(intent, it.notification.contentIntent)
            assertEquals(null, it.notification.deleteIntent)
        }
        assertEquals(2, posted().map { it.id }.toSet().size)
    }

    @Test
    fun `blocked channel is not confirmed published`() {
        notificationManager.createNotificationChannel(android.app.NotificationChannel(
            "wristrelay.relayed_messages_silent", "blocked", NotificationManager.IMPORTANCE_NONE,
        ))
        assertTrue(!publisher.publish(listOf(message())))
        assertTrue(posted().isEmpty())
    }

}
