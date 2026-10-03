package jp.linkserver.nittcsc.data

import androidx.room.withTransaction
import jp.linkserver.nittcsc.InternalFeatureFlags
import jp.linkserver.nittcsc.logic.QrShareException
import jp.linkserver.nittcsc.logic.QrShareFailure
import jp.linkserver.nittcsc.logic.QrShareImportSummary
import jp.linkserver.nittcsc.logic.TimetableTerm
import jp.linkserver.nittcsc.logic.academicYearForDate
import jp.linkserver.nittcsc.logic.qrCheckScheduleCompatibility
import jp.linkserver.nittcsc.logic.qrExamDayUpdates
import jp.linkserver.nittcsc.logic.qrLessonReference
import jp.linkserver.nittcsc.logic.qrResolveSharedItems
import jp.linkserver.nittcsc.logic.qrShareMerge
import jp.linkserver.nittcsc.logic.qrTimetableReplacement

internal class QrShareTransfer(private val repository: SchedulerRepository, private val db: AppDatabase) {
    private val dao = db.schedulerDao()

    suspend fun export(selection: QrShareSelection): QrSharePayload = db.withTransaction {
        check(InternalFeatureFlags.QR_SHARE_BETA)
        require(selection.sections.isNotEmpty() && selection.academicYear in 2000..2200)
        val settings = checkNotNull(dao.getSettings())
        val sections = selection.sections
        val allLessons = dao.getLessonsOnce()
        val lessonsById = allLessons.associateBy { it.id }
        fun referenceFor(id: Long?): QrLessonReference? = id?.let {
            qrLessonReference(lessonsById[it] ?: throw QrShareException(QrShareFailure.LESSON_REFERENCE))
        }
        QrSharePayload(
            academicYear = selection.academicYear, sections = sections,
            secondTermStartMonth = settings.secondTermStartMonth, secondTermStartDay = settings.secondTermStartDay,
            scheduleTimes = if (QrShareSection.FIRST in sections || QrShareSection.SECOND in sections) settings.qrScheduleTimes() else null,
            lessons = allLessons.filter {
                it.academicYear == selection.academicYear &&
                    (if (it.timetableTerm == TimetableTerm.FIRST) QrShareSection.FIRST else QrShareSection.SECOND) in sections
            },
            cancellations = if (QrShareSection.CANCELLATIONS in sections) dao.getCancelledLessonsOnce().filter { academicYearForDate(it.date) == selection.academicYear } else emptyList(),
            changes = if (QrShareSection.CHANGES in sections) dao.getChangedLessonsOnce().filter { academicYearForDate(it.date) == selection.academicYear } else emptyList(),
            dayTypes = if (QrShareSection.DAY_TYPES in sections) dao.getDayTypesOnce() else emptyList(),
            longBreaks = if (QrShareSection.DAY_TYPES in sections) dao.getLongBreaksOnce() else emptyList(),
            notes = if (QrShareSection.NOTES in sections) dao.getLessonNotesOnce().filter { (it.date to it.slotIndex) in selection.noteKeys } else emptyList(),
            plans = if (QrShareSection.PLANS in sections) dao.getPlansOnce().filter { it.id in selection.planIds }.map {
                QrSharedItem(it.subject, it.teacher, it.title, it.description, it.dueDate, it.dueHour, it.dueMinute, it.isCompleted, it.completedDate, it.createdDate, it.priority, it.useTeacherMatching, referenceFor(it.lessonId))
            } else emptyList(),
            tasks = if (QrShareSection.TASKS in sections) dao.getTasksOnce().filter { it.id in selection.taskIds }.map {
                QrSharedItem(it.subject, it.teacher, it.title, it.description, it.dueDate, it.dueHour, it.dueMinute, it.isCompleted, it.completedDate, it.createdDate, it.priority, it.useTeacherMatching, referenceFor(it.lessonId))
            } else emptyList(),
            examDays = if (QrShareSection.EXAMS in sections) dao.getDayTypesOnce().filter {
                academicYearForDate(it.date) == selection.academicYear && it.holidaySpecialLabel?.usesExamTimetable == true
            }.map { DayTypeEntity(it.date, DayType.HOLIDAY, holidaySpecialLabel = it.holidaySpecialLabel) } else emptyList(),
            examDaySchedules = if (QrShareSection.EXAMS in sections) dao.getExamDaySchedulesOnce().filter {
                academicYearForDate(it.date) == selection.academicYear
            } else emptyList(),
            examLessons = if (QrShareSection.EXAMS in sections) dao.getExamLessonsOnce().filter {
                academicYearForDate(it.date) == selection.academicYear
            } else emptyList()
        ).also { data ->
            // 選択したデータが生成直前に削除された場合、空の共有として配布しない。
            require(listOf(QrShareSection.NOTES, QrShareSection.PLANS, QrShareSection.TASKS)
                .all { it !in data.sections || data.count(it) > 0 })
            QrShareJson.decode(QrShareJson.encode(data))
        }
    }

