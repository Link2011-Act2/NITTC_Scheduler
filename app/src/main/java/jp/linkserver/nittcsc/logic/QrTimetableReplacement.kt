package jp.linkserver.nittcsc.logic

import jp.linkserver.nittcsc.data.LessonEntity
import jp.linkserver.nittcsc.data.LessonMode

/** 受信側の授業IDを維持し、送信側のIDは使わない。欠けた枠は空欄に置き換える。 */
internal fun qrTimetableReplacement(
    existing: List<LessonEntity>, incoming: List<LessonEntity>, academicYear: Int, term: TimetableTerm
): List<LessonEntity> {
    val current = existing.filter { it.academicYear == academicYear && it.timetableTerm == term }
    val received = incoming.filter { it.academicYear == academicYear && it.timetableTerm == term }
        .associateBy { it.dayOfWeek to it.slotIndex }
    val existingKeys = current.map { it.dayOfWeek to it.slotIndex }.toSet()
    return current.map { old ->
        val next = received[old.dayOfWeek to old.slotIndex] ?: LessonEntity(
            academicYear = academicYear, timetableTerm = term, dayOfWeek = old.dayOfWeek,
            slotIndex = old.slotIndex, mode = LessonMode.WEEKLY,
            weeklySubject = "", weeklyTeacher = "", aSubject = "", aTeacher = "", bSubject = "", bTeacher = ""
        )
        next.copy(id = old.id)
    } + received.filterKeys { it !in existingKeys }.values.map { it.copy(id = 0) }
}
