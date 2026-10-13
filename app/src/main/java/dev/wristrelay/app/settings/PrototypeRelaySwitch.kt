package dev.wristrelay.app.settings

/**
 * Task 0.2 prototype switch (manual wiring; DataStore persistence is task
 * 2.3). Default off. While on, extracted Telegram messages are relayed to
 * independent notifications.
 */
object PrototypeRelaySwitch {
    @Volatile
    var enabled: Boolean = false
}
