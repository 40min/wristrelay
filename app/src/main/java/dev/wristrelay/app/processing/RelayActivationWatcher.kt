package dev.wristrelay.app.processing

import dev.wristrelay.app.settings.RelaySettings

/**
 * Fires the re-baseline callback exactly when relay activation transitions
 * off → on, so conversations already present in the status bar when the
 * owner enables relay are never replayed as fresh messages when the next
 * POSTED callback arrives. Repeated updates while active do nothing.
 */
class RelayActivationWatcher(private val onActivated: () -> Unit) {

    private var active = false

    fun update(settings: RelaySettings) {
        val nowActive = settings.masterEnabled && settings.telegramEnabled
        if (nowActive && !active) onActivated()
        active = nowActive
    }
}
