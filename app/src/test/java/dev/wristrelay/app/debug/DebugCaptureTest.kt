package dev.wristrelay.app.debug

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DebugCaptureTest {

    @Before
    fun resetStore() {
        DebugEventStore.clear()
    }

    private data class EventSpec(
        val seq: Long = 1L,
        val action: String = "POSTED",
        val pkg: String = "org.example.app",
        val extras: List<ExtraSummary> = emptyList(),
        val postTimeMs: Long = 1_000L,
        val captureMs: Long = 1_050L,
    )

    private fun event(spec: EventSpec = EventSpec()) = DebugEvent(
        seq = spec.seq,
        wallClockMs = spec.captureMs,
        action = spec.action,
        packageName = spec.pkg,
        userId = 0,
        key = "0|${spec.pkg}|1|null|1000",
        isOngoing = false,
        postTimeMs = spec.postTimeMs,
        extras = spec.extras,
    )

    @Test
    fun `store is bounded and evicts oldest`() {
        for (seq in 1..(DebugEventStore.MAX_EVENTS + 5)) {
            DebugEventStore.append(event(EventSpec(seq = seq.toLong())))
        }
        val snapshot = DebugEventStore.snapshot()
        assertEquals(DebugEventStore.MAX_EVENTS, snapshot.size)
        assertEquals(
            6L,
            snapshot.first().seq,
        )
        assertEquals((DebugEventStore.MAX_EVENTS + 5).toLong(), snapshot.last().seq)
    }

    @Test
    fun `clear empties the store`() {
        DebugEventStore.append(event())
        DebugEventStore.clear()
        assertEquals(0, DebugEventStore.snapshot().size)
    }

    @Test
    fun `snapshot is a defensive copy`() {
        DebugEventStore.append(event())
        val snapshot = DebugEventStore.snapshot()
        DebugEventStore.clear()
        assertEquals(1, snapshot.size)
        assertEquals(0, DebugEventStore.snapshot().size)
    }

    @Test
    fun `formatting contains no text content`() {
        val extras = listOf(
            DebugFormat.summarizeExtra("android.text", ExtraKind.STRING, 42),
            DebugFormat.summarizeExtra("android.title", ExtraKind.CHARSEQ, 7),
            DebugFormat.summarizeExtra("android.messages", ExtraKind.PARCELABLE_ARRAY, 3),
            DebugFormat.summarizeExtra("android.showWhen", ExtraKind.BOOLEAN, null),
        )
        val lines = DebugFormat.format(event(EventSpec(extras = extras)))
        val joined = lines.joinToString("\n")
        assertTrue(joined.contains("extra android.text: String len=42"))
        assertTrue(joined.contains("extra android.title: CharSequence len=7"))
        assertTrue(joined.contains("extra android.messages: Parcelable[] len=3"))
        assertTrue(joined.contains("extra android.showWhen: Boolean"))
        // Digits in lengths/timestamps are expected; no free-text content present.
        assertTrue(joined.contains("pkg=org.example.app"))
        assertTrue(joined.contains("key=0|org.example.app|1|null|1000"))
    }

    @Test
    fun `timestamp description shows capture delay and UTC post time`() {
        val description = DebugFormat.describeTimestamps(posted = 1_000L, captureMs = 1_350L)
        assertTrue(description.contains("postTimeMillis=1000"))
        assertTrue(description.contains("+350ms"))
        // 1970-01-01T00:00:01.000Z is 1 second after the epoch.
        assertTrue(description.contains("00:00:01.000 UTC"))
    }

    @Test
    fun `negative capture delay is reported as clock skew`() {
        val description = DebugFormat.describeTimestamps(posted = 1_000L, captureMs = 900L)
        assertTrue(description.contains("100ms before postTime (clock skew)"))
    }
}
