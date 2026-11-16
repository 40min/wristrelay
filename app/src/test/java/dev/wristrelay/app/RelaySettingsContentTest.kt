package dev.wristrelay.app

import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.wristrelay.app.settings.FaultingSettingsDataStore
import dev.wristrelay.app.settings.RelaySettings
import dev.wristrelay.app.settings.SettingsGate
import dev.wristrelay.app.settings.SettingsStore
import dev.wristrelay.app.ui.RelaySettingsContent
import dev.wristrelay.app.ui.WristRelayTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RelaySettingsContentTest {
    @get:Rule
    val compose = createComposeRule()

    @Before
    fun resetGate() {
        SettingsGate.update(RelaySettings())
    }

    @Test
    fun failedReadShowsErrorWithoutSettingsControls() {
        showSettings(FaultingSettingsDataStore(failFirstRead = true))

        compose.onNodeWithText("Couldn’t load saved settings.").assertExists()
        relaySwitch().assertDoesNotExist()
    }

    @Test
    fun failedWriteShowsErrorAndKeepsSavedSwitchOff() {
        showSettings(FaultingSettingsDataStore())

        relaySwitch().assertIsOff().performClick()

        compose.onNodeWithText("Couldn’t save the change. Try again.").assertExists()
        relaySwitch().assertIsOff()
        assertEquals(RelaySettings(), SettingsGate.current)
    }

    private fun relaySwitch() = compose.onNodeWithContentDescription("Show Telegram messages on the watch")

    private fun showSettings(dataStore: FaultingSettingsDataStore) {
        val store = SettingsStore(dataStore, CoroutineScope(Dispatchers.Main))
        compose.setContent { WristRelayTheme { RelaySettingsContent(store) } }
    }
}
