package dev.wristrelay.app.processing

import dev.wristrelay.app.model.RelayMessage
import dev.wristrelay.app.relay.RelayPublication
import dev.wristrelay.app.relay.RelayPublisher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pipeline-level behavior over an in-memory scripted publisher: cumulative
 * snapshots emit exactly the new suffix, repeated callbacks and edits stay
 * silent, bursts and multiple conversations do not lose or duplicate
 * entries. Detection semantics owned by MessageTracker (covered there);
 * this suite proves the binding, relay-result accounting and the
 * publication-loss guard (Cf7Lga4n2Sx7): the tracker advances only through
 * the successfully submitted prefix, so a full failure retries the same
 * snapshot on the next qualifying event and a partial failure retries only
 * the missing copies. Synthetic fixtures only (PRIVACY.md).
 */
class TelegramRelayPipelineTest {

    /**
     * Records every slice handed to Android per call and scripts how many
     * copies each call reports as successfully submitted
     * (Int.MAX_VALUE = every requested copy).
     */
    private class ScriptedPublisher : RelayPublisher {
        val requestedSlices = mutableListOf<List<RelayMessage>>()
        var submittedPerCall: (call: Int) -> Int = { Int.MAX_VALUE }
        var throwOnCall = 0
        private var calls = 0

        val texts get() = requestedSlices.flatten().map { it.text }

        override fun publish(messages: List<RelayMessage>): RelayPublication {
            calls++
            if (calls == throwOnCall) throw SecurityException("fixture denial")
            requestedSlices.add(messages)
            val submitted = submittedPerCall(calls).coerceAtMost(messages.size)
            return RelayPublication(
                submittedIdentities = messages.take(submitted)
                    .mapIndexed { index, _ -> "fixture-$calls-$index" to calls * 1_000 + index },
                requestedCount = messages.size,
                failureReason = if (submitted == messages.size) null else "fixture publication failure",
            )
        }
    }

    private fun assertPublished(count: Int, result: RelayResult) {
        assertTrue(result is RelayResult.Published)
        assertEquals(count, (result as RelayResult.Published).identities.size)
    }

    private fun message(text: String, conversation: String = "conv-one", time: Long? = null) = RelayMessage(
        conversationTitle = conversation,
        isGroup = false,
        sender = "sender-a",
        text = text,
        timestampMs = time,
    )

    @Test
    fun `cumulative snapshots emit exactly three in order`() {
        val tracker = MessageTracker()
        val publisher = ScriptedPublisher()
        val pipeline = TelegramRelayPipeline(tracker, publisher)

        pipeline.run("key-a", listOf(message("one")))
        pipeline.run("key-a", listOf(message("one"), message("two")))
        pipeline.run("key-a", listOf(message("one"), message("two"), message("three")))

        assertEquals(listOf("one", "two", "three"), publisher.texts)
    }

    @Test
    fun `two identical texts remain distinct and both emitted`() {
        val tracker = MessageTracker()
        val publisher = ScriptedPublisher()
        val pipeline = TelegramRelayPipeline(tracker, publisher)

        pipeline.run("key-a", listOf(message("ok")))
        pipeline.run("key-a", listOf(message("ok"), message("ok")))

        assertEquals(listOf("ok", "ok"), publisher.texts)
    }

    @Test
    fun `repeated callback and edited-shrink snapshots emit nothing`() {
        val tracker = MessageTracker()
        val publisher = ScriptedPublisher()
        val pipeline = TelegramRelayPipeline(tracker, publisher)

        pipeline.run("key-a", listOf(message("one")))
        pipeline.run("key-a", listOf(message("one"), message("two")))
        val replayed = pipeline.run("key-a", listOf(message("one"), message("two")))
        val edited = pipeline.run("key-a", listOf(message("one")))
        assertTrue(replayed is RelayResult.Duplicate)
        assertTrue(edited is RelayResult.Duplicate)
        assertEquals(listOf("one", "two"), publisher.texts)
    }

    @Test
    fun `unextractable snapshot emits nothing and keeps no state`() {
        val tracker = MessageTracker()
        val publisher = ScriptedPublisher()
        val pipeline = TelegramRelayPipeline(tracker, publisher)

        val result = pipeline.run("key-a", null)
        assertTrue(result is RelayResult.Unextractable)
        assertTrue(publisher.requestedSlices.isEmpty())

        // The unextractable event left no cursor: a later real snapshot relays.
        assertPublished(1, pipeline.run("key-a", listOf(message("one"))))
        assertEquals(listOf("one"), publisher.texts)
    }

