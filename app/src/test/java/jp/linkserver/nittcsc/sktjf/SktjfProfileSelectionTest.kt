package jp.linkserver.nittcsc.sktjf

import java.time.LocalDate
import jp.linkserver.nittcsc.logic.normalizeSubjectRomanNumerals
import org.junit.Assert.*
import org.junit.Test

class SktjfProfileSelectionTest {
    @Test fun onlyRomanNumeralGlyphsAreNormalized() {
        assertEquals("保体I・数学IV・英語VII（１年）", normalizeSubjectRomanNumerals("保体Ⅰ・数学Ⅳ・英語Ⅶ（１年）"))
        assertEquals("ABC I V", normalizeSubjectRomanNumerals("ABC I V"))
    }
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

    @Test fun explicitChoicePreservesUnambiguousOtherCycle() {
        val a1 = common.copy(id = "a1", cycle = SktjfCycle.A, conditions = SktjfConditions(emptySet(), emptySet()))
        val a2 = a1.copy(id = "a2", subject = "別のA")
        val source = doc.copy(lessons = listOf(a1, a2, b))
        assertEquals(listOf("a1", "a2", "b"), sktjfProfileSlotOptions(source, emptySet()).single().lessons.map { it.id })
        assertNull(selectSktjfLessons(source, emptySet()))
        assertNull(selectSktjfLessons(source, emptySet(), setOf("a1")))
        assertNull(selectSktjfLessons(source, emptySet(), setOf("a1", "a2", "b")))
        assertNull(selectSktjfLessons(source, emptySet(), setOf("a1", "b", "unknown")))
        assertEquals(listOf("a2", "b"), selectSktjfLessons(source, emptySet(), setOf("a2", "b"))!!.lessons.map { it.id })
        assertEquals(3, source.lessons.size)
    }

    @Test fun profileFilteringOccursBeforeAskingForChoices() {
        assertTrue(sktjfProfileSlotOptions(doc, emptySet()).isEmpty())
        assertTrue(sktjfProfileSlotOptions(doc, setOf("DD")).isEmpty())
        assertNotNull(selectSktjfLessons(doc, setOf("DD")))
    }
}
