package dev.wristrelay.app.debug

import android.app.Notification
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.os.Handler
import android.os.Looper
import android.service.notification.StatusBarNotification
import androidx.core.os.BundleCompat
import dev.wristrelay.app.source.spotify.SPOTIFY_PACKAGE
import dev.wristrelay.app.source.spotify.containsSpotifyAdLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/** Process-local classifications. No raw media text leaves the callback. */
internal object SpotifyProbe {
    const val MAX_EVENTS = 256
    const val TAIL_EVENTS = 5

    private val enabledState = MutableStateFlow(false)
    val enabled = enabledState.asStateFlow()
    private val eventState = MutableStateFlow<List<String>>(emptyList())
    val events = eventState.asStateFlow()
    private var sequence = 0

    fun setEnabled(enabled: Boolean) {
        if (!enabled) {
            eventState.value = emptyList()
            sequence = 0
        }
        enabledState.value = enabled
    }

    fun record(event: String) {
        if (enabled.value) {
            eventState.value = (eventState.value + "${++sequence}. $event").takeLast(MAX_EVENTS)
        }
    }

    fun classify(value: CharSequence?): String = when {
        value.isNullOrBlank() -> "missing"
        "Advertisement".equals(value.toString().trim(), ignoreCase = true) -> "ad"
        containsSpotifyAdLabel(value) -> "contains-ad"
        else -> "other"
    }
}

internal class SpotifyMediaProbe(private val context: Context) {
    private var notificationKey: String? = null
    private var controller: MediaController? = null
    private var callback: MediaController.Callback? = null

    fun posted(sbn: StatusBarNotification, event: String) {
        if (!SpotifyProbe.enabled.value || sbn.packageName != SPOTIFY_PACKAGE) return
        val extras = sbn.notification.extras
        val token = BundleCompat.getParcelable(extras, Notification.EXTRA_MEDIA_SESSION, MediaSession.Token::class.java)
        val textLines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
        with(SpotifyProbe) {
            record(
                "$event · title=${classify(extras.getCharSequence(Notification.EXTRA_TITLE))}" +
                    " text=${classify(extras.getCharSequence(Notification.EXTRA_TEXT))}" +
                    " subtext=${classify(extras.getCharSequence(Notification.EXTRA_SUB_TEXT))}" +
                    " titleBig=${classify(extras.getCharSequence(Notification.EXTRA_TITLE_BIG))}" +
                    " bigText=${classify(extras.getCharSequence(Notification.EXTRA_BIG_TEXT))}" +
                    " summary=${classify(extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT))}" +
                    " info=${classify(extras.getCharSequence(Notification.EXTRA_INFO_TEXT))}" +
                    " lines=${classify(textLines?.joinToString(" "))}" +
                    " ticker=${classify(sbn.notification.tickerText)}" +
                    " session=${if (token == null) "missing" else "present"}",
            )
        }
        if (token == null) {
            if (notificationKey == sbn.key) stop()
            return
        }
        notificationKey = sbn.key
        if (controller?.sessionToken != token) bind(token)
    }

    fun removed(sbn: StatusBarNotification) {
        if (!SpotifyProbe.enabled.value || sbn.packageName != SPOTIFY_PACKAGE) return
        SpotifyProbe.record("Notification removed")
        if (notificationKey == sbn.key) stop()
    }

    fun stop() {
        unbind()
        notificationKey = null
    }

    private fun unbind() {
        callback?.let { controller?.unregisterCallback(it) }
        callback = null
        controller = null
    }

    private fun bind(token: MediaSession.Token) {
        unbind()
        try {
            val media = MediaController(context, token)
            if (media.packageName != SPOTIFY_PACKAGE) {
                SpotifyProbe.record("Media session belongs to another package")
                return
            }
            val listener = object : MediaController.Callback() {
                override fun onMetadataChanged(metadata: MediaMetadata?) {
                    if (callback === this) recordMetadata("Media metadata changed", metadata)
                }

                override fun onSessionDestroyed() {
                    if (callback !== this) return
                    SpotifyProbe.record("Media session ended")
                    stop()
                }
            }
            controller = media
            callback = listener
            media.registerCallback(listener, Handler(Looper.getMainLooper()))
            recordMetadata("Media session snapshot", media.metadata)
        } catch (_: SecurityException) {
            unbind()
            SpotifyProbe.record("Media session access denied")
        }
    }

    private fun recordMetadata(event: String, metadata: MediaMetadata?) {
        val description = metadata?.description
        with(SpotifyProbe) {
            record(
                "$event · title=${classify(metadata?.getText(MediaMetadata.METADATA_KEY_TITLE))}" +
                    " displayTitle=${classify(metadata?.getText(MediaMetadata.METADATA_KEY_DISPLAY_TITLE))}" +
                    " artist=${classify(metadata?.getText(MediaMetadata.METADATA_KEY_ARTIST))}" +
                    " album=${classify(metadata?.getText(MediaMetadata.METADATA_KEY_ALBUM))}" +
                    " albumArtist=${classify(metadata?.getText(MediaMetadata.METADATA_KEY_ALBUM_ARTIST))}" +
                    " displaySubtitle=${classify(metadata?.getText(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE))}" +
                    " displayDescription=${classify(metadata?.getText(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION))}" +
                    " description.title=${classify(description?.title)}" +
                    " description.subtitle=${classify(description?.subtitle)}" +
                    " description.description=${classify(description?.description)}",
            )
        }
    }
}

internal class SpotifyProbeCoordinator(
    context: Context,
    private val activeNotifications: () -> Array<StatusBarNotification>?,
) {
    private val mediaProbe = SpotifyMediaProbe(context)
    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        stop()
        job = scope.launch {
            SpotifyProbe.enabled.collect { enabled ->
                if (enabled) {
                    SpotifyProbe.record("Listener connected. Reading active Spotify notifications.")
                    try {
                        activeNotifications()?.forEach { mediaProbe.posted(it, "Notification snapshot") }
                    } catch (_: SecurityException) {
                        SpotifyProbe.record("Notification access denied")
                    }
                } else {
                    mediaProbe.stop()
                }
            }
        }
    }

    fun notificationChanged(sbn: StatusBarNotification, posted: Boolean) {
        if (posted) mediaProbe.posted(sbn, "Notification posted or updated")
        else mediaProbe.removed(sbn)
    }

    fun stop() {
        job?.cancel()
        job = null
        mediaProbe.stop()
    }
}
