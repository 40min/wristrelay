package dev.wristrelay.app.relay

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import dev.wristrelay.app.R
import dev.wristrelay.app.model.RelayMessage
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RelayActionReceiverTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val telegram = object : ContextWrapper(context) {
        override fun getPackageName() = "org.telegram.messenger"
    }
    private val manager get() = context.getSystemService(NotificationManager::class.java)

    private fun copies(
        reply: Boolean = false,
        title: String = "Action",
        semantic: Int = Notification.Action.SEMANTIC_ACTION_MARK_AS_READ,
    ): PendingIntent {
        val target = PendingIntent.getBroadcast(telegram, 42, Intent("fixture.action").setPackage(context.packageName),
            PendingIntent.FLAG_MUTABLE)
        shadowOf(target).setCreatorPackage(telegram.packageName)
        val icon = android.graphics.drawable.Icon.createWithResource(context, R.drawable.ic_notification)
        val action = Notification.Action.Builder(icon, title, target).apply {
            setSemanticAction(if (reply) Notification.Action.SEMANTIC_ACTION_REPLY else semantic)
            setAllowGeneratedReplies(true)
            setAuthenticationRequired(true)
            setContextual(true)
            if (reply) addRemoteInput(RemoteInput.Builder("reply").setLabel("Reply")
                .setChoices(arrayOf("Yes", "No")).setAllowDataType("image/png", true).build())
        }.build()
        val source = Notification.Builder(context, "source").setSmallIcon(R.drawable.ic_notification)
            .addAction(action).build()
        assertTrue(RelayNotificationPublisher(context).publish(
            listOf(RelayMessage(null, false, null, "one"), RelayMessage(null, false, null, "two")),
            source, telegram.packageName,
        ))
        return target
    }

    private fun invoke(intent: Intent) = RelayActionReceiver().onReceive(context, intent)

    private fun publishCompletionAndReplyActions() {
        fun target(requestCode: Int, flags: Int) = PendingIntent.getBroadcast(
            telegram, requestCode, Intent("fixture.action.$requestCode").setPackage(context.packageName), flags,
        ).also { shadowOf(it).setCreatorPackage(telegram.packageName) }

        val icon = android.graphics.drawable.Icon.createWithResource(context, R.drawable.ic_notification)
        val markRead = Notification.Action.Builder(
            icon, "Mark as read", target(50, PendingIntent.FLAG_IMMUTABLE),
        ).setSemanticAction(Notification.Action.SEMANTIC_ACTION_MARK_AS_READ).build()
        val reply = Notification.Action.Builder(
            icon, "Reply", target(51, PendingIntent.FLAG_MUTABLE),
        ).setSemanticAction(Notification.Action.SEMANTIC_ACTION_REPLY)
            .addRemoteInput(RemoteInput.Builder("reply").setLabel("Reply").build()).build()
        val source = Notification.Builder(context, "source").setSmallIcon(R.drawable.ic_notification)
            .addAction(markRead).addAction(reply).build()
        assertTrue(RelayNotificationPublisher(context).publish(
            listOf(RelayMessage(null, false, null, "one"), RelayMessage(null, false, null, "two")),
            source, telegram.packageName,
        ))
        val originalTokens = listOf(markRead.actionIntent, reply.actionIntent)
        assertTrue(manager.activeNotifications.all { entry ->
            entry.notification.actions.map { it.actionIntent } == originalTokens
        })
    }

    @Test
    @Config(sdk = [29, 30])
    fun `older Android versions preserve direct mark read and inline reply tokens`() {
        publishCompletionAndReplyActions()
    }

    @Test
    fun `mark read handoff removes only originating copy with independent tokens`() {
        val target = copies()
        val entries = manager.activeNotifications
        val first = entries[0].notification.actions.single().actionIntent
        val second = entries[1].notification.actions.single().actionIntent
        assertNotEquals(target, first)
        assertNotEquals(first, second)
        assertTrue(first.isImmutable)
        invoke(shadowOf(first).savedIntent)
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(listOf(entries[1].id), manager.activeNotifications.map { it.id })
        val delivered = shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).broadcastIntents
        assertTrue(delivered.any { it.action == "fixture.action" })
    }

    @Test
    fun `reply waits for submit and forwards results without wrapper metadata`() {
        copies(reply = true)
        val entries = manager.activeNotifications
        val action = entries[0].notification.actions.single()
        val remote = action.remoteInputs.single()
        assertEquals(listOf("Yes", "No"), remote.choices.toList())
        assertEquals(setOf("image/png"), remote.allowedDataTypes)
        assertEquals(Notification.Action.SEMANTIC_ACTION_REPLY, action.semanticAction)
        assertTrue(action.allowGeneratedReplies)
        assertTrue(action.isAuthenticationRequired)
        assertTrue(action.isContextual)
        // Direct invocation without a matching submission also leaves both copies and target untouched.
        val emptySubmit = Intent(shadowOf(action.actionIntent).savedIntent)
        invoke(emptySubmit)
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        assertTrue(shadowOf(app).broadcastIntents.none { it.action == "fixture.action" })
        assertEquals(2, manager.activeNotifications.size)
        val submitted = Intent(shadowOf(action.actionIntent).savedIntent)
        RemoteInput.addResultsToIntent(arrayOf(remote), submitted, Bundle().apply { putCharSequence("reply", "fixture answer") })
        val dataUri = android.net.Uri.parse("content://fixture/attachment")
        RemoteInput.addDataResultToIntent(remote, submitted, mapOf("image/png" to dataUri))
        RemoteInput.setResultsSource(submitted, RemoteInput.SOURCE_CHOICE)
        invoke(submitted)
        shadowOf(Looper.getMainLooper()).idle()
        val delivered = shadowOf(ApplicationProvider.getApplicationContext<android.app.Application>()).broadcastIntents
            .last { it.action == "fixture.action" }
        assertEquals("fixture answer", RemoteInput.getResultsFromIntent(delivered)?.getCharSequence("reply"))
        assertEquals(RemoteInput.SOURCE_CHOICE, RemoteInput.getResultsSource(delivered))
        assertEquals(dataUri, RemoteInput.getDataResultsFromIntent(delivered, "reply")?.get("image/png"))
        assertNull(delivered.getParcelableExtra<PendingIntent>("relay.action.target"))
        assertEquals(listOf(entries[1].id), manager.activeNotifications.map { it.id })
    }

    @Test
    fun `English mark read fallback cleans only originating copy`() {
        copies(title = "  mArK aS rEaD  ", semantic = Notification.Action.SEMANTIC_ACTION_NONE)
        val entries = manager.activeNotifications
        invoke(shadowOf(entries[0].notification.actions.single().actionIntent).savedIntent)
        assertEquals(listOf(entries[1].id), manager.activeNotifications.map { it.id })
    }

    @Test
    fun `no input Reply compose action remains direct and retains copies`() {
        val target = copies(title = "Reply", semantic = Notification.Action.SEMANTIC_ACTION_REPLY)
        assertTrue(manager.activeNotifications.all { it.notification.actions.single().actionIntent == target })
        target.send()
        shadowOf(Looper.getMainLooper()).idle()
        assertEquals(2, manager.activeNotifications.size)
    }

    @Test
    fun `stale wrapper cannot cancel newer copy with reused identity`() {
        copies()
        val old = manager.activeNotifications[0]
        val intent = Intent(shadowOf(old.notification.actions.single().actionIntent).savedIntent)
        manager.cancelAll()
        copies()
        invoke(intent)
        assertEquals(2, manager.activeNotifications.size)
    }

    @Test
    fun `canceled target retains both copies`() {
        val target = copies()
        val intent = shadowOf(manager.activeNotifications[0].notification.actions.single().actionIntent).savedIntent
        target.cancel()
        invoke(intent)
        assertEquals(2, manager.activeNotifications.size)
    }

    @Test
    fun `malformed identity or rejected source retains both copies`() {
        copies()
        val intent = shadowOf(manager.activeNotifications[0].notification.actions.single().actionIntent).savedIntent
        invoke(Intent(intent).putExtra("relay.action.id", -1))
        invoke(Intent(intent).putExtra("relay.action.source", context.packageName))
        val rejected = PendingIntent.getBroadcast(context, 900, Intent("wrong"), PendingIntent.FLAG_IMMUTABLE)
        invoke(Intent(intent).putExtra("relay.action.target", rejected))
        invoke(Intent(intent).setData(android.net.Uri.parse("wrong://host")))
        invoke(Intent())
        assertEquals(2, manager.activeNotifications.size)
    }
}
