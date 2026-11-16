package dev.wristrelay.app.source.spotify

import android.os.Handler
import android.os.Looper
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpotifyAdMuteTest {
    private var volume = 7
    private val writes = mutableListOf<Int>()
    private val looper = Looper.getMainLooper()
    private val mute = SpotifyAdMute(Handler(looper), { volume }, {
        writes += it
        volume = it
    })

    @Test
    fun `only a nonblank update to the matching notification restores early`() {
        mute.posted("spotify", "Advertisement · Spotify", 30)
        mute.posted("spotify", null, 30)
        mute.posted("spotify", "  ", 30)
        mute.posted("other-key", "Artist", 30)
        assertEquals(listOf(0), writes)

        mute.posted("spotify", "Artist", 30)
        shadowOf(looper).idleFor(Duration.ofSeconds(30))
        assertEquals(listOf(0, 7), writes)
    }

    @Test
    fun `timeout survives duplicate updates and rearms only after track text`() {
        mute.posted("spotify", "Advertisement", 30)
        shadowOf(looper).idleFor(Duration.ofSeconds(29))
        mute.posted("spotify", "ADVERTISEMENT", 60)
        assertEquals(0, volume)

        shadowOf(looper).idleFor(Duration.ofSeconds(1))
        mute.posted("spotify", "Advertisement", 30)
        assertEquals(listOf(0, 7), writes)

        mute.posted("spotify", "Artist", 30)
        mute.posted("spotify", "Advertisement", 5)
        shadowOf(looper).idleFor(Duration.ofSeconds(5))
        assertEquals(listOf(0, 7, 0, 7), writes)
    }

    @Test
    fun `removing the tracked notification restores and allows a replacement to mute`() {
        mute.posted("old-key", "Advertisement", 30)
        mute.posted("replacement-key", "Advertisement", 30)
        mute.removed("old-key")
        assertEquals(listOf(0), writes)

        mute.removed("replacement-key")
        assertEquals(listOf(0, 7), writes)
        mute.posted("replacement-key", "Advertisement", 30)
        assertEquals(listOf(0, 7, 0), writes)
    }

    @Test
    fun `unrelated removal does not restore the active ad`() {
        mute.posted("spotify", "Advertisement", 30)
        mute.removed("unrelated-key")

        assertEquals(0, volume)
        assertEquals(listOf(0), writes)
    }

    @Test
    fun `turning off the feature restores once and cancels the deadline`() {
        mute.posted("spotify", "Advertisement", 30)
        mute.stop()
        shadowOf(looper).idleFor(Duration.ofSeconds(30))
        assertEquals(listOf(0, 7), writes)
    }

    @Test
    fun `restore preserves user volume changes and leaves existing silence alone`() {
        mute.posted("spotify", "Advertisement", 30)
        volume = 3
        mute.posted("spotify", "Artist", 30)
        shadowOf(looper).idleFor(Duration.ofSeconds(30))
        assertEquals(3, volume)
        assertEquals(listOf(0), writes)

        volume = 0
        mute.posted("spotify", "Advertisement", 30)
        shadowOf(looper).idleFor(Duration.ofSeconds(30))
        assertEquals(listOf(0), writes)
    }
}
