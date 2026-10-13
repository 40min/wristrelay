package dev.wristrelay.app.processing

import dev.wristrelay.app.model.RelayMessage
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Synthetic fixtures only: "one", "two", "three" mirror the device test
 * vocabulary without any real message content (PRIVACY.md).
 */
class MessageTrackerTest {

    private fun message(text: String, sender: String = "sender-a", time: Long? = null) =
        RelayMessage(
            conversationTitle = "conv-one",
            isGroup = false,
            sender = sender,
            text = text,
            timestampMs = time,
        )

    private val convKey = "0|org.telegram.messenger|386558704|null|10278"

    @Test
    fun `cumulative snapshots emit exactly the new suffix`() {
        val tracker = MessageTracker()
        assertEquals(listOf("one"), tracker.newMessages(convKey, listOf(message("one"))).map { it.text })
        assertEquals(listOf("two"), tracker.newMessages(convKey, listOf(message("one"), message("two"))).map { it.text })
        assertEquals(
            listOf("three"),
            tracker.newMessages(convKey, listOf(message("one"), message("two"), message("three"))).map { it.text },
        )
    }

    @Test
    fun `repeated identical callback emits nothing`() {
        val tracker = MessageTracker()
        val snapshot = listOf(message("one"), message("two"), message("three"))
        tracker.newMessages(convKey, snapshot)
        assertEquals(emptyList<Any>(), tracker.newMessages(convKey, snapshot))
    }

    @Test
    fun `two identical texts are distinct entries and both delivered`() {
        val tracker = MessageTracker()
        tracker.newMessages(convKey, listOf(message("ok"), message("ok")))
        val bothAgain = listOf(message("ok"), message("ok"), message("ok"), message("ok"))
        assertEquals(
            listOf("ok", "ok"),
            tracker.newMessages(convKey, bothAgain).map { it.text },
        )
    }

    @Test
    fun `shrink resets cursor without emitting old entries`() {
        val tracker = MessageTracker()
        val three = listOf(message("one"), message("two"), message("three"))
        tracker.newMessages(convKey, three)
        // Source cleared to one entry; nothing emitted.
        assertEquals(emptyList<Any>(), tracker.newMessages(convKey, listOf(message("one"))))
        // New growth beyond one is emitted.
        val growth = listOf(message("one"), message("fresh-a"), message("fresh-b"))
        assertEquals(listOf("fresh-a", "fresh-b"), tracker.newMessages(convKey, growth).map { it.text })
    }

    @Test
    fun `conversations are tracked independently`() {
        val tracker = MessageTracker()
        val otherKey = "0|org.telegram.messenger|100|null|10278"
        tracker.newMessages(convKey, listOf(message("one")))
        assertEquals(
            listOf("two-conv-two"),
            tracker.newMessages(
                otherKey,
                listOf(
                    RelayMessage("conv-two", false, "sender-b", "two-conv-two"),
                ),
            ).map { it.text },
        )
    }

    @Test
    fun `empty snapshot is ignored and keeps cursor`() {
        val tracker = MessageTracker()
        tracker.newMessages(convKey, listOf(message("one")))
        assertEquals(emptyList<Any>(), tracker.newMessages(convKey, emptyList()))
        // Growth continues from the retained cursor.
        assertEquals(listOf("two"), tracker.newMessages(convKey, listOf(message("one"), message("two"))).map { it.text })
    }

    @Test
    fun `tracked conversations are bounded by eviction count`() {
        val tracker = MessageTracker(maxTrackedConversations = 2)
        val initial = listOf(message("one"))
        tracker.newMessages("conv-key-a", initial)
        tracker.newMessages("conv-key-b", initial)
        // At cap 2, inserting c evicts the eldest cursor (a); its state is
        // gone, so a's next two-entry snapshot is fully new again.
        tracker.newMessages("conv-key-c", initial)
        assertEquals(
            listOf("one", "one"),
            tracker.newMessages("conv-key-a", listOf(message("one"), message("one"))).map { it.text },
        )
    }

    @Test
    fun `baseline resets replay after restart or rebind`() {
        // Process life one: three messages relayed.
        val firstRun = MessageTracker()
        assertEquals(
            listOf("one", "two", "three"),
            firstRun.newMessages(
                convKey,
                listOf(message("one"), message("two"), message("three")),
            ).map { it.text },
        )
        // Process restart / listener rebind: fresh state, current snapshot
        // becomes the silent baseline — no replay, then growth relays again.
        val restarted = MessageTracker()
        restarted.markBaseline(
            convKey,
            listOf(message("one"), message("two"), message("three")),
        )
        assertEquals(
            emptyList<Any>(),
            restarted.newMessages(
                convKey,
                listOf(message("one"), message("two"), message("three")),
            ),
        )
        assertEquals(
            listOf("four"),
            restarted.newMessages(
                convKey,
                listOf(message("one"), message("two"), message("three"), message("four")),
            ).map { it.text },
        )
    }

