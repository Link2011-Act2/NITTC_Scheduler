package jp.linkserver.nittcsc.logic

internal sealed interface CalendarInsertResult {
    data class Inserted(val id: Long) : CalendarInsertResult
    data object AlreadyExists : CalendarInsertResult
}

internal interface CalendarReplacementStore<T> {
    fun activeIds(): Set<Long>
    fun insertIfMissing(item: T, ignoredOldIds: Set<Long>): CalendarInsertResult
    fun delete(ids: Set<Long>)
}

internal data class CalendarReplacementResult(val inserted: Int, val skipped: Int, val failure: CalendarDeletionFailure?)

/** Write a complete replacement before deleting the original IDs. */
internal fun <T> replaceCalendarEvents(store: CalendarReplacementStore<T>, items: List<T>): CalendarReplacementResult {
    fun failure(error: Exception) = if (error is SecurityException) CalendarDeletionFailure.PERMISSION else CalendarDeletionFailure.PROVIDER
    val old = try { store.activeIds() } catch (error: Exception) { return CalendarReplacementResult(0, 0, failure(error)) }
    val inserted = linkedSetOf<Long>()
    var skipped = 0
    try {
        for (item in items) when (val result = store.insertIfMissing(item, old)) {
            is CalendarInsertResult.Inserted -> {
                require(result.id !in old && inserted.add(result.id))
            }
            CalendarInsertResult.AlreadyExists -> skipped++
        }
        if (inserted.isNotEmpty()) check(store.activeIds().containsAll(inserted))
    } catch (error: Exception) {
        // Roll back only known new IDs. The original snapshot is never deleted here.
        try { if (inserted.isNotEmpty()) store.delete(inserted) } catch (_: Exception) { }
        return CalendarReplacementResult(0, skipped, failure(error))
    }
    try {
        if (old.isNotEmpty()) store.delete(old)
        val after = store.activeIds()
        check(after.none { it in old } && after.containsAll(inserted))
    } catch (error: Exception) {
        // All replacement events were written; retain them even if old-event cleanup failed.
        return CalendarReplacementResult(inserted.size, skipped, failure(error))
    }
    return CalendarReplacementResult(inserted.size, skipped, null)
}
