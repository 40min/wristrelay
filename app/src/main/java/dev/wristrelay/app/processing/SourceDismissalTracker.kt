package dev.wristrelay.app.processing

/** Bounded, content-free bookkeeping for our source dismissal lifecycle. */
class SourceDismissalTracker(private val limit: Int = 16) {
    private val pending = LinkedHashMap<String, Long>()

    fun clear() = pending.clear()

    fun abandon(key: String) { pending.remove(key) }

    fun requested(key: String, postTime: Long) {
        pending.remove(key)
        pending[key] = postTime
        while (pending.size > limit) pending.remove(pending.keys.first())
    }

    fun posted(key: String, postTime: Long): Boolean {
        val dismissed = pending[key] ?: return false
        val fresh = dismissed != postTime
        if (fresh) pending.remove(key)
        return fresh
    }

    fun removedByUs(key: String, postTime: Long): Boolean {
        if (pending[key] != postTime) return false
        pending.remove(key)
        return true
    }
}
