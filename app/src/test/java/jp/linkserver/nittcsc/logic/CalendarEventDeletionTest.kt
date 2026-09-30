package jp.linkserver.nittcsc.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarEventDeletionTest {
    private class Store(
        activeIds: Set<Long>,
        deletedIds: Set<Long> = emptySet()
    ) : CalendarDeletionStore {
        val active = activeIds.toMutableSet()
        val deleted = deletedIds.toMutableSet()
        var deleteCalls = 0
        var queryCalls = 0
        var onDelete: (Set<Long>) -> Unit = { ids ->
            deleted.addAll(ids)
            active.removeAll(ids)
        }
        var onQuery: (Int) -> Unit = {}

        override fun activeEventIds(): Set<Long> {
            onQuery(++queryCalls)
            return active.toSet()
        }

        override fun deleteEvents(ids: Set<Long>) {
            deleteCalls++
            onDelete(ids)
        }
    }

    @Test
    fun `500 active events and 370 tombstones count as 500 deletions only once`() {
        val store = Store((1L..500L).toSet(), (501L..870L).toSet())
        val first = deleteCalendarEvents(store)
        assertTrue(first.isSuccess)
        assertEquals(500, first.targetCount)
        assertEquals(500, first.deletedCount)
        assertEquals(0, first.remainingCount)
        assertEquals(870, store.deleted.size)
        val second = deleteCalendarEvents(store)
        assertTrue(second.isSuccess)
        assertEquals(0, second.targetCount)
        assertEquals(0, second.deletedCount)
        assertEquals(1, store.deleteCalls)
    }

    @Test
    fun `provider returning without removing events is not success`() {
        val store = Store(setOf(1, 2))
        store.onDelete = {}
        val result = deleteCalendarEvents(store)
        assertFalse(result.isSuccess)
        assertEquals(0, result.deletedCount)
        assertEquals(2, result.remainingCount)
    }

    @Test
    fun `partial deletion reports verified counts even when provider throws`() {
        val store = Store(setOf(1, 2))
        store.onDelete = {
            store.active.remove(1)
            throw IllegalStateException("provider failed")
        }
        val result = deleteCalendarEvents(store)
        assertFalse(result.isSuccess)
        assertEquals(1, result.deletedCount)
        assertEquals(1, result.remainingCount)
        assertEquals(CalendarDeletionFailure.PROVIDER, result.failure)
    }

    @Test
    fun `permission failure before querying is not an empty success`() {
        val store = Store(setOf(1))
        store.onQuery = { throw SecurityException() }
        val result = deleteCalendarEvents(store)
        assertFalse(result.isSuccess)
        assertNull(result.deletedCount)
        assertNull(result.remainingCount)
        assertEquals(CalendarDeletionFailure.PERMISSION, result.failure)
        assertEquals(0, store.deleteCalls)
    }

    @Test
    fun `permission failure during deletion preserves remaining counts`() {
        val store = Store(setOf(1))
        store.onDelete = { throw SecurityException() }
        val result = deleteCalendarEvents(store)
        assertFalse(result.isSuccess)
        assertEquals(0, result.deletedCount)
        assertEquals(1, result.remainingCount)
        assertEquals(CalendarDeletionFailure.PERMISSION, result.failure)
    }

    @Test
    fun `verification failure never reports deletion success`() {
        val store = Store(setOf(1))
        store.onQuery = { if (it == 2) throw IllegalStateException("query failed") }
        val result = deleteCalendarEvents(store)
        assertFalse(result.isSuccess)
        assertEquals(1, result.targetCount)
        assertNull(result.deletedCount)
        assertNull(result.remainingCount)
        assertEquals(CalendarDeletionFailure.PROVIDER, result.failure)
    }

    @Test
    fun `new events added during deletion are reported as remaining`() {
        val store = Store(setOf(1))
        store.onDelete = {
            store.active.removeAll(it)
            store.active.add(2)
        }
        val result = deleteCalendarEvents(store)
        assertFalse(result.isSuccess)
        assertEquals(1, result.deletedCount)
        assertEquals(1, result.remainingCount)
    }
}
