package dev.wristrelay.app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

val Context.settingsDataStore by preferencesDataStore(name = "wristrelay_settings")

/**
 * Persists relay settings only (PRIVACY.md: no message history, no
 * telemetry). DataStore is injected so Robolectric tests can point at an
 * isolated file; production uses the module singleton.
 */
class SettingsStore(
    private val dataStore: DataStore<Preferences>,
    private val observerScope: CoroutineScope,
) {
    constructor(context: Context) : this(
        context.settingsDataStore,
        CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    val flow: Flow<RelaySettings> = dataStore.data.map { prefs: Preferences ->
        RelaySettings(
            masterEnabled = prefs[MASTER] ?: false,
            telegramEnabled = prefs[TELEGRAM] ?: false,
        )
    }

    suspend fun current(): RelaySettings = flow.first()

    suspend fun save(settings: RelaySettings) {
        dataStore.edit { prefs ->
            prefs[MASTER] = settings.masterEnabled
            prefs[TELEGRAM] = settings.telegramEnabled
        }
    }

    /**
     * Keeps the synchronous [SettingsGate] mirror aligned with persisted
     * settings; returns the job so the listener can cancel it.
     */
    fun observe(onChange: (RelaySettings) -> Unit) {
        observerScope.launch {
            flow.collect(onChange)
        }
    }

    private companion object {
        val MASTER = booleanPreferencesKey("master_enabled")
        val TELEGRAM = booleanPreferencesKey("telegram_enabled")
    }
}
