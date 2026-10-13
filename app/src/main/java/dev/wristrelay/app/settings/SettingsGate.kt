package dev.wristrelay.app.settings

/**
 * Process-lifetime in-memory mirror of the persisted relay settings. The
 * notification listener reads it synchronously from binder callbacks while
 * a collector keeps it aligned with the DataStore ( manual wiring, no DI).
 */
object SettingsGate {
    @Volatile
    var current: RelaySettings = RelaySettings()
        private set

    fun update(settings: RelaySettings) {
        current = settings
    }

    /** Relay proceeds only when master and explicit Telegram gates both pass. */
    fun relayingAllowed(): Boolean =
        current.masterEnabled && current.telegramEnabled
}
