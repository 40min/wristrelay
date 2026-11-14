package dev.wristrelay.app.settings

/**
 * Persisted relay configuration; the only data the DataStore stores.
 * Both gates are off by default, so relay never runs without explicit
 * owner action: no Telegram relay until this toggle pair is on.
 */
data class RelaySettings(
    val masterEnabled: Boolean = false,
    val telegramEnabled: Boolean = false,
    val hideTelegramOriginals: Boolean = false,
    val autoClearEnabled: Boolean = false,
    val autoClearMinutes: Int = 60,
) {
    init {
        require(autoClearMinutes in 1..MAX_AUTO_CLEAR_MINUTES)
    }

    val timeoutMillis: Long
        get() = if (autoClearEnabled) autoClearMinutes * 60_000L else 0L

    companion object {
        const val MAX_AUTO_CLEAR_MINUTES = 10_080 // Seven days.

        fun durationMinutes(amount: String, hours: Boolean): Int? {
            val value = amount.toIntOrNull() ?: return null
            val maximum = if (hours) MAX_AUTO_CLEAR_MINUTES / 60 else MAX_AUTO_CLEAR_MINUTES
            return value.takeIf { it in 1..maximum }?.let { if (hours) it * 60 else it }
        }
    }
}
