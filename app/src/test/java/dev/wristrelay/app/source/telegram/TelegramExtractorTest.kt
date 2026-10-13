package dev.wristrelay.app.source.telegram

import android.content.Context
import android.app.Notification
import android.os.Bundle
import androidx.core.app.NotificationCompat
import androidx.core.app.Person as CompatPerson
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Synthetic fixtures built through the framework MessagingStyle path, whose
 * parceled bundle shape is what Telegram actually delivers (verified on
 * device in task 0.1, docs/VALIDATION.md). No real message content.
 *
 * Telegram explicitly re-sets EXTRA_TITLE to the chat name after the
 * framework MessagingStyle convention would have used the last sender's
 * display name; the fixture builder mirrors that verified shape by overriding
 * the title post-build, like the real source does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TelegramExtractorTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private val senderOne: CompatPerson = CompatPerson.Builder()
        .setKey("fixture-sender-1")
        .setName("fixture-sender-one")
        .build()

    private fun messagingNotification(
        conversationTitle: String,
        isGroup: Boolean,
        vararg messages: Triple<String, Long, CompatPerson?>,
    ): Bundle {
        val selfPerson = CompatPerson.Builder().setName("fixture-me").build()
        val style = NotificationCompat.MessagingStyle(selfPerson)
            .setGroupConversation(isGroup)
        messages.forEach { (text, time, person) ->
            style.addMessage(text, time, person)
        }
        val notification = NotificationCompat.Builder(context, "fixture-channel")
            .setSmallIcon(1)
            .setStyle(style)
            .build()
        // Telegram overrides the framework-derived title with the chat name.
        notification.extras.putCharSequence(Notification.EXTRA_TITLE, conversationTitle)
        return notification.extras
    }

    private fun chat(vararg messages: Triple<String, Long, CompatPerson?>): Bundle =
        messagingNotification("fixture-chat", isGroup = false, *messages)

    @Test
    fun `single-message snapshot extracts one message`() {
        val extras = chat(Triple("one", 1000L, senderOne))
        val messages = TelegramExtractor.extractBundle(extras)!!
        assertEquals(1, messages.size)
        assertEquals("one", messages[0].text)
        assertEquals("fixture-chat", messages[0].conversationTitle)
        assertEquals("fixture-sender-one", messages[0].sender)
        assertFalse(messages[0].isGroup)
    }

    @Test
    fun `cumulative snapshot preserves order`() {
        val extras = chat(
            Triple("one", 1000L, senderOne),
            Triple("two", 2000L, senderOne),
            Triple("three", 3000L, senderOne),
        )
        val messages = TelegramExtractor.extractBundle(extras)!!
        assertEquals(listOf("one", "two", "three"), messages.map { it.text })
    }

    @Test
    fun `identical consecutive texts stay distinct entries`() {
        val extras = chat(
            Triple("ok", 1000L, senderOne),
            Triple("ok", 2000L, senderOne),
        )
        val messages = TelegramExtractor.extractBundle(extras)!!
        assertEquals(2, messages.size)
        assertEquals(listOf("ok", "ok"), messages.map { it.text })
    }

    @Test
    fun `group flag and per-message sender are extracted`() {
        val extras = messagingNotification(
            "fixture-group",
            isGroup = true,
            Triple("group-msg", 1000L, senderOne),
        )
        val messages = TelegramExtractor.extractBundle(extras)!!
        assertTrue(messages[0].isGroup)
        assertEquals("fixture-group", messages[0].conversationTitle)
        assertEquals("group-msg", messages[0].text)
        assertEquals("fixture-sender-one", messages[0].sender)
    }

    @Test
    fun `message without sender is tolerated with null sender`() {
        val extras = chat(Triple("anon", 1000L, null))
        val messages = TelegramExtractor.extractBundle(extras)!!
        assertNull(messages[0].sender)
        assertEquals("anon", messages[0].text)
    }

    @Test
    fun `empty message entries are skipped`() {
        val extras = chat(
            Triple("", 1000L, senderOne),
            Triple("two", 2000L, senderOne),
        )
        val messages = TelegramExtractor.extractBundle(extras)!!
        assertEquals(listOf("two"), messages.map { it.text })
    }

    @Test
    fun `non-messaging extras are unsupported and yield null`() {
        val notification = NotificationCompat.Builder(context, "fixture-channel")
            .setSmallIcon(1)
            .setContentTitle("plain")
            .setContentText("plain text body")
            .build()
        assertNull(TelegramExtractor.extractBundle(notification.extras))
        assertNull(TelegramExtractor.extractBundle(Bundle.EMPTY))
    }

    @Test
    fun `package gate matches the verified telegram package only`() {
        assertTrue(TelegramExtractor.matches("org.telegram.messenger"))
        assertFalse(TelegramExtractor.matches("dev.wristrelay.app"))
        assertFalse(TelegramExtractor.matches("com.example.app"))
    }
}