    suspend fun import(payload: QrSharePayload, approvedNoteReplacements: Set<LessonNoteEntity>): QrShareImportedData = db.withTransaction {
        check(InternalFeatureFlags.QR_SHARE_BETA)
        // 確認済みDTOでも、保存前に全行を検証して部分的な取り込みを防ぐ。
        val data = QrShareJson.decode(QrShareJson.encode(payload))
        val settings = checkNotNull(dao.getSettings())
        qrCheckScheduleCompatibility(data, settings)
        // DBの最新状態で再判定する。確認中の追加・編集と競合しても既存データを守る。
        val merge = qrShareMerge(
            if (QrShareSection.TASKS in data.sections) dao.getTasksOnce() else emptyList(), data.tasks,
            if (QrShareSection.PLANS in data.sections) dao.getPlansOnce() else emptyList(), data.plans,
            if (QrShareSection.NOTES in data.sections) dao.getLessonNotesOnce() else emptyList(), data.notes,
            approvedNoteReplacements
        )
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
        if (QrShareSection.EXAMS in data.sections) {
            dao.getExamLessonsOnce().filter { academicYearForDate(it.date) == data.academicYear }
                .map { it.date }.distinct().forEach { dao.deleteExamLessonsForDate(it) }
            dao.getExamDaySchedulesOnce().filter { academicYearForDate(it.date) == data.academicYear }
                .forEach { dao.deleteExamDaySchedule(it.date) }
            data.examDaySchedules.forEach { dao.upsertExamDaySchedule(it) }
            dao.upsertExamLessons(data.examLessons)
            val dayUpdates = qrExamDayUpdates(dao.getDayTypesOnce(), data.examDays, data.academicYear)
            dao.upsertDayTypes(dayUpdates)
            touched += SchedulerRepository.DATASET_EXAM_TIMETABLES
            if (dayUpdates.isNotEmpty()) touched += SchedulerRepository.DATASET_DAY_TYPES
        }
        if (QrShareSection.NOTES in data.sections) {
            (merge.addedNotes + merge.replacedNotes).forEach { dao.upsertLessonNote(it) }
            if (merge.addedNotes.isNotEmpty() || merge.replacedNotes.isNotEmpty()) touched += SchedulerRepository.DATASET_LESSON_NOTES
        }
        // 同時に取り込んだ時間割も含めて、受信側で保存された授業IDへ解決する。
        val linkedLessons = if ((merge.tasks + merge.plans).any { it.lessonReference != null }) {
            dao.getLessonsOnce().associateBy { it.lessonKey() }
        } else emptyMap()
        val resolved = qrResolveSharedItems(merge.tasks, merge.plans, linkedLessons)
        if (QrShareSection.TASKS in data.sections) {
            resolved.tasks.forEach { task ->
                importedTasks += task.copy(id = dao.upsertTask(task))
            }
            if (importedTasks.isNotEmpty()) touched += SchedulerRepository.DATASET_TASKS
        }
        if (QrShareSection.PLANS in data.sections) {
            resolved.plans.forEach { plan ->
                importedPlans += plan.copy(id = dao.upsertPlan(plan))
            }
            if (importedPlans.isNotEmpty()) touched += SchedulerRepository.DATASET_PLANS
        }
        if (touched.isNotEmpty()) repository.touchSyncDatasetMeta(*touched.toTypedArray())
        QrShareImportedData(importedTasks, importedPlans, settings.addTasksToCalendar,
            merge.summary.copy(unlinkedItems = resolved.unlinkedItems))
    }
}

data class QrShareImportedData(
    val importedTasks: List<TaskEntity>, val importedPlans: List<PlanEntity>,
    val syncCalendar: Boolean, val summary: QrShareImportSummary
)

data class QrShareImportResult(val summary: QrShareImportSummary, val integrationsFailed: Boolean)