    @Test
    fun `baseline on smaller current snapshot resumes from current`() {
        val restarted = MessageTracker()
        restarted.markBaseline(convKey, listOf(message("one")))
        assertEquals(
            listOf("two", "three"),
            restarted.newMessages(
                convKey,
                listOf(message("one"), message("two"), message("three")),
            ).map { it.text },
        )
    }

    @Test
    fun `zero baseline keeps nothing tracked`() {
        val restarted = MessageTracker()
        restarted.markBaseline(convKey, emptyList())
        restarted.markBaseline(convKey, listOf())
        // No cursor retained, so the first real snapshot relays normally.
        assertEquals(listOf("one"), restarted.newMessages(convKey, listOf(message("one"))).map { it.text })
    }

    @Test
    fun `telegram array rebuild with a newer entry is emitted instead of swallowed`() {
        // Owner repro 2026-10-13: relay through a series, then Telegram
        // rebuilds the array fresh with only the newest message inside.
        val tracker = MessageTracker()
        tracker.newMessages(
            convKey,
            listOf(
                message("one", time = 1000),
                message("two", time = 2000),
                message("three", time = 3000),
            ),
        )
        val rebuilt = listOf(message("next", time = 4000))
        assertEquals(
            listOf("next"),
            tracker.newMessages(convKey, rebuilt).map { it.text },
        )
        // Growth after the rebuilt snapshot keeps relaying.
        tracker.newMessages(convKey, rebuilt)
        assertEquals(
            listOf("after"),
            tracker.newMessages(
                convKey,
                listOf(message("next", time = 4000), message("after", time = 5000)),
            ).map { it.text },
        )
    }

    @Test
    fun `shrink to older already-relayed leftovers emits nothing`() {
        val tracker = MessageTracker()
        tracker.newMessages(
            convKey,
            listOf(
                message("one", time = 1000),
                message("two", time = 2000),
                message("three", time = 3000),
            ),
        )
        // Telegram shrinks the unread array to older entries only (often
        // after the phone side read newer ones): treated as read.
        assertEquals(
            emptyList<Any>(),
            tracker.newMessages(convKey, listOf(message("older-still-unread", time = 2000))),
        )
        // Successive smaller snapshots stay silent as well.
        assertEquals(
            emptyList<Any>(),
            tracker.newMessages(convKey, listOf(message("older-still-unread", time = 2000))),
        )
    }

    @Test
    fun `shrink with leftovers plus newer entry emits only the newer part`() {
        val tracker = MessageTracker()
        tracker.newMessages(
            convKey,
            listOf(
                message("one", time = 1000),
                message("two", time = 2000),
                message("three", time = 3000),
            ),
        )
        assertEquals(
            listOf("brand-new"),
            tracker.newMessages(
                convKey,
                listOf(message("two", time = 2000), message("brand-new", time = 9000)),
            ).map { it.text },
        )
        // The cursor advanced over the whole snapshot: the leftover older
        // entry is not re-emitted later even though the array keeps it.
        assertEquals(
            emptyList<Any>(),
            tracker.newMessages(convKey, listOf(message("two", time = 2000), message("brand-new", time = 9000))),
        )
    }

    @Test
    fun `late arrival carrying an older timestamp stays silent documented limitation`() {
        val tracker = MessageTracker()
        tracker.newMessages(convKey, listOf(message("one", time = 5000)))
        // An offline-sent message arrives with a timestamp older than the
        // newest relayed one: classified as already-relayed rather than
        // re-emitted (documented limitation, see VALIDATION.md).
        assertEquals(
            emptyList<Any>(),
            tracker.newMessages(convKey, listOf(message("old-queued", time = 1000))),
        )
    }

    @Test
    fun `entries without timestamps keep conservative shrink behavior`() {
        val tracker = MessageTracker()
        tracker.newMessages(
            convKey,
            listOf(
                message("one", time = 1000),
                message("two", time = 2000),
            ),
        )
        assertEquals(
            emptyList<Any>(),
            tracker.newMessages(convKey, listOf(message("untimestamped"))),
        )
        // Growth after the conservative collapse still relays normally.
        assertEquals(
            listOf("grown"),
            tracker.newMessages(
                convKey,
                listOf(message("untimestamped"), message("grown", time = 4000)),
            ).map { it.text },
        )
    }

    @Test
    fun `baseline records timestamps so later rebuilds classify correctly`() {
        val restarted = MessageTracker()
        restarted.markBaseline(convKey, listOf(message("history", time = 1000), message("history", time = 3000)))
        assertEquals(
            emptyList<Any>(),
            restarted.newMessages(convKey, listOf(message("older", time = 2000))),
        )
        assertEquals(
            listOf("newer"),
            restarted.newMessages(convKey, listOf(message("newer", time = 4000))).map { it.text },
        )
    }
}
