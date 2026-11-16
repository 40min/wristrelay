package dev.wristrelay.app.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.flow

internal class FaultingSettingsDataStore(
    private val saved: Preferences = emptyPreferences(),
    private val failFirstRead: Boolean = false,
) : DataStore<Preferences> {
    val failRead = CompletableDeferred<Unit>()

    override val data = flow {
        if (!failFirstRead) {
            emit(saved)
            failRead.await()
        }
        throw IOException("Read failed")
    }

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        throw IOException("Write failed")
    }
}
