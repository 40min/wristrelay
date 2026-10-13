package dev.wristrelay.app.processing

import dev.wristrelay.app.settings.RelaySettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RelayActivationWatcherTest {

    private var fired = 0

    private val watcher =
        RelayActivationWatcher { fired++ }

    @Test
    fun `activation fires re-baseline callback exactly on off to on`() {
        watcher.update(RelaySettings())
        assertTrue(fired == 0)
        watcher.update(RelaySettings(masterEnabled = true))
        assertTrue(fired == 0)
        watcher.update(RelaySettings(masterEnabled = true, telegramEnabled = true))
        assertTrue(fired == 1)
        watcher.update(RelaySettings(masterEnabled = true, telegramEnabled = true))
        assertTrue(fired == 1)
    }

    @Test
    fun `deactivation then reactivation fires again`() {
        watcher.update(RelaySettings(masterEnabled = true, telegramEnabled = true))
        watcher.update(RelaySettings())
        assertTrue(fired == 1)
        watcher.update(RelaySettings(masterEnabled = true, telegramEnabled = true))
        assertTrue(fired == 2)
    }

    @Test
    fun `flush of both gates does not fire at once while inactive`() {
        watcher.update(RelaySettings(masterEnabled = true, telegramEnabled = true))
        assertTrue(fired == 1)
        watcher.update(RelaySettings(masterEnabled = true, telegramEnabled = false))
        assertFalse(watcher.toString().contains("active"))
    }
}
