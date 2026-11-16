package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.data.ChangedLessonEntity
import jp.linkserver.nittcsc.data.ResolvedLesson
import jp.linkserver.nittcsc.data.SchedulerUsageMode
import jp.linkserver.nittcsc.logic.applyChangedLesson
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SktjfNotificationPolicyTest {
    @Test fun excludesOnlyUnchangedDistributedSpecialSlotsInNittcMode() {
        for ((kind, name) in listOf(SktjfLessonKind.MAKEUP to "補講", SktjfLessonKind.EXTENSION to "延長枠")) {
            val origin = SktjfLessonOrigin(kind, name); val lesson = ResolvedLesson(name, "")
            assertFalse(shouldNotifySktjfLesson(SchedulerUsageMode.NITTC, origin, lesson))
            assertTrue(shouldNotifySktjfLesson(SchedulerUsageMode.GENERIC, origin, lesson))
            assertTrue(shouldNotifySktjfLesson(SchedulerUsageMode.NITTC, null, lesson))
            assertTrue(shouldNotifySktjfLesson(SchedulerUsageMode.NITTC, SktjfLessonOrigin(SktjfLessonKind.NORMAL, name), lesson))
            val changed = applyChangedLesson(lesson, ChangedLessonEntity(LocalDate.of(2026, 4, 6), 0, "数学", "先生"))!!
            assertTrue(shouldNotifySktjfLesson(SchedulerUsageMode.NITTC, origin, changed))
        }
    }
}
