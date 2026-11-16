package dev.wristrelay.app

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import android.media.session.MediaSession
import android.os.Bundle
import android.os.Looper
import android.service.notification.StatusBarNotification
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.test.core.app.ApplicationProvider
import dev.wristrelay.app.debug.DebugEventStore
import dev.wristrelay.app.debug.DebugFormat
import dev.wristrelay.app.settings.FaultingSettingsDataStore
import dev.wristrelay.app.settings.RelaySettings
import dev.wristrelay.app.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
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
    private val mainLooper get() = shadowOf(Looper.getMainLooper())
    private val preferences = MutableStateFlow(relayPreferences(RelaySettings(true, true, true)))

    @Before
    fun setUp() {
        listener = Robolectric.buildService(RelayNotificationListener::class.java).create().get()
        listener.openSettings = {
            SettingsStore(
                object : DataStore<Preferences> {
                    override val data = preferences

                    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
                        error("These tests only collect settings")
                    }
                },
                CoroutineScope(Dispatchers.Main),
            )
        }
    }

    @After
    fun tearDown() {
        listener.onDestroy()
    }

    private fun relayPreferences(settings: RelaySettings) = preferencesOf(
        booleanPreferencesKey("master_enabled") to settings.masterEnabled,
        booleanPreferencesKey("telegram_enabled") to settings.telegramEnabled,
        booleanPreferencesKey("hide_telegram_originals") to settings.hideTelegramOriginals,
    )

    private fun connect() {
        listener.onListenerConnected()
        mainLooper.idle()
    }

    private fun changeSettings(settings: RelaySettings) {
        preferences.value = relayPreferences(settings)
        mainLooper.idle()
    }

    @Test
    fun settingsReadFailureStopsRelayingAndRestoresMutedVolume() {
        val dataStore = FaultingSettingsDataStore(
            preferencesOf(
                booleanPreferencesKey("master_enabled") to true,
                booleanPreferencesKey("telegram_enabled") to true,
                booleanPreferencesKey("spotify_ad_mute_enabled") to true,
            ),
        )
        listener.openSettings = { SettingsStore(dataStore, CoroutineScope(Dispatchers.Main)) }
        val audio = listener.getSystemService(AudioManager::class.java)
        audio.setStreamVolume(AudioManager.STREAM_MUSIC, 5, 0)

        connect()
        val mediaSession = MediaSession(context, "test-ad")
        val notification = Notification.Builder(context, "spotify")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentText("Advertisement")
            .addExtras(Bundle().apply { putParcelable(Notification.EXTRA_MEDIA_SESSION, mediaSession.sessionToken) })
            .build()
        val ad = StatusBarNotification(
            "com.spotify.music", "com.spotify.music", 2, null, 1000, 0, 0,
            notification, android.os.Process.myUserHandle(), 1000,
        )
        listener.onNotificationPosted(ad)
        assertEquals(0, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
        listener.onNotificationPosted(source())
        assertEquals(1, manager.activeNotifications.size)

        dataStore.failRead.complete(Unit)
        mainLooper.idle()
        listener.onNotificationPosted(source(time = 2000, id = 8))

        assertEquals(5, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
        assertEquals(1, manager.activeNotifications.size)
        listener.onListenerDisconnected()
        mediaSession.release()
    }

    private fun source(
        time: Long = 1000,
        summary: Boolean = false,
        messageTime: Long = time,
        id: Int = 7,
        messages: List<Pair<String, Long>> = listOf("OK" to messageTime),
    ): StatusBarNotification {
        val notification = Notification.Builder(context, "telegram")
            .setSmallIcon(R.drawable.ic_notification)
            .setCategory(Notification.CATEGORY_MESSAGE)
            .setStyle(Notification.MessagingStyle("Me").apply {
                messages.forEach { (text, timestamp) -> addMessage(text, timestamp, "Sender") }
            })
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
    fun `diagnostics require explicit debug capture and retain metadata only`() {
        DebugEventStore.setEnabled(false)
        DebugEventStore.clear()
        changeSettings(RelaySettings(false, false, false))
        connect()
        val original = source()
        original.notification.extras.putCharSequence(Notification.EXTRA_TEXT, "private message")

        listener.onNotificationPosted(original)
        assertTrue(DebugEventStore.snapshot().isEmpty())
        try {
            DebugEventStore.setEnabled(true)
            listener.onNotificationPosted(original)
            DebugEventStore.setEnabled(false)
            listener.onNotificationPosted(original)

            val events = DebugEventStore.snapshot()
            assertEquals(if (BuildConfig.DEBUG) 1 else 0, events.size)
            if (BuildConfig.DEBUG) {
                val event = events.single()
                val lines = DebugFormat.format(event)
                assertEquals(original.key, event.key)
                assertTrue(lines.any { it.startsWith("extra android.text:") && it.endsWith("len=15") })
                assertTrue(lines.none { it.contains("private message") || it.contains("Sender") })
            }
        } finally {
            DebugEventStore.setEnabled(false)
            DebugEventStore.clear()
        }
    }

    @Test
    fun `connection and reconnection baseline active snapshots before publishing new messages`() {
        val original = source()
        shadowOf(listener).addActiveNotification(original)

        connect()
        listener.onNotificationPosted(original)
        assertTrue(manager.activeNotifications.isEmpty())

        val fresh = source(time = 2000)
        shadowOf(listener).addActiveNotification(fresh)
        listener.onNotificationPosted(fresh)
        assertEquals(1, manager.activeNotifications.size)

        listener.onListenerDisconnected()
        connect()
        listener.onNotificationPosted(fresh)
        assertEquals(1, manager.activeNotifications.size)
        assertTrue(listener.activeNotifications.any { it.key == fresh.key })
    }

    @Test
    @Config(sdk = [29, 30, 34])
    fun `reconnect absorbs overflow and restored history then publishes only the append`() {
        connect()
        listener.onListenerDisconnected()
        // Reconnect with more active conversations than the tracker keeps in its 16-entry baseline.
        (7..23).forEach { shadowOf(listener).addActiveNotification(source(id = it)) }
        connect()

        val restored = listOf("OK" to 1000L, "historical" to 2000L)
        listener.onNotificationPosted(source(time = 2000, messages = restored))
        assertTrue(manager.activeNotifications.isEmpty())

        listener.onNotificationPosted(source(time = 3000, messages = restored + ("fresh" to 3000L)))
        val copy = manager.activeNotifications.single()
        assertEquals("fresh", copy.notification.extras.getCharSequence(Notification.EXTRA_TEXT).toString())
    }

    @Test
    @Config(sdk = [29, 30, 34])
    fun `publication denial preserves the original and the same snapshot can recover`() {
        connect()
        val original = source()
        shadowOf(listener).addActiveNotification(original)
        shadowOf(manager).setNotificationsEnabled(false)
        listener.onNotificationPosted(original)
        assertTrue(manager.activeNotifications.isEmpty())
        assertTrue(listener.activeNotifications.any { it.key == original.key })

        shadowOf(manager).setNotificationsEnabled(true)
        listener.onNotificationPosted(original)
        listener.onNotificationPosted(original)
        assertEquals(1, manager.activeNotifications.size)
        assertTrue(listener.activeNotifications.any { it.key == original.key })

        confirmCopies()
        assertTrue(listener.activeNotifications.any { it.key == original.key })
        assertEquals(1, manager.activeNotifications.size)

        listener.onListenerDisconnected()
        connect()
        listener.onNotificationPosted(original)
        assertEquals(1, manager.activeNotifications.size)

        val fresh = source(time = 2000, id = 8)
        shadowOf(listener).addActiveNotification(fresh)
        listener.onNotificationPosted(fresh)
        confirmCopies()
        assertTrue(listener.activeNotifications.any { it.key == original.key })
        assertTrue(listener.activeNotifications.none { it.key == fresh.key })
        assertEquals(2, manager.activeNotifications.size)
    }

    @Test
    fun `original waits for own posted confirmation then only original disappears`() {
        connect()
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
        connect()
        val original = source()
        shadowOf(listener).addActiveNotification(original)
        changeSettings(RelaySettings(true, true, false))
        listener.onNotificationPosted(original)
        confirmCopies()
        assertTrue(listener.activeNotifications.any { it.key == original.key })
        changeSettings(RelaySettings(true, true, true))
        shadowOf(manager).setNotificationsEnabled(false)
        listener.onNotificationPosted(source(2000))
        assertTrue(listener.activeNotifications.any { it.key == original.key })
    }

    @Test
    fun `summary and disabled relay do not publish or cancel`() {
        connect()
        val summary = source(summary = true)
        shadowOf(listener).addActiveNotification(summary)
        listener.onNotificationPosted(summary)
        changeSettings(RelaySettings(false, false, true))
        listener.onNotificationPosted(source(2000))
        assertTrue(manager.activeNotifications.isEmpty())
        assertTrue(listener.activeNotifications.any { it.key == summary.key })
    }

    @Test
    fun `own removal allows next identical timestamp message`() {
        connect()
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
        connect()
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
        connect()
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
