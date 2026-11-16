package jp.linkserver.nittcsc.sktjf

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class SktjfProfileSelectionTest {
    private val date = LocalDate.of(2026, 4, 1)
    private val common = SktjfLesson("normal", 1, 1, SktjfCycle.A, "通常", listOf("先生1", "先生2"),
        listOf("教室1", "教室2"), SktjfConditions(emptySet(), setOf("DD")), SktjfLessonKind.NORMAL, null, emptyList())
    private val dd = common.copy(id = "dd", subject = "DD", conditions = SktjfConditions(setOf("DD"), emptySet()))
    private val b = common.copy(id = "b", subject = "B", cycle = SktjfCycle.B, conditions = SktjfConditions(emptySet(), emptySet()))
    private val doc = SktjfDocument(2026, "S", date, date, "1-1", "2026.S.0", emptyList(), emptyList(), emptyList(), listOf(common, dd, b), emptyList())

    @Test fun selectsOneProfileAndRetainsSeparateAlternatingWeeks() {
        val regular = selectSktjfLessons(doc, emptySet())!!
        val doubleDegree = selectSktjfLessons(doc, setOf("DD"))!!
        assertEquals(listOf("通常", "B"), regular.lessons.map { it.subject })
        assertEquals(listOf("DD", "B"), doubleDegree.lessons.map { it.subject })
        assertEquals(listOf("先生1、先生2"), regular.lessons.first().teachers)
        assertEquals(listOf("教室1、教室2"), regular.lessons.first().rooms)
        assertTrue(regular.lessons.all { it.conditions == SktjfConditions(emptySet(), emptySet()) })
        assertEquals(3, doc.lessons.size)
    }

    @Test fun refusesAmbiguousApplicableLessonsRatherThanDisplayingBoth() {
        assertNull(selectSktjfLessons(doc.copy(lessons = listOf(b, b.copy(id = "duplicate"))), emptySet()))
        assertNull(selectSktjfLessons(doc.copy(lessons = listOf(b, b.copy(id = "weekly", cycle = SktjfCycle.WEEKLY))), emptySet()))
    }
}
