package jp.linkserver.nittcsc.logic

import jp.linkserver.nittcsc.data.PlanEntity
import jp.linkserver.nittcsc.data.ResolvedLesson
import jp.linkserver.nittcsc.data.TaskEntity

fun taskMatchesLesson(task: TaskEntity, lesson: ResolvedLesson): Boolean {
    if (task.extraLessonId != null) return false
    if (task.lessonId != null && lesson.lessonId != null && task.lessonId != lesson.lessonId) return false
    return itemMatchesLesson(
        subject = task.subject,
        teacher = task.teacher,
        useTeacherMatching = task.useTeacherMatching,
        lesson = lesson
    )
}

fun planMatchesLesson(plan: PlanEntity, lesson: ResolvedLesson): Boolean {
    if (plan.extraLessonId != null) return false
    if (plan.lessonId != null && lesson.lessonId != null && plan.lessonId != lesson.lessonId) return false
    return itemMatchesLesson(
        subject = plan.subject,
        teacher = plan.teacher,
        useTeacherMatching = plan.useTeacherMatching,
        lesson = lesson
    )
}

fun findTaskLessonSlotIndex(
    task: TaskEntity,
    lessonSlots: List<Pair<ClassSlot, ResolvedLesson>>,
    ignoreDueTime: Boolean
): Int? {
    val dueMinuteOfDay = task.dueHour * 60 + task.dueMinute
    return lessonSlots.firstOrNull { (slot, lesson) ->
        taskMatchesLesson(task, lesson) && (
            ignoreDueTime || dueMinuteOfDay in slot.start.toMinuteOfDay() until slot.end.toMinuteOfDay()
        )
    }?.first?.index
}

private fun itemMatchesLesson(
    subject: String,
    teacher: String?,
    useTeacherMatching: Boolean,
    lesson: ResolvedLesson
): Boolean {
    val itemSubject = subject.trim()
    val lessonSubject = lesson.subject.trim()
    if (itemSubject.isBlank() || lessonSubject.isBlank()) return false
    if (!sameSubject(itemSubject, lessonSubject)) return false
    return !useTeacherMatching || teacherMatches(teacher, lesson.teacher)
}

private fun teacherMatches(expectedTeacher: String?, actualTeacher: String): Boolean {
    val normalizedExpected = expectedTeacher?.trim().orEmpty()
    if (normalizedExpected.isBlank()) return true
    val normalizedActual = actualTeacher.trim()
    if (normalizedActual.equals(normalizedExpected, ignoreCase = true)) return true
    return normalizeTeacherCandidates(normalizedActual)
        .any { it.equals(normalizedExpected, ignoreCase = true) }
}

private fun normalizeTeacherCandidates(value: String): List<String> {
    return value
        .replace('，', '、')
        .replace(',', '、')
        .replace('　', ' ')
        .split('、', ' ')
        .map { it.trim() }
        .filter { it.isNotBlank() }
}

private fun java.time.LocalTime.toMinuteOfDay(): Int = hour * 60 + minute
