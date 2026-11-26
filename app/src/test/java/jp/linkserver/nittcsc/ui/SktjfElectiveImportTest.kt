package jp.linkserver.nittcsc.ui

import jp.linkserver.nittcsc.logic.LessonWeekRange
import jp.linkserver.nittcsc.sktjf.*
import org.junit.Assert.*
import org.junit.Test

class SktjfElectiveImportTest {
    private fun lesson(id: String) = SktjfLesson(id, 1, 1, SktjfCycle.WEEKLY, id, emptyList(), emptyList(),
        SktjfConditions(emptySet(), emptySet()), SktjfLessonKind.NORMAL, null, emptyList())

    @Test fun selectionIsAskedOnlyWhenCandidatesCanOverlap() {
        val lessons = listOf(lesson("a"), lesson("b"))
        assertFalse(sktjfElectiveNeedsChoice(lessons.take(1), emptyMap()))
        assertTrue(sktjfElectiveNeedsChoice(lessons, emptyMap()))
        val ranges = mapOf("a" to SktjfV2Range(LessonWeekRange(1, 8), "1-8"),
            "b" to SktjfV2Range(LessonWeekRange(9, null), "9-"))
        assertFalse(sktjfElectiveNeedsChoice(lessons, ranges))
        assertTrue(sktjfElectiveNeedsChoice(lessons, ranges + ("b" to SktjfV2Range(LessonWeekRange(8, null), "8-"))))
        val distinctSeries = listOf(
            SktjfV2WeekSeries("first", 1, setOf(1), setOf("a"), SktjfCycle.WEEKLY, emptyList()),
            SktjfV2WeekSeries("second", 1, setOf(1), setOf("b"), SktjfCycle.WEEKLY, emptyList()))
        assertTrue(sktjfElectiveNeedsChoice(lessons, ranges, distinctSeries))
    }
}
