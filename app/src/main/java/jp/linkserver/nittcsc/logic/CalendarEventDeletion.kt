package jp.linkserver.nittcsc.logic

enum class CalendarDeletionFailure { PERMISSION, PROVIDER }

data class CalendarDeletionResult(
    val targetCount: Int,
    val deletedCount: Int?,
    val remainingCount: Int?,
    val failure: CalendarDeletionFailure? = null
) {
    val isSuccess: Boolean get() = failure == null && remainingCount == 0
}

interface CalendarDeletionStore {
    /** 削除済みを除く、アプリ由来の予定IDを返す。取得不能時は例外を投げる。 */
    fun activeEventIds(): Set<Long>
    fun deleteEvents(ids: Set<Long>)
}

/** APIの削除件数ではなく、操作前後の未削除IDで結果を判定する。 */
fun deleteCalendarEvents(store: CalendarDeletionStore): CalendarDeletionResult {
    val before = try {
        store.activeEventIds()
    } catch (e: Exception) {
        return CalendarDeletionResult(0, null, null, deletionFailure(e))
    }
    var failure: CalendarDeletionFailure? = null
    if (before.isNotEmpty()) {
        try {
            store.deleteEvents(before)
        } catch (e: Exception) {
            failure = deletionFailure(e)
        }
    }
    val after = try {
        store.activeEventIds()
    } catch (e: Exception) {
        return CalendarDeletionResult(before.size, null, null, failure ?: deletionFailure(e))
    }
    return CalendarDeletionResult(
        targetCount = before.size,
        deletedCount = (before - after).size,
        remainingCount = after.size,
        failure = failure
    )
}

private fun deletionFailure(e: Exception): CalendarDeletionFailure =
    if (e is SecurityException) CalendarDeletionFailure.PERMISSION else CalendarDeletionFailure.PROVIDER

/** ORを含む識別条件全体に削除済み除外を適用する。 */
fun activeCalendarEventSelection(selection: String): String = "deleted = 0 AND ($selection)"
