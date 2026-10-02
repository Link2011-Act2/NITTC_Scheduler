package jp.linkserver.nittcsc.data

import androidx.room.withTransaction
import jp.linkserver.nittcsc.InternalFeatureFlags
import jp.linkserver.nittcsc.logic.QrShareException
import jp.linkserver.nittcsc.logic.QrShareFailure
import jp.linkserver.nittcsc.logic.TimetableTerm
import jp.linkserver.nittcsc.logic.academicYearForDate
import jp.linkserver.nittcsc.logic.qrTimetableReplacement

internal class QrShareTransfer(private val repository: SchedulerRepository, private val db: AppDatabase) {
    private val dao = db.schedulerDao()

    suspend fun export(selection: QrShareSelection): QrSharePayload = db.withTransaction {
        check(InternalFeatureFlags.QR_SHARE_BETA)
        require(selection.sections.isNotEmpty() && selection.academicYear in 2000..2200)
        val settings = checkNotNull(dao.getSettings())
        val sections = selection.sections
        QrSharePayload(
            academicYear = selection.academicYear, sections = sections,
            secondTermStartMonth = settings.secondTermStartMonth, secondTermStartDay = settings.secondTermStartDay,
            lessons = dao.getLessonsOnce().filter {
                it.academicYear == selection.academicYear &&
                    (if (it.timetableTerm == TimetableTerm.FIRST) QrShareSection.FIRST else QrShareSection.SECOND) in sections
            },
            cancellations = if (QrShareSection.CANCELLATIONS in sections) dao.getCancelledLessonsOnce().filter { academicYearForDate(it.date) == selection.academicYear } else emptyList(),
            changes = if (QrShareSection.CHANGES in sections) dao.getChangedLessonsOnce().filter { academicYearForDate(it.date) == selection.academicYear } else emptyList(),
            dayTypes = if (QrShareSection.DAY_TYPES in sections) dao.getDayTypesOnce() else emptyList(),
            longBreaks = if (QrShareSection.DAY_TYPES in sections) dao.getLongBreaksOnce() else emptyList(),
            notes = if (QrShareSection.NOTES in sections) dao.getLessonNotesOnce().filter { (it.date to it.slotIndex) in selection.noteKeys } else emptyList(),
            plans = if (QrShareSection.PLANS in sections) dao.getPlansOnce().filter { it.id in selection.planIds }.map {
                QrSharedItem(it.subject, it.teacher, it.title, it.description, it.dueDate, it.dueHour, it.dueMinute, it.isCompleted, it.completedDate, it.createdDate, it.priority, it.useTeacherMatching)
            } else emptyList(),
            tasks = if (QrShareSection.TASKS in sections) dao.getTasksOnce().filter { it.id in selection.taskIds }.map {
                QrSharedItem(it.subject, it.teacher, it.title, it.description, it.dueDate, it.dueHour, it.dueMinute, it.isCompleted, it.completedDate, it.createdDate, it.priority, it.useTeacherMatching)
            } else emptyList()
        ).also { data ->
            // 選択したデータが生成直前に削除された場合、空の全件置換として配布しない。
            require(listOf(QrShareSection.NOTES, QrShareSection.PLANS, QrShareSection.TASKS)
                .all { it !in data.sections || data.count(it) > 0 })
            QrShareJson.decode(QrShareJson.encode(data))
        }
    }

    suspend fun import(payload: QrSharePayload): QrShareReplacedData = db.withTransaction {
        check(InternalFeatureFlags.QR_SHARE_BETA)
        // 確認済みDTOでも、保存前に全行を検証して部分的な取り込みを防ぐ。
        val data = QrShareJson.decode(QrShareJson.encode(payload))
        val settings = checkNotNull(dao.getSettings())
        if ((QrShareSection.FIRST in data.sections || QrShareSection.SECOND in data.sections) &&
            (settings.secondTermStartMonth != data.secondTermStartMonth || settings.secondTermStartDay != data.secondTermStartDay)) {
            throw QrShareException(QrShareFailure.SETTINGS)
        }
        val oldTasks = if (QrShareSection.TASKS in data.sections) dao.getTasksOnce() else emptyList()
        val oldPlans = if (QrShareSection.PLANS in data.sections) dao.getPlansOnce() else emptyList()
        val touched = mutableSetOf<String>()
        val importedTasks = mutableListOf<TaskEntity>()
        val importedPlans = mutableListOf<PlanEntity>()
        for (term in TimetableTerm.entries) {
            if ((if (term == TimetableTerm.FIRST) QrShareSection.FIRST else QrShareSection.SECOND) !in data.sections) continue
            // 既存の課題・予定が持つ端末内の授業IDを、同じ曜日・時限について維持する。
            qrTimetableReplacement(dao.getLessonsOnce(), data.lessons, data.academicYear, term)
                .forEach { dao.upsertLesson(it) }
            touched += LessonSyncPartition(data.academicYear, term).datasetKey
        }
        if (QrShareSection.CANCELLATIONS in data.sections) {
            dao.getCancelledLessonsOnce().filter { academicYearForDate(it.date) == data.academicYear }.forEach { dao.deleteCancelledLesson(it.date, it.slotIndex) }
            data.cancellations.forEach { dao.upsertCancelledLesson(it) }
            touched += SchedulerRepository.DATASET_CANCELLED_LESSONS
        }
        if (QrShareSection.CHANGES in data.sections) {
            dao.getChangedLessonsOnce().filter { academicYearForDate(it.date) == data.academicYear }.forEach { dao.deleteChangedLesson(it.date, it.slotIndex) }
            data.changes.forEach { dao.upsertChangedLesson(it) }
            touched += SchedulerRepository.DATASET_CHANGED_LESSONS
        }
        if (QrShareSection.DAY_TYPES in data.sections) {
            dao.deleteAllDayTypes()
            dao.upsertDayTypes(data.dayTypes)
            dao.deleteAllLongBreaks()
            data.longBreaks.forEach { dao.upsertLongBreak(it.copy(id = 0)) }
            touched += SchedulerRepository.DATASET_DAY_TYPES
            touched += SchedulerRepository.DATASET_LONG_BREAKS
        }
        if (QrShareSection.NOTES in data.sections) {
            dao.deleteAllLessonNotes()
            data.notes.forEach { dao.upsertLessonNote(it) }
            touched += SchedulerRepository.DATASET_LESSON_NOTES
        }
        if (QrShareSection.TASKS in data.sections) {
            dao.deleteAllTasks()
            data.tasks.forEach { item ->
                val task = item.asTask()
                importedTasks += task.copy(id = dao.upsertTask(task))
            }
            touched += SchedulerRepository.DATASET_TASKS
        }
        if (QrShareSection.PLANS in data.sections) {
            dao.deleteAllPlans()
            data.plans.forEach { item ->
                val plan = item.asPlan()
                importedPlans += plan.copy(id = dao.upsertPlan(plan))
            }
            touched += SchedulerRepository.DATASET_PLANS
        }
        repository.touchSyncDatasetMeta(*touched.toTypedArray())
        QrShareReplacedData(oldTasks, oldPlans, importedTasks, importedPlans, settings.addTasksToCalendar)
    }
}

data class QrShareReplacedData(
    val tasks: List<TaskEntity>, val plans: List<PlanEntity>,
    val importedTasks: List<TaskEntity>, val importedPlans: List<PlanEntity>,
    val syncCalendar: Boolean
)
