package dev.wristrelay.app.source.telegram

import android.app.Notification
import android.app.Person
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Parcelable
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
 * device in task 0.1, docs/VALIDATION.md). Raw synthetic bundles cover
 * variant shapes by mirroring framework key names. No real message content
 * anywhere (PRIVACY.md).
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

    private val senderTwo: CompatPerson = CompatPerson.Builder()
        .setKey("fixture-sender-2")
        .setName("fixture-sender-two")
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

    /** Raw bundle for shapes the framework builder cannot produce. */
    private fun rawExtras(
        vararg messages: Parcelable,
        conversationTitle: CharSequence? = "fixture-chat",
        isGroup: Boolean = false,
    ): Bundle = Bundle().apply {
        if (conversationTitle != null) putCharSequence(Notification.EXTRA_TITLE, conversationTitle)
        putBoolean("android.isGroupConversation", isGroup)
        putParcelableArray(Notification.EXTRA_MESSAGES, messages)
    }

    private fun rawMessage(
        text: CharSequence? = null,
        senderPerson: Person? = null,
        senderName: String? = null,
        timeMillis: Long? = null,
        timeInt: Int? = null,
    ): Bundle = Bundle().apply {
        if (text != null) putCharSequence("text", text)
        senderPerson?.let { putParcelable("sender_person", it) }
        senderName?.let { putString("sender", it) }
        timeMillis?.let { putLong("time", it) }
        timeInt?.let { putInt("time", it) }
    }

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
    fun `per-message timestamps are extracted`() {
        val extras = chat(
            Triple("one", 1000L, senderOne),
            Triple("two", 2000L, senderOne),
        )
        val messages = TelegramExtractor.extractBundle(extras)!!
        assertEquals(listOf(1000L, 2000L), messages.map { it.timestampMs })
    }

    @Test
    fun `out-of-order timestamps keep array order and delivered values`() {
        val extras = chat(
            Triple("late-text", 5000L, senderOne),
            Triple("early-text", 1000L, senderOne),
            Triple("middle-text", 3000L, senderOne),
        )
        val messages = TelegramExtractor.extractBundle(extras)!!
        assertEquals(
            listOf("late-text", "early-text", "middle-text"),
            messages.map { it.text },
        )
        assertEquals(
            listOf(5000L, 1000L, 3000L),
            messages.map { it.timestampMs },
        )
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
    fun `group keeps each message's own sender`() {
        val extras = messagingNotification(
            "fixture-group",
            isGroup = true,
            Triple("msg-from-one", 1000L, senderOne),
            Triple("msg-from-two", 2000L, senderTwo),
        )
        val messages = TelegramExtractor.extractBundle(extras)!!
        assertEquals("fixture-sender-one", messages[0].sender)
        assertEquals("fixture-sender-two", messages[1].sender)
        assertTrue(messages[0].isGroup)
        assertTrue(messages[1].isGroup)
    }

    @Test
    fun `message without sender is tolerated with null sender`() {
        val extras = chat(Triple("anon", 1000L, null))
        val messages = TelegramExtractor.extractBundle(extras)!!
        assertNull(messages[0].sender)
        assertEquals("anon", messages[0].text)
    }

    @Test
    fun `legacy plain-string sender key is accepted as fallback`() {
        val entry = rawMessage(text = "legacy-sender-text", senderName = "fixture-legacy-sender")
        val extras = rawExtras(entry)
        val messages = TelegramExtractor.extractBundle(extras)!!
        assertEquals("fixture-legacy-sender", messages[0].sender)
        assertEquals("legacy-sender-text", messages[0].text)
    }

    @Test
    fun `int-shaped per-message time is tolerated as long`() {
        val entry = rawMessage(text = "int-time-text", timeInt = 1234)
        val extras = rawExtras(entry)
        val messages = TelegramExtractor.extractBundle(extras)!!
        assertEquals(1234L, messages[0].timestampMs)
    }

    @Test
    fun `missing timestamp yields null without dropping the message`() {
        val entry = rawMessage(text = "no-time-text")
        val extras = rawExtras(entry)
        val messages = TelegramExtractor.extractBundle(extras)!!
        assertEquals(1, messages.size)
        assertNull(messages[0].timestampMs)
    }

    @Test
    fun `missing conversation title is tolerated with null`() {
        val entry = rawMessage(text = "untitled-text", timeMillis = 1000L)
        val extras = rawExtras(entry, conversationTitle = null)
        val messages = TelegramExtractor.extractBundle(extras)!!
        assertNull(messages[0].conversationTitle)
        assertEquals("untitled-text", messages[0].text)
    }

    @Test
    fun `media-only entry without text is skipped and later entries survive`() {
        val extras = chat(
            Triple("", 1000L, senderOne),
            Triple("two", 2000L, senderOne),
        )
        val messages = TelegramExtractor.extractBundle(extras)!!
        assertEquals(listOf("two"), messages.map { it.text })
    }

    @Test
    fun `entry with no text key at all is skipped`() {
        val extras = chat(Triple("one", 1000L, senderOne))
        // Simulate a stripped payload entry appended after a real message.
        val array = extras.getParcelableArray(Notification.EXTRA_MESSAGES)!!
        val grown = array.copyOf(array.size + 1)
        grown[array.size] = Bundle()
        extras.putParcelableArray(Notification.EXTRA_MESSAGES, grown as Array<Parcelable>)
        val messages = TelegramExtractor.extractBundle(extras)!!
        assertEquals(listOf("one"), messages.map { it.text })
    }

    @Test
    fun `snapshot of only media or empty entries yields null`() {
        assertNull(TelegramExtractor.extractBundle(rawExtras(Bundle())))
    }

    @Test
    fun `non-bundle array entries are skipped not fatal`() {
        val extras = rawExtras(
            Intent("fixture-nonparcelable-stray"),
            rawMessage(text = "one", timeMillis = 1000L),
        )
        val messages = TelegramExtractor.extractBundle(extras)!!
        assertEquals(listOf("one"), messages.map { it.text })
    }

    @Test
    fun `summary-style snapshot without messaging array is unsupported`() {
        val summary = Bundle().apply {
            putCharSequence(Notification.EXTRA_TITLE, "fixture-chat")
            putCharSequence(Notification.EXTRA_TEXT, "2 new messages")
        }
        assertNull(TelegramExtractor.extractBundle(summary))
    }

    @Test
    fun `empty messaging array is unsupported not empty list`() {
        assertNull(TelegramExtractor.extractBundle(rawExtras()))
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
