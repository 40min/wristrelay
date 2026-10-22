package dev.wristrelay.app.processing

import dev.wristrelay.app.model.RelayMessage
import dev.wristrelay.app.relay.RelayPublisher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pipeline-level behavior over an in-memory recording publisher: cumulative
 * snapshots emit exactly the new suffix, repeated callbacks and edits stay
 * silent, bursts and multiple conversations do not lose or duplicate
 * entries. Detection semantics owned by MessageTracker (covered there);
 * this suite proves the binding and relay-result accounting.
 */
class TelegramRelayPipelineTest {

    private class RecordingPublisher : RelayPublisher {
        val published = mutableListOf<RelayMessage>()
        override fun publish(messages: List<RelayMessage>): Boolean {
            published.addAll(messages)
            return true
        }
    }

    private fun message(text: String, conversation: String = "conv-one") = RelayMessage(
        conversationTitle = conversation,
        isGroup = false,
        sender = "sender-a",
        text = text,
    )

    @Test
    fun `cumulative snapshots emit exactly three in order`() {
        val tracker = MessageTracker()
        val publisher = RecordingPublisher()
        val pipeline = TelegramRelayPipeline(tracker, publisher)

        pipeline.run("key-a", listOf(message("one")))
        pipeline.run("key-a", listOf(message("one"), message("two")))
        pipeline.run("key-a", listOf(message("one"), message("two"), message("three")))

        assertEquals(listOf("one", "two", "three"), publisher.published.map { it.text })
    }

    @Test
    fun `two identical texts remain distinct and both emitted`() {
        val tracker = MessageTracker()
        val publisher = RecordingPublisher()
        val pipeline = TelegramRelayPipeline(tracker, publisher)

        pipeline.run("key-a", listOf(message("ok")))
        pipeline.run("key-a", listOf(message("ok"), message("ok")))

        assertEquals(listOf("ok", "ok"), publisher.published.map { it.text })
    }

    @Test
    fun `repeated callback and edited-shrink snapshots emit nothing`() {
        val tracker = MessageTracker()
        val publisher = RecordingPublisher()
        val pipeline = TelegramRelayPipeline(tracker, publisher)

        pipeline.run("key-a", listOf(message("one")))
        pipeline.run("key-a", listOf(message("one"), message("two")))
        val replayed = pipeline.run("key-a", listOf(message("one"), message("two")))
        val edited = pipeline.run("key-a", listOf(message("one")))
        assertTrue(replayed is RelayResult.Duplicate)
        assertTrue(edited is RelayResult.Duplicate)
        assertEquals(listOf("one", "two"), publisher.published.map { it.text })
    }

    @Test
    fun `unextractable snapshot emits nothing and keeps no state`() {
        val tracker = MessageTracker()
        val publisher = RecordingPublisher()
        val pipeline = TelegramRelayPipeline(tracker, publisher)

        val result = pipeline.run("key-a", null)
        assertTrue(result is RelayResult.Unextractable)
        assertTrue(publisher.published.isEmpty())

        // The unextractable event left no cursor: a later real snapshot relays.
        assertEquals(RelayResult.Published(1), pipeline.run("key-a", listOf(message("one"))))
        assertEquals(listOf("one"), publisher.published.map { it.text })
    }

    @Test
    fun `burst inside one snapshot publishes every arrival`() {
        val tracker = MessageTracker()
        val publisher = RecordingPublisher()
        val pipeline = TelegramRelayPipeline(tracker, publisher)

        val burst = listOf(
            message("one"),
            message("two"),
            message("three"),
            message("four"),
        )
        val result = pipeline.run("key-a", burst)
        assertTrue(result is RelayResult.Published)
        assertEquals(4, (result as RelayResult.Published).count)
        assertEquals(4, publisher.published.size)
    }

    @Test
    fun `two conversations are detected and published independently`() {
        val tracker = MessageTracker()
        val publisher = RecordingPublisher()
        val pipeline = TelegramRelayPipeline(tracker, publisher)

        pipeline.run("key-one", listOf(message("one", conversation = "conv-one")))
        pipeline.run("key-two", listOf(message("two", conversation = "conv-two")))
        pipeline.run("key-one", listOf(message("one", conversation = "conv-one"), message("three", conversation = "conv-one")))

        assertEquals(listOf("one", "two", "three"), publisher.published.map { it.text })
    }

    @Test
    fun `numeric conversation keys do not interfere`() {
        val tracker = MessageTracker()
        val publisher = RecordingPublisher()
        val pipeline = TelegramRelayPipeline(tracker, publisher)

        pipeline.run("0|org.telegram.messenger|1|null|10278", listOf(message("one")))
        pipeline.run("0|org.telegram.messenger|2|null|10278", listOf(message("two")))

        assertEquals(listOf("one", "two"), publisher.published.map { it.text })
    }
    @Test
    fun `unconfirmed publication never reports published`() {
        val pipeline = TelegramRelayPipeline(MessageTracker(), RelayPublisher { false })
        assertTrue(pipeline.run("chat", listOf(message("one"))) is RelayResult.Failed)
    }

    @Test
    fun `confirmed dismissal permits identical timestamp and content in new series`() {
        val tracker = MessageTracker()
        val publisher = RecordingPublisher()
        val pipeline = TelegramRelayPipeline(tracker, publisher)
        val snapshot = listOf(message("OK").copy(timestampMs = 1000))
        pipeline.run("chat", snapshot)
        tracker.forgetDismissedSeries("chat")
        assertEquals(RelayResult.Published(1), pipeline.run("chat", snapshot))
        assertEquals(2, publisher.published.size)
    }

}
