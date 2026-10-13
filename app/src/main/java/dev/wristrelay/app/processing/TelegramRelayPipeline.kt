package dev.wristrelay.app.processing

import dev.wristrelay.app.model.RelayMessage
import dev.wristrelay.app.relay.RelayNotificationPublisher

/**
 * Binds message detection to publication for one Telegram snapshot. One step
 * of the task 0.2 pipeline; grows into the production relay in Epic 2.
 */
class TelegramRelayPipeline(
    private val tracker: MessageTracker,
    private val publisher: RelayNotificationPublisher,
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
        return when {
            fresh.isEmpty() -> RelayResult.Duplicate
            else -> {
                publisher.publish(fresh)
                RelayResult.Published(fresh.size)
            }
        }
    }
}

sealed interface RelayResult {
    data class Published(val count: Int) : RelayResult
    data object Duplicate : RelayResult
    data object Unextractable : RelayResult
    data class Failed(val reason: String) : RelayResult
}
