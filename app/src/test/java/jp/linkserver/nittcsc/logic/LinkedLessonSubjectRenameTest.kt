package jp.linkserver.nittcsc.logic

import jp.linkserver.nittcsc.data.LessonDraft
import jp.linkserver.nittcsc.data.LessonEntity
import jp.linkserver.nittcsc.data.LessonMode
import org.junit.Assert.*
import org.junit.Test

class LinkedLessonSubjectRenameTest {
    private val row = LessonEntity(id = 42, academicYear = 2026, dayOfWeek = 1, slotIndex = 0,
        mode = LessonMode.ALTERNATING, weeklySubject = "", weeklyTeacher = "", aSubject = "英語Ⅱ", aTeacher = "",
        bSubject = "数学I", bTeacher = "")

    @Test fun resolvesOnlyMatchingCycleAndDoesNotGuessAmbiguousOrDeletedSubjects() {
        val draft = LessonDraft(mode = LessonMode.ALTERNATING, aSubject = "英語II（発展）", bSubject = "数学I")
        assertEquals("英語Ⅱ" to "英語II（発展）", linkedLessonSubjectRename(row, draft, "英語II"))
        assertNull(linkedLessonSubjectRename(row, draft, "数学Ⅰ"))
        assertNull(linkedLessonSubjectRename(row.copy(bSubject = "英語II"), draft, "英語II"))
        assertNull(linkedLessonSubjectRename(row, draft.copy(aSubject = ""), "英語II"))
        assertNull(linkedLessonSubjectRename(row, draft.copy(mode = LessonMode.WEEKLY), "英語II"))
    }

    @Test fun onlySpellingOrQualifiedNameIsAutomaticallyRelated() {
        assertTrue(isClearlyRelatedSubjectRename("英語Ⅱ", "英語II"))
        assertTrue(isClearlyRelatedSubjectRename("英語Ⅱ", "英語II（発展）"))
        assertFalse(isClearlyRelatedSubjectRename("英語I", "英語II"))
        assertFalse(isClearlyRelatedSubjectRename("英語II", "数学II"))
        assertFalse(isClearlyRelatedSubjectRename("英語II", ""))
    }
}
