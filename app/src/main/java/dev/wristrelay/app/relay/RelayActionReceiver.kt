package dev.wristrelay.app.relay

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import dev.wristrelay.app.source.telegram.TelegramExtractor
import java.util.UUID

/** System-held action tokens carry their own routing state, including after process death. */
class RelayActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val handoff = parse(context, intent) ?: return
        if (!submittedResults(intent)) return
        // RemoteInput transports text, data results and result-source metadata in ClipData.
        // Do not leak the wrapper's target/identity extras into Telegram's operation.
        val results = Intent().apply { clipData = intent.clipData }
        try {
            handoff.target.send(context, 0, results)
            val manager = context.getSystemService(NotificationManager::class.java)
            val active = manager?.activeNotifications?.firstOrNull {
                it.id == handoff.id && it.tag == handoff.tag
            }
            if (active?.notification?.actions?.any {
                it.extras.getString(MARKER) == handoff.uri.toString()
            } == true) {
                manager.cancel(handoff.tag, handoff.id)
            }
        } catch (_: PendingIntent.CanceledException) {
            // Leave the copy available when Telegram's token has expired.
        } catch (_: SecurityException) {
            // Rejected handoff is not a completed user action.
        }
    }

    private data class Handoff(val target: PendingIntent, val tag: String, val id: Int, val uri: Uri)

    private fun parse(context: Context, intent: Intent): Handoff? {
        @Suppress("DEPRECATION")
        val target = intent.getParcelableExtra<PendingIntent>(TARGET)
        val source = intent.getStringExtra(SOURCE)
        val tag = intent.getStringExtra(TAG)
        val id = intent.getIntExtra(ID, -1)
        val uri = intent.data
        val sourceAllowed = source != null && TelegramExtractor.matches(source)
        val targetAllowed = target != null && target.creatorPackage == source && nonActivityTarget(target)
        val identityValid = tag == "wristrelay-msg-$id" && id >= FIRST_ID
        val ownerAllowed = sourceAllowed && targetAllowed
        val uriValid = validUri(context, uri, id)
        return if (ownerAllowed && identityValid && uriValid) {
            Handoff(checkNotNull(target), checkNotNull(tag), id, checkNotNull(uri))
        } else {
            null
        }
    }

    private fun validUri(context: Context, uri: Uri?, id: Int): Boolean {
        val originValid = uri?.scheme == SCHEME && uri.authority == context.packageName
        val segmentsValid = uri?.pathSegments?.size == SEGMENT_COUNT && uri.pathSegments[0] == id.toString()
        return originValid && segmentsValid
    }

    private fun submittedResults(intent: Intent): Boolean {
        val keys = intent.getStringArrayListExtra(INPUT_KEYS) ?: return false
        val text = RemoteInput.getResultsFromIntent(intent)
        return keys.isEmpty() || keys.any { key ->
            text?.getCharSequence(key) != null || !RemoteInput.getDataResultsFromIntent(intent, key).isNullOrEmpty()
        }
    }

    companion object {
        private const val INPUT_KEYS = "relay.action.inputKeys"
        private const val MARKER = "relay.action.identity"
        private const val TARGET = "relay.action.target"
        private const val SOURCE = "relay.action.source"
        private const val TAG = "relay.action.tag"
        private const val ID = "relay.action.id"
        private const val SCHEME = "wristrelay-action"
        private const val SEGMENT_COUNT = 3
        private const val FIRST_ID = 0x5252_0000

        private fun nonActivityTarget(target: PendingIntent?): Boolean =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && target != null && !target.isActivity

        fun wrap(
            context: Context,
            action: Notification.Action,
            sourcePackage: String?,
            id: Int,
            index: Int,
        ): Notification.Action {
            val target = action.actionIntent
            val inputs = action.remoteInputs?.toList().orEmpty() + action.dataOnlyRemoteInputs?.toList().orEmpty()
            val hasInput = inputs.isNotEmpty()
            val englishFallback = action.semanticAction == Notification.Action.SEMANTIC_ACTION_NONE &&
                action.title?.toString()?.trim().equals("Mark as read", ignoreCase = true)
            val markRead = action.semanticAction == Notification.Action.SEMANTIC_ACTION_MARK_AS_READ || englishFallback
            val targetAllowed = nonActivityTarget(target) && sourcePackage != null
            // Activity and compose-opening/unknown actions stay direct; only known completion is observed.
            if (!targetAllowed || !(hasInput || markRead)) return action
            val intent = Intent(context, RelayActionReceiver::class.java).apply {
                data = Uri.Builder().scheme(SCHEME).authority(context.packageName)
                    .appendPath(id.toString()).appendPath(index.toString())
                    .appendPath(UUID.randomUUID().toString()).build()
                putExtra(TARGET, target)
                putExtra(SOURCE, sourcePackage)
                putExtra(TAG, "wristrelay-msg-$id")
                putExtra(ID, id)
                putStringArrayListExtra(INPUT_KEYS, ArrayList(inputs.map { it.resultKey }))
            }
            val flags = if (hasInput) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_IMMUTABLE
            val wrapper = PendingIntent.getBroadcast(context, 0, intent, flags)
            return Notification.Action.Builder(action)
                .setContextual(action.isContextual)
                .apply {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        setAuthenticationRequired(action.isAuthenticationRequired)
                    }
                }
                .addExtras(Bundle().apply { putString(MARKER, intent.data.toString()) })
                .build().apply { actionIntent = wrapper }
        }
    }
}
