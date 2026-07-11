package jp.linkserver.nittcsc.logic

import jp.linkserver.nittcsc.data.PlanEntity
import jp.linkserver.nittcsc.data.TaskEntity
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TaskPlanSearchTest {
    private val date = LocalDate.of(2026, 7, 13)

    @Test
    fun tokenizerNormalizesWidthCaseAndSeparators() {
        val tokens = tokenizeSearchQuery(" 英語Ｉ　ＹＡＭＡＤＡ・先生 ")

        assertTrue(tokens.contains("英語i"))
        assertTrue(tokens.contains("yamada先生"))
    }

    @Test
    fun taskMatchesSubjectTeacherDateAndTime() {
        val task = TaskEntity(
            subject = "英語I",
            teacher = "山田",
            title = "レポート提出",
            description = "研究室前の箱へ提出",
            dueDate = date,
            dueHour = 17,
            dueMinute = 30,
            createdDate = LocalDate.of(2026, 7, 10)
        )

        assertTrue(task.matchesTaskPlanSearch(tokenizeSearchQuery("英語 山田")))
        assertTrue(task.matchesTaskPlanSearch(tokenizeSearchQuery("月曜 17:30")))
        assertTrue(task.matchesTaskPlanSearch(tokenizeSearchQuery("2026/07/13 提出")))
        assertFalse(task.matchesTaskPlanSearch(tokenizeSearchQuery("数学")))
    }

    @Test
    fun taskAndPlanTypeCanBeSearched() {
        val task = TaskEntity(
            subject = "国語",
            title = "作文",
            dueDate = date,
            createdDate = date
        )
        val plan = PlanEntity(
            subject = "国語",
            title = "面談",
            dueDate = date,
            createdDate = date
        )

        assertTrue(task.matchesTaskPlanSearch(tokenizeSearchQuery("課題")))
        assertFalse(task.matchesTaskPlanSearch(tokenizeSearchQuery("予定")))
        assertTrue(plan.matchesTaskPlanSearch(tokenizeSearchQuery("予定")))
    }
}
