package dev.wristrelay.app.processing

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceDismissalTrackerTest {
    @Test
    fun `own removal starts new series but unrelated removal does not`() {
        val tracker = SourceDismissalTracker()
        assertFalse(tracker.removedByUs("chat", 1))
        tracker.requested("chat", 1)
        assertFalse(tracker.removedByUs("chat", 2))
        assertTrue(tracker.removedByUs("chat", 1))
        assertFalse(tracker.removedByUs("chat", 1))
    }

    @Test
    fun `repost before removal starts series and old removal cannot clear it`() {
        val tracker = SourceDismissalTracker()
        tracker.requested("chat", 1)
        assertFalse(tracker.posted("chat", 1))
        assertTrue(tracker.posted("chat", 2))
        tracker.requested("chat", 2)
        assertFalse(tracker.removedByUs("chat", 1))
        assertTrue(tracker.removedByUs("chat", 2))
    }

    @Test
    fun `metadata is bounded`() {
        val tracker = SourceDismissalTracker(1)
        tracker.requested("old", 1)
        tracker.requested("new", 1)
        assertFalse(tracker.removedByUs("old", 1))
        assertTrue(tracker.removedByUs("new", 1))
    }
}
