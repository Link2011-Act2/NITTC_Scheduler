package jp.linkserver.nittcsc.logic

import jp.linkserver.nittcsc.data.DayTypeEntity
import java.time.LocalDate

/** 年度の候補は管理中の時間割・試験から作る。通常のA/B日や休みの年度またぎでは増やさない。 */
internal fun qrShareAcademicYears(
    currentYear: Int,
    lessonKeys: Collection<LessonKey>,
    examScheduleDates: Collection<LocalDate>,
    examLessonDates: Collection<LocalDate>,
    dayTypes: Collection<DayTypeEntity>
): List<Int> = (
    listOf(currentYear) + lessonKeys.map { it.academicYear } +
        examScheduleDates.map(::academicYearForDate) + examLessonDates.map(::academicYearForDate) +
        dayTypes.filter { it.holidaySpecialLabel?.usesExamTimetable == true }.map { academicYearForDate(it.date) }
    ).distinct().sortedDescending()