    @Test
    fun `burst inside one snapshot publishes every arrival`() {
        val tracker = MessageTracker()
        val publisher = ScriptedPublisher()
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
        assertEquals(4, publisher.texts.size)
    }

    @Test
    fun `two conversations are detected and published independently`() {
        val tracker = MessageTracker()
        val publisher = ScriptedPublisher()
        val pipeline = TelegramRelayPipeline(tracker, publisher)

        pipeline.run("key-one", listOf(message("one", conversation = "conv-one")))
        pipeline.run("key-two", listOf(message("two", conversation = "conv-two")))
        pipeline.run("key-one", listOf(message("one", conversation = "conv-one"), message("three", conversation = "conv-one")))

        assertEquals(listOf("one", "two", "three"), publisher.texts)
    }

    @Test
    fun `numeric conversation keys do not interfere`() {
        val tracker = MessageTracker()
        val publisher = ScriptedPublisher()
        val pipeline = TelegramRelayPipeline(tracker, publisher)

        pipeline.run("0|org.telegram.messenger|1|null|10278", listOf(message("one")))
        pipeline.run("0|org.telegram.messenger|2|null|10278", listOf(message("two")))

        assertEquals(listOf("one", "two"), publisher.texts)
    }

    @Test
    fun `unconfirmed publication never reports published`() {
        val publisher = ScriptedPublisher().apply { submittedPerCall = { 0 } }
        val pipeline = TelegramRelayPipeline(MessageTracker(), publisher)
        assertTrue(pipeline.run("chat", listOf(message("one"))) is RelayResult.Failed)
    }

    @Test
    fun `complete failure retries the same snapshot on the next event`() {
        val tracker = MessageTracker()
        val publisher = ScriptedPublisher().apply {
            submittedPerCall = { if (it == 1) 0 else Int.MAX_VALUE }
        }
        val pipeline = TelegramRelayPipeline(tracker, publisher)
        val snapshot = listOf(message("one"), message("two"))

        assertTrue(pipeline.run("key-a", snapshot) is RelayResult.Failed)
        assertPublished(2, pipeline.run("key-a", snapshot))
        assertEquals(
            listOf(listOf("one", "two"), listOf("one", "two")),
            publisher.requestedSlices.map { slice -> slice.map { it.text } },
        )
    }

    @Test
    fun `partial failure retries only the missing copies`() {
        val tracker = MessageTracker()
        val publisher = ScriptedPublisher().apply {
            submittedPerCall = { if (it == 1) 1 else Int.MAX_VALUE }
        }
        val pipeline = TelegramRelayPipeline(tracker, publisher)
        val snapshot = listOf(message("one"), message("two"), message("three"))

        val failed = pipeline.run("key-a", snapshot)
        assertTrue(failed is RelayResult.Failed)
        assertPublished(2, pipeline.run("key-a", snapshot))
        assertEquals(
            listOf(listOf("one", "two", "three"), listOf("two", "three")),
            publisher.requestedSlices.map { slice -> slice.map { it.text } },
        )
    }

    @Test
    fun `partial failure plus growth retries missing and new copies together`() {
        val tracker = MessageTracker()
        val publisher = ScriptedPublisher().apply {
            submittedPerCall = { if (it == 1) 1 else Int.MAX_VALUE }
        }
        val pipeline = TelegramRelayPipeline(tracker, publisher)

        assertTrue(pipeline.run("key-a", listOf(message("one"), message("two"))) is RelayResult.Failed)
        assertPublished(2, pipeline.run("key-a", listOf(message("one"), message("two"), message("three"))))
        assertEquals(
            listOf(listOf("one", "two"), listOf("two", "three")),
            publisher.requestedSlices.map { slice -> slice.map { it.text } },
        )
    }

    @Test
    fun `publish exception counts as complete failure and retries the same snapshot`() {
        val tracker = MessageTracker()
        val publisher = ScriptedPublisher().apply { throwOnCall = 1 }
        val pipeline = TelegramRelayPipeline(tracker, publisher)
        val snapshot = listOf(message("one"), message("two"))

        assertTrue(pipeline.run("key-a", snapshot) is RelayResult.Failed)
        assertPublished(2, pipeline.run("key-a", snapshot))
        assertEquals(
            listOf(listOf("one", "two")),
            publisher.requestedSlices.map { slice -> slice.map { it.text } },
        )
    }

