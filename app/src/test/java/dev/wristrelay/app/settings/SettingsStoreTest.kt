package dev.wristrelay.app.settings

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
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

    private fun isolatedStore(): SettingsStore {
        context.filesDir.mkdirs()
        val file = File(context.filesDir, "${UUID.randomUUID()}.preferences_pb")
        val scope = CoroutineScope(Dispatchers.IO + Job())
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
    fun `saving off settings persists off`() = runBlocking {
        val store = isolatedStore()
        store.save(RelaySettings(masterEnabled = true, telegramEnabled = true))
        store.save(RelaySettings())
        assertEquals(RelaySettings(), store.current())
    }

    @Test
    fun `observer keeps the synchronous gate aligned`() = runBlocking {
        val store = isolatedStore()
        store.observe { SettingsGate.update(it) }
        store.save(RelaySettings(masterEnabled = true, telegramEnabled = true))

        val deadline = System.currentTimeMillis() + 2_000
        while (!SettingsGate.relayingAllowed() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        assertTrue(SettingsGate.relayingAllowed())
    }
}
