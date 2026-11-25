package jp.linkserver.nittcsc.logic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class NaturalLanguageSubjectCompatibilityTest {
    @Test fun fullRomanNumeralWinsOverShorterSubjectPrefix() {
        val candidates = listOf(
            NaturalLanguageLessonCandidate("英語I", "佐藤"),
            NaturalLanguageLessonCandidate("英語II", "佐藤")
        )
        fun subject(input: String) = NaturalLanguageTaskParser.parse(input, candidates,
            LocalDate.of(2026, 4, 6), LocalTime.NOON)?.subject
        assertEquals("英語II", subject("英語Ⅱのレポート"))
        assertEquals("英語II", subject("英語IIのレポート"))
        assertEquals("英語I", subject("英語Ⅰのレポート"))
        assertNull(NaturalLanguageTaskParser.parse("英語Ⅱのレポート", candidates.take(1),
            LocalDate.of(2026, 4, 6), LocalTime.NOON)?.subject?.takeIf { it.isNotBlank() })
        assertNull(NaturalLanguageTaskParser.parse("英語Ⅰのレポート", candidates.takeLast(1),
            LocalDate.of(2026, 4, 6), LocalTime.NOON)?.subject?.takeIf { it.isNotBlank() })
    }

    @Test fun acceptsRomanNumeralVariantsWithoutChangingStoredCandidate() {
        val candidates = listOf(NaturalLanguageLessonCandidate("数学Ⅳ", null))
        val result = NaturalLanguageTaskParser.parse("数学IVの課題", candidates,
            LocalDate.of(2026, 4, 6), LocalTime.NOON)
        assertEquals("数学Ⅳ", result?.subject)
    }
}
