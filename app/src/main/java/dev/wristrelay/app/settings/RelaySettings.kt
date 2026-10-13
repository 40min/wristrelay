package dev.wristrelay.app.settings

/**
 * Persisted relay configuration; the only data the DataStore stores.
 * Both gates are off by default, so relay never runs without explicit
 * owner action: no Telegram relay until this toggle pair is on.
 */
data class RelaySettings(
    val masterEnabled: Boolean = false,
    val telegramEnabled: Boolean = false,
)
