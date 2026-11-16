package dev.wristrelay.app.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.settingsDataStore by preferencesDataStore(name = "wristrelay_settings")

/**
 * Persists relay settings only (PRIVACY.md: no message history, no
 * telemetry). DataStore is injected so Robolectric tests can point at an
 * isolated file; production uses the module singleton.
 */
class SettingsStore(
    private val dataStore: DataStore<Preferences>,
) {
    constructor(context: Context) : this(context.settingsDataStore)

    val flow: Flow<RelaySettings> = dataStore.data.map { prefs: Preferences ->
        RelaySettings(
            masterEnabled = prefs[MASTER] ?: false,
            telegramEnabled = prefs[TELEGRAM] ?: false,
            hideTelegramOriginals = prefs[HIDE_ORIGINALS] ?: false,
            spotifyAdMuteEnabled = prefs[SPOTIFY_AD_MUTE] ?: false,
            spotifyAdMuteSeconds = prefs[SPOTIFY_AD_MUTE_SECONDS]
                ?.takeIf { it in 1..RelaySettings.MAX_SPOTIFY_AD_MUTE_SECONDS }
                ?: RelaySettings.DEFAULT_SPOTIFY_AD_MUTE_SECONDS,
            autoClearEnabled = prefs[AUTO_CLEAR] ?: false,
            autoClearMinutes = prefs[AUTO_CLEAR_MINUTES]
                ?.takeIf { it in 1..RelaySettings.MAX_AUTO_CLEAR_MINUTES } ?: 60,
        )
    }

    suspend fun current(): RelaySettings = flow.first()

    suspend fun save(settings: RelaySettings) {
        dataStore.edit { prefs ->
            prefs[SPOTIFY_AD_MUTE] = settings.spotifyAdMuteEnabled
            prefs[SPOTIFY_AD_MUTE_SECONDS] = settings.spotifyAdMuteSeconds
            prefs[MASTER] = settings.masterEnabled
            prefs[TELEGRAM] = settings.telegramEnabled
            prefs[HIDE_ORIGINALS] = settings.hideTelegramOriginals
            prefs[AUTO_CLEAR] = settings.autoClearEnabled
            prefs[AUTO_CLEAR_MINUTES] = settings.autoClearMinutes
        }
    }

    private companion object {
        val AUTO_CLEAR = booleanPreferencesKey("auto_clear_enabled")
        val AUTO_CLEAR_MINUTES = intPreferencesKey("auto_clear_minutes")
        val HIDE_ORIGINALS = booleanPreferencesKey("hide_telegram_originals")
        val SPOTIFY_AD_MUTE = booleanPreferencesKey("spotify_ad_mute_enabled")
        val SPOTIFY_AD_MUTE_SECONDS = intPreferencesKey("spotify_ad_mute_seconds")
        val MASTER = booleanPreferencesKey("master_enabled")
        val TELEGRAM = booleanPreferencesKey("telegram_enabled")
    }
}
