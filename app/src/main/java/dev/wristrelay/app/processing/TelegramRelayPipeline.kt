package dev.wristrelay.app.processing

import dev.wristrelay.app.model.RelayMessage
import dev.wristrelay.app.relay.RelayPublisher

/**
 * Binds message detection to publication for one Telegram snapshot.
 * Detection never advances the tracker on its own: the cursor moves only
 * through the successfully submitted prefix (Cf7Lga4n2Sx7), so a full
 * publication failure retries the same snapshot on the next qualifying
 * event and a partial failure retries only the missing copies.
 */
class TelegramRelayPipeline(
    private val tracker: MessageTracker,
    private val publisher: dev.wristrelay.app.relay.RelayPublisher,
) {

    fun run(conversationKey: String, snapshot: List<RelayMessage>?): RelayResult =
        when {
            snapshot == null -> RelayResult.Unextractable
            else -> try {
                runDetection(conversationKey, snapshot)
            } catch (failure: SecurityException) {
                RelayResult.Failed("publish denied: ${failure.javaClass.simpleName}")
            } catch (failure: IllegalArgumentException) {
                RelayResult.Failed("publish rejected: ${failure.javaClass.simpleName}")
            }
        }

    private fun runDetection(
        conversationKey: String,
        snapshot: List<RelayMessage>,
    ): RelayResult {
        val fresh = tracker.newMessages(conversationKey, snapshot)
        if (fresh.isEmpty()) return RelayResult.Duplicate
        val publication = publisher.publish(fresh)
        // Full failure: 0 submitted — the cursor stays put and the same
        // snapshot retries next event. Partial failure: only the missing
        // copies re-emit. Telegram originals wait regardless (ADR-014);
        // the listener suspends hiding for the session on any failure.
        tracker.advanceThroughSubmitted(conversationKey, snapshot, fresh, publication.submittedCount)
        return when {
            publication.fullySubmitted -> RelayResult.Published(publication.submittedIdentities)
            publication.submittedCount > 0 -> RelayResult.Failed(
                "partial submission ${publication.submittedCount}/${fresh.size}" +
                    publication.failureReason?.let { ": $it" }.orEmpty(),
            )
            else -> RelayResult.Failed(publication.failureReason ?: "publication not confirmed")
        }
    }
}

sealed interface RelayResult {
    data class Published(val identities: List<Pair<String, Int>>) : RelayResult {
        val count: Int get() = identities.size
    }
    data object Duplicate : RelayResult
    data object Unextractable : RelayResult
    data class Failed(val reason: String) : RelayResult
}
