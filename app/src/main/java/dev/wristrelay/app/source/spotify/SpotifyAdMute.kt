package dev.wristrelay.app.source.spotify

import android.media.AudioManager
import android.os.Handler

internal const val SPOTIFY_PACKAGE = "com.spotify.music"
private val advertisementLabel = Regex("\\bAdvertisement\\b", RegexOption.IGNORE_CASE)

internal fun containsSpotifyAdLabel(text: CharSequence): Boolean = advertisementLabel.containsMatchIn(text)

/** One mute per observed ad run. All calls and the restore callback run on the listener's main thread. */
internal class SpotifyAdMute(
    private val handler: Handler,
    private val readVolume: () -> Int,
    private val writeVolume: (Int) -> Unit,
) {
    constructor(handler: Handler, audio: AudioManager) : this(
        handler,
        {
            if (audio.isVolumeFixed || audio.isStreamMute(AudioManager.STREAM_MUSIC)) 0
            else audio.getStreamVolume(AudioManager.STREAM_MUSIC)
        },
        { audio.setStreamVolume(AudioManager.STREAM_MUSIC, it, 0) },
    )

    private var adKey: String? = null
    private var previousVolume: Int? = null
    private val timeout = Runnable { restore() }

    fun posted(key: String, text: CharSequence?, timeoutSeconds: Int) {
        if (!text.isNullOrBlank()) {
            if (!containsSpotifyAdLabel(text)) {
                if (adKey == key) stop()
            } else if (adKey != null) {
                // Spotify may replace its ongoing notification while an ad is active.
                // Follow the replacement without extending the original deadline.
                adKey = key
            } else {
                startMute(key, timeoutSeconds)
            }
        }
    }

    private fun startMute(key: String, timeoutSeconds: Int) {
        adKey = key
        try {
            val volume = readVolume()
            if (volume <= 0) return
            writeVolume(0)
            previousVolume = volume
            handler.postDelayed(timeout, timeoutSeconds * 1000L)
        } catch (_: SecurityException) {
            // Device audio policy can deny a stream volume change.
        }
    }

    fun stop() {
        adKey = null
        restore()
    }

    fun removed(key: String) {
        if (adKey == key) stop()
    }

    private fun restore() {
        handler.removeCallbacks(timeout)
        val volume = previousVolume ?: return
        previousVolume = null
        try {
            if (readVolume() == 0) writeVolume(volume)
        } catch (_: SecurityException) {
            // Android can deny restoration after an audio policy change.
        }
    }
}
