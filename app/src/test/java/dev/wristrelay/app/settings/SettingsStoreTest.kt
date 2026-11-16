package dev.wristrelay.app.settings

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * DataStore persistence is verified on an isolated file per test so the
 * module singleton used in production is not shared with unit state.
 * Restart persistence itself is a DataStore file contract; the unit suite
 * covers the round trip and the synchronous-gate mirror.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SettingsStoreTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun resetGate() {
        SettingsGate.update(RelaySettings())
    }

    private fun isolatedStore(
        scope: CoroutineScope = CoroutineScope(Dispatchers.IO + Job()),
    ): SettingsStore {
        context.filesDir.mkdirs()
        val file = File(context.filesDir, "${UUID.randomUUID()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        return SettingsStore(dataStore, scope)
    }

    @Test
    fun `defaults are both off`() = runBlocking {
        assertEquals(RelaySettings(), isolatedStore().current())
    }

    @Test
    fun `save round trip keeps both switches`() = runBlocking {
        val store = isolatedStore()
        store.save(RelaySettings(masterEnabled = true, telegramEnabled = true))
        assertEquals(
            RelaySettings(masterEnabled = true, telegramEnabled = true),
            store.current(),
        )
    }

    @Test
    fun `Spotify ad mute saves independently with a configurable timeout`() = runBlocking {
        val settings = RelaySettings(spotifyAdMuteEnabled = true, spotifyAdMuteSeconds = 45)
        val store = isolatedStore()
        store.save(settings)
        assertEquals(settings, store.current())
    }

    @Test
    fun `saving off settings persists off`() = runBlocking {
        val store = isolatedStore()
        store.save(RelaySettings(masterEnabled = true, telegramEnabled = true))
        store.save(RelaySettings())
        assertEquals(RelaySettings(), store.current())
    }

    @Test
    fun `observer keeps the synchronous gate aligned`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = isolatedStore(scope)
            val expectedSettingsObserved = CompletableDeferred<Unit>()
            store.observe {
                SettingsGate.update(it)
                if (it.masterEnabled && it.telegramEnabled) expectedSettingsObserved.complete(Unit)
            }
            store.save(RelaySettings(masterEnabled = true, telegramEnabled = true))

            withTimeout(2_000) { expectedSettingsObserved.await() }
            assertTrue(SettingsGate.relayingAllowed())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `hide originals persists independently when relay is disabled`() = runBlocking {
        val store = isolatedStore()
        store.save(RelaySettings(hideTelegramOriginals = true))
        assertEquals(RelaySettings(hideTelegramOriginals = true), store.current())
    }

    @Test
    fun `legacy two boolean preferences default hiding off`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO + Job())
        val file = File(context.filesDir, "${UUID.randomUUID()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        dataStore.edit { prefs ->
            prefs[androidx.datastore.preferences.core.booleanPreferencesKey("master_enabled")] = true
            prefs[androidx.datastore.preferences.core.booleanPreferencesKey("telegram_enabled")] = true
        }
        assertEquals(RelaySettings(true, true, false), SettingsStore(dataStore, scope).current())
    }

    @Test
    fun `expiry persists independently with configured minutes`() = runBlocking {
        val store = isolatedStore()
        val settings = RelaySettings(autoClearEnabled = true, autoClearMinutes = 15)
        store.save(settings)
        assertEquals(settings, store.current())
        store.save(settings.copy(autoClearEnabled = false))
        assertEquals(15, store.current().autoClearMinutes)
        assertEquals(0L, store.current().timeoutMillis)
    }

    @Test
    fun `duration validates units bounds and overflow`() {
        assertEquals(false, RelaySettings().autoClearEnabled)
        assertEquals(60, RelaySettings().autoClearMinutes)
        assertEquals(15, RelaySettings.durationMinutes("15", false))
        assertEquals(120, RelaySettings.durationMinutes("2", true))
        assertEquals(10080, RelaySettings.durationMinutes("168", true))
        assertEquals(10080, RelaySettings.durationMinutes("10080", false))
        listOf("", "0", "-1", "1.5", "abc", "2147483648").forEach {
            assertEquals(null, RelaySettings.durationMinutes(it, false))
            assertEquals(null, RelaySettings.durationMinutes(it, true))
        }
        assertEquals(null, RelaySettings.durationMinutes("169", true))
        assertEquals(null, RelaySettings.durationMinutes("10081", false))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `nonpositive persisted duration cannot be constructed`() {
        RelaySettings(autoClearMinutes = 0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `oversized persisted duration cannot be constructed`() {
        RelaySettings(autoClearMinutes = 10081)
    }

    @Test
    fun `corrupt duration falls back to safe one hour`() = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO + Job())
        val file = File(context.filesDir, "${UUID.randomUUID()}.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(scope = scope) { file }
        dataStore.edit { prefs ->
            prefs[androidx.datastore.preferences.core.intPreferencesKey("auto_clear_minutes")] = -1
        }
        assertEquals(RelaySettings(), SettingsStore(dataStore, scope).current())
    }

}
