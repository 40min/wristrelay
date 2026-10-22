package dev.wristrelay.app

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.service.notification.StatusBarNotification
import androidx.test.core.app.ApplicationProvider
import dev.wristrelay.app.processing.MessageTracker
import dev.wristrelay.app.relay.RelayNotificationPublisher
import dev.wristrelay.app.settings.RelaySettings
import dev.wristrelay.app.settings.SettingsGate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RelayNotificationListenerTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val manager get() = context.getSystemService(NotificationManager::class.java)
    private lateinit var listener: RelayNotificationListener

    @Before
    fun setUp() {
        listener = Robolectric.buildService(RelayNotificationListener::class.java).create().get()
        inject("tracker", MessageTracker())
        inject("publisher", RelayNotificationPublisher(listener))
        SettingsGate.update(RelaySettings(true, true, true))
    }

    private fun inject(name: String, value: Any) {
        RelayNotificationListener::class.java.getDeclaredField(name).apply {
            isAccessible = true
            set(listener, value)
        }
    }

    private fun source(time: Long = 1000, summary: Boolean = false, messageTime: Long = time, id: Int = 7): StatusBarNotification {
        val notification = Notification.Builder(context, "telegram")
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setStyle(Notification.MessagingStyle("Me")
                .addMessage("OK", messageTime, "Sender"))
            .setGroupSummary(summary).build()
        return StatusBarNotification("org.telegram.messenger", "org.telegram.messenger", id,
            null, 1000, 0, 0, notification, android.os.Process.myUserHandle(), time)
    }

    private fun confirmCopies() {
        manager.activeNotifications.forEach {
            shadowOf(listener).addActiveNotification(it)
            listener.onNotificationPosted(it)
        }
    }

    @Test
    fun `original waits for own posted confirmation then only original disappears`() {
        val original = source()
        shadowOf(listener).addActiveNotification(original)
        listener.onNotificationPosted(original)
        assertTrue(listener.activeNotifications.any { it.key == original.key })
        assertEquals(1, manager.activeNotifications.size)
        confirmCopies()
        assertTrue(listener.activeNotifications.none { it.key == original.key })
        assertEquals(1, manager.activeNotifications.size)
    }

    @Test
    fun `off setting and denied posting never dismiss source`() {
        val original = source()
        shadowOf(listener).addActiveNotification(original)
        SettingsGate.update(RelaySettings(true, true, false))
        listener.onNotificationPosted(original)
        confirmCopies()
        assertTrue(listener.activeNotifications.any { it.key == original.key })
        SettingsGate.update(RelaySettings(true, true, true))
        shadowOf(manager).setNotificationsEnabled(false)
        listener.onNotificationPosted(source(2000))
        assertTrue(listener.activeNotifications.any { it.key == original.key })
    }

    @Test
    fun `summary and disabled relay do not publish or cancel`() {
        val summary = source(summary = true)
        shadowOf(listener).addActiveNotification(summary)
        listener.onNotificationPosted(summary)
        SettingsGate.update(RelaySettings(false, false, true))
        listener.onNotificationPosted(source(2000))
        assertTrue(manager.activeNotifications.isEmpty())
        assertTrue(listener.activeNotifications.any { it.key == summary.key })
    }

    @Test
    fun `own removal allows next identical timestamp message`() {
        val original = source()
        shadowOf(listener).addActiveNotification(original)
        listener.onNotificationPosted(original)
        confirmCopies()
        listener.onNotificationRemoved(original, listener.currentRanking,
            android.service.notification.NotificationListenerService.REASON_LISTENER_CANCEL)
        val next = source(2000, messageTime = 1000)
        shadowOf(listener).addActiveNotification(next)
        listener.onNotificationPosted(next)
        assertEquals(2, manager.activeNotifications.size)
    }
    @Test
    fun `blocked channel keeps original and publishes nothing`() {
        manager.createNotificationChannel(android.app.NotificationChannel(
            "wristrelay.relayed_messages_silent", "blocked", NotificationManager.IMPORTANCE_NONE,
        ))
        val original = source()
        shadowOf(listener).addActiveNotification(original)
        listener.onNotificationPosted(original)
        confirmCopies()
        assertTrue(manager.activeNotifications.isEmpty())
        assertTrue(listener.activeNotifications.any { it.key == original.key })
    }

    @Test
    fun `failure in another source cancels all pending hiding`() {
        val waiting = source(id = 7)
        shadowOf(listener).addActiveNotification(waiting)
        listener.onNotificationPosted(waiting)
        assertEquals(1, manager.activeNotifications.size)
        shadowOf(manager).setNotificationsEnabled(false)
        val failing = source(time = 2000, id = 8)
        shadowOf(listener).addActiveNotification(failing)
        listener.onNotificationPosted(failing)
        shadowOf(manager).setNotificationsEnabled(true)
        confirmCopies()
        assertTrue(listener.activeNotifications.any { it.key == waiting.key })
        assertTrue(listener.activeNotifications.any { it.key == failing.key })
    }

}
