package dev.wristrelay.app.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SettingsGateTest {

    @Before
    fun reset() {
        SettingsGate.update(RelaySettings())
    }

    @Test
    fun `default settings disallow relaying`() {
        assertFalse(SettingsGate.relayingAllowed())
    }

    @Test
    fun `master alone is not enough`() {
        SettingsGate.update(RelaySettings(masterEnabled = true))
        assertFalse(SettingsGate.relayingAllowed())
    }

    @Test
    fun `telegram alone is not enough`() {
        SettingsGate.update(RelaySettings(telegramEnabled = true))
        assertFalse(SettingsGate.relayingAllowed())
    }

    @Test
    fun `both switches allow relaying`() {
        SettingsGate.update(RelaySettings(masterEnabled = true, telegramEnabled = true))
        assertTrue(SettingsGate.relayingAllowed())
    }
}
