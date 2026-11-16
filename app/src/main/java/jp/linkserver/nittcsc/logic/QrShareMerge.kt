package jp.linkserver.nittcsc.logic

import jp.linkserver.nittcsc.data.LessonNoteEntity
import jp.linkserver.nittcsc.data.PlanEntity
import jp.linkserver.nittcsc.data.QrSharedItem
import jp.linkserver.nittcsc.data.QrExtraLessonReference
import jp.linkserver.nittcsc.data.TaskEntity
import java.time.LocalDate

data class QrShareImportSummary(
    val addedTasks: Int, val duplicateTasks: Int,
    val addedPlans: Int, val duplicatePlans: Int,
    val addedNotes: Int, val duplicateNotes: Int,
    val replacedNotes: Int, val keptNotes: Int,
    val unlinkedItems: Int = 0
) {
    val added: Int get() = addedTasks + addedPlans + addedNotes
    val duplicates: Int get() = duplicateTasks + duplicatePlans + duplicateNotes
}

internal data class QrNoteConflict(val current: LessonNoteEntity, val incoming: LessonNoteEntity)

internal data class QrShareMerge(
    val tasks: List<QrSharedItem>, val plans: List<QrSharedItem>,
    val addedNotes: List<LessonNoteEntity>, val replacedNotes: List<LessonNoteEntity>,
    val noteConflicts: List<QrNoteConflict>, val summary: QrShareImportSummary
)

private data class ItemKey(
    val subject: String, val teacher: String, val title: String,
    val date: LocalDate, val hour: Int, val minute: Int, val extraKey: String?
)

private fun itemKey(subject: String, teacher: String?, title: String, date: LocalDate, hour: Int, minute: Int, extraKey: String?) =
    ItemKey(subject.trim(), teacher.orEmpty().trim(), title.trim(), date, hour, minute, extraKey)

internal data class QrReattachedItems(val tasks: List<TaskEntity>, val plans: List<PlanEntity>)

/** Resolve only a prior missing reference for the same incoming item; preserve all user edits. */
internal fun qrReattachExtraItems(
    tasks: List<TaskEntity>, incomingTasks: List<QrSharedItem>,
    plans: List<PlanEntity>, incomingPlans: List<QrSharedItem>,
    extras: Map<String, QrExtraLessonReference>
): QrReattachedItems {
    fun target(id: String?, subject: String, teacher: String?, title: String, date: LocalDate,
        hour: Int, minute: Int, incoming: List<QrSharedItem>): String? {
        if (id == null || id in extras || !id.matches(Regex("qr-unresolved-[0-9a-f]{64}"))) return null
        val signature = id.removePrefix("qr-unresolved-")
        val key = itemKey(subject, teacher, title, date, hour, minute, null)
        return incoming.mapNotNull { item ->
            val ref = item.extraLessonReference
            if (ref?.signature != signature || itemKey(item.subject, item.teacher, item.title,
                    item.dueDate, item.dueHour, item.dueMinute, null) != key) null
            else qrResolveExtraLessonId(ref, extras)
        }.distinct().singleOrNull()
    }
    return QrReattachedItems(
        tasks.map { task -> target(task.extraLessonId, task.subject, task.teacher, task.title, task.dueDate,
            task.dueHour, task.dueMinute, incomingTasks)?.let { task.copy(extraLessonId = it) } ?: task },
        plans.map { plan -> target(plan.extraLessonId, plan.subject, plan.teacher, plan.title, plan.dueDate,
            plan.dueHour, plan.dueMinute, incomingPlans)?.let { plan.copy(extraLessonId = it) } ?: plan }
    )
}

/** 端末IDや完了・通知設定は同一性に含めず、既存項目をそのまま残す。 */
internal fun qrShareMerge(
    existingTasks: List<TaskEntity>, incomingTasks: List<QrSharedItem>,
    existingPlans: List<PlanEntity>, incomingPlans: List<QrSharedItem>,
    existingNotes: List<LessonNoteEntity>, incomingNotes: List<LessonNoteEntity>,
    approvedNoteReplacements: Set<LessonNoteEntity> = emptySet(),
    extras: Map<String, QrExtraLessonReference> = emptyMap()
): QrShareMerge {
    fun existingExtra(id: String?): String? = id?.let {
        if (it !in extras && it.matches(Regex("qr-unresolved-[0-9a-f]{64}"))) "unresolved:${it.removePrefix("qr-unresolved-")}" else "id:$it"
    }
    fun incomingExtra(ref: QrExtraLessonReference?): String? = ref?.let {
        qrResolveExtraLessonId(it, extras)?.let { id -> "id:$id" } ?: "unresolved:${it.signature}"
    }
    fun additions(existingKeys: Set<ItemKey>, incoming: List<QrSharedItem>): List<QrSharedItem> {
        val seen = existingKeys.toMutableSet()
        // 同じQR内の重複も、再読み込みと同じように除外する。
        return incoming.filter { seen.add(itemKey(it.subject, it.teacher, it.title, it.dueDate, it.dueHour, it.dueMinute, incomingExtra(it.extraLessonReference))) }
    }
    val tasks = additions(existingTasks.map {
        itemKey(it.subject, it.teacher, it.title, it.dueDate, it.dueHour, it.dueMinute, existingExtra(it.extraLessonId))
    }.toSet(), incomingTasks)
    val plans = additions(existingPlans.map {
        itemKey(it.subject, it.teacher, it.title, it.dueDate, it.dueHour, it.dueMinute, existingExtra(it.extraLessonId))
    }.toSet(), incomingPlans)
    val currentNotes = existingNotes.associateBy { it.date to it.slotIndex }
    val addedNotes = mutableListOf<LessonNoteEntity>()
    val replacedNotes = mutableListOf<LessonNoteEntity>()
    val conflicts = mutableListOf<QrNoteConflict>()
    var duplicateNotes = 0
    for (note in incomingNotes) {
        val current = currentNotes[note.date to note.slotIndex]
        when {
            current == null -> addedNotes += note
            current.text == note.text -> duplicateNotes++
            else -> {
                conflicts += QrNoteConflict(current, note)
                // 確認画面の後に編集されたメモは、承認済みでも上書きしない。
                if (current in approvedNoteReplacements) replacedNotes += note
            }
        }
    }
    return QrShareMerge(tasks, plans, addedNotes, replacedNotes, conflicts, QrShareImportSummary(
        tasks.size, incomingTasks.size - tasks.size, plans.size, incomingPlans.size - plans.size,
        addedNotes.size, duplicateNotes, replacedNotes.size, conflicts.size - replacedNotes.size
    ))
}