    @Test
    fun `failed shrink publication does not swallow the rebuilt entry`() {
        val tracker = MessageTracker()
        val publisher = ScriptedPublisher().apply {
            submittedPerCall = { if (it == 2) 0 else Int.MAX_VALUE }
        }
        val pipeline = TelegramRelayPipeline(tracker, publisher)
        val rebuilt = listOf(message("two", time = 2000), message("brand-new", time = 9000))

        assertPublished(3, pipeline.run("key-a", listOf(
            message("one", time = 1000),
            message("two", time = 2000),
            message("three", time = 3000),
        )))
        // Telegram rebuilds the array holding one genuinely new message; the
        // publication fails completely and must retry on the next event.
        assertTrue(pipeline.run("key-a", rebuilt) is RelayResult.Failed)
        assertPublished(1, pipeline.run("key-a", rebuilt))
        assertEquals(
            listOf(
                listOf("one", "two", "three"),
                listOf("brand-new"),
                listOf("brand-new"),
            ),
            publisher.requestedSlices.map { slice -> slice.map { it.text } },
        )
    }

    @Test
    fun `rebuilt snapshot retries an older missing entry before appended messages`() {
        val tracker = MessageTracker()
        val publisher = ScriptedPublisher().apply {
            submittedPerCall = { if (it == 1) 1 else Int.MAX_VALUE }
        }
        val pipeline = TelegramRelayPipeline(tracker, publisher)
        tracker.markBaseline("chat", List(4) { message("old", time = 1000) })
        val rebuilt = listOf(
            message("submitted", time = 4000),
            message("leftover", time = 1000),
            message("missing", time = 3000),
        )

        assertTrue(pipeline.run("chat", rebuilt) is RelayResult.Failed)
        val grown = rebuilt + message("appended", time = 2000)
        assertPublished(2, pipeline.run("chat", grown))
        assertEquals(RelayResult.Duplicate, pipeline.run("chat", grown))
        assertEquals(
            listOf(listOf("submitted", "missing"), listOf("missing", "appended")),
            publisher.requestedSlices.map { slice -> slice.map { it.text } },
        )
    }

    @Test
    fun `rolling snapshot retries the missing entry after dropping the submitted prefix`() {
        for (rebuilt in listOf(false, true)) {
            val tracker = MessageTracker()
            val publisher = ScriptedPublisher().apply {
                submittedPerCall = { if (it == 1) 1 else Int.MAX_VALUE }
            }
            val pipeline = TelegramRelayPipeline(tracker, publisher)
            if (rebuilt) tracker.markBaseline("chat", List(4) { message("old", time = 1000) })
            val a = message("A", time = 4000)
            val b = message("B", time = 3000)
            val c = message("C", time = 5000)
            val d = message("D", time = 6000)
            val original = if (rebuilt) listOf(a, b) else listOf(a, b, c)
            val shifted = if (rebuilt) listOf(b, c) else listOf(b, c, d)

            assertTrue(pipeline.run("chat", original) is RelayResult.Failed)
            assertPublished(shifted.size, pipeline.run("chat", shifted))
            assertEquals(shifted, publisher.requestedSlices.last())
            assertEquals(RelayResult.Duplicate, pipeline.run("chat", shifted))
        }
    }

    @Test
    fun `large rolling snapshot retries after a complete refusal`() {
        val publisher = ScriptedPublisher().apply {
            submittedPerCall = { if (it == 1) 0 else Int.MAX_VALUE }
        }
        val pipeline = TelegramRelayPipeline(MessageTracker(), publisher)
        val original = List(66) { message("entry-$it", time = it.toLong()) }
        val shifted = original.drop(1) + message("appended", time = 66)

        assertTrue(pipeline.run("chat", original) is RelayResult.Failed)
        assertPublished(66, pipeline.run("chat", shifted))
        assertEquals(shifted, publisher.requestedSlices.last())
    }

    @Test
    fun `confirmed dismissal permits identical timestamp and content in new series`() {
        val tracker = MessageTracker()
        val publisher = ScriptedPublisher()
        val pipeline = TelegramRelayPipeline(tracker, publisher)
        val snapshot = listOf(message("OK").copy(timestampMs = 1000))
        pipeline.run("chat", snapshot)
        tracker.forgetDismissedSeries("chat")
        assertPublished(1, pipeline.run("chat", snapshot))
        assertEquals(2, publisher.texts.size)
    }

}
