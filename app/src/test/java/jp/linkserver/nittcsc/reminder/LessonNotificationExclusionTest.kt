package jp.linkserver.nittcsc.reminder

import jp.linkserver.nittcsc.data.LessonNotificationExclusionEntity
import jp.linkserver.nittcsc.data.ResolvedLesson
import org.junit.Assert.*
import org.junit.Test

class LessonNotificationExclusionTest {
    @Test fun subjectOnlyAndTeacherSpecificRulesArePreserved() {
        val lesson = ResolvedLesson("数学", "先生A、先生B")
        assertTrue(isExcluded(lesson, listOf(LessonNotificationExclusionEntity(subject = "数学"))))
        assertTrue(isExcluded(lesson, listOf(LessonNotificationExclusionEntity(subject = "数学", teacher = "先生B", matchTeacher = true))))
        assertFalse(isExcluded(lesson, listOf(LessonNotificationExclusionEntity(subject = "数学", teacher = "先生C", matchTeacher = true))))
        assertFalse(isExcluded(lesson, emptyList()))
        assertFalse(isExcluded(lesson, listOf(LessonNotificationExclusionEntity(subject = "英語"))))
    }
}
