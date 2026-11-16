package jp.linkserver.nittcsc.logic

import org.junit.Assert.*
import org.junit.Test

class CalendarSyncReplacementTest {
    private class Store : CalendarReplacementStore<String> {
        val active = linkedSetOf(1L, 2L)
        val deleted = mutableListOf<Set<Long>>()
        var failAt: String? = null
        var failDeleteOld = false
        var removeNewBeforeVerification = false
        var reads = 0
        var next = 10L
        val ignored = mutableListOf<Set<Long>>()
        override fun activeIds(): Set<Long> {
            reads++
            if (reads == 2 && removeNewBeforeVerification) active.remove(10L)
            return active.toSet()
        }
        override fun insertIfMissing(item: String, ignoredOldIds: Set<Long>): CalendarInsertResult {
            ignored += ignoredOldIds.toSet()
            if (item == failAt) throw SecurityException("failed")
            if (item == "manual") return CalendarInsertResult.AlreadyExists
            val id = next++; active += id
            return CalendarInsertResult.Inserted(id)
        }
        override fun delete(ids: Set<Long>) {
            deleted += ids.toSet()
            if (failDeleteOld && ids.any { it < 10 }) throw IllegalStateException("failed")
            active.removeAll(ids)
        }
    }

    @Test fun replacementInsertsAllThenDeletesOnlyOriginals() {
        val store = Store()
        val result = replaceCalendarEvents(store, listOf("a", "manual", "b"))
        assertEquals(CalendarReplacementResult(2, 1, null), result)
        assertEquals(setOf(10L, 11L), store.active)
        assertEquals(listOf(setOf(1L, 2L)), store.deleted)
        assertTrue(store.ignored.all { it == setOf(1L, 2L) })
    }

    @Test fun insertionFailureKeepsOldAndRollsBackKnownNewEvents() {
        val store = Store().apply { failAt = "b" }
        val result = replaceCalendarEvents(store, listOf("a", "b"))
        assertEquals(CalendarDeletionFailure.PERMISSION, result.failure)
        assertEquals(setOf(1L, 2L), store.active)
        assertEquals(listOf(setOf(10L)), store.deleted)
    }

    @Test fun cleanupFailureKeepsAllNewEvents() {
        val store = Store().apply { failDeleteOld = true }
        val result = replaceCalendarEvents(store, listOf("a", "b"))
        assertEquals(CalendarDeletionFailure.PROVIDER, result.failure)
        assertEquals(setOf(1L, 2L, 10L, 11L), store.active)
    }

    @Test fun missingNewEventDoesNotDeleteOriginals() {
        val store = Store().apply { removeNewBeforeVerification = true }
        val result = replaceCalendarEvents(store, listOf("a"))
        assertEquals(CalendarDeletionFailure.PROVIDER, result.failure)
        assertEquals(setOf(1L, 2L), store.active)
        assertEquals(listOf(setOf(10L)), store.deleted)
    }

    @Test fun emptyReplacementRemovesOnlyPreviousAutomaticEvents() {
        val store = Store()
        assertNull(replaceCalendarEvents(store, emptyList()).failure)
        assertTrue(store.active.isEmpty())
    }
}
