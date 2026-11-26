package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.data.*
import jp.linkserver.nittcsc.logic.LessonWeekRange
import jp.linkserver.nittcsc.logic.TimetableTerm
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SktjfElectiveScheduleTest {
    private val date=LocalDate.of(2026,4,6)
    private val profile=NittcProfileEntity(2026,"UNDERGRADUATE",2,"2M",false,false)
    private fun extra(id: String, subject: String, range: SktjfV2Range?=null): SktjfExtraLessonEntity {
        val content=SktjfLessonContent(1,1,SktjfCycle.WEEKLY,subject,emptyList(),emptyList(),
            SktjfConditions(emptySet(),emptySet()),SktjfLessonKind.NORMAL,null,range)
        val json=SktjfBaselineCodec.encode(SktjfStoredBaseline(SktjfBaseline(2026,"S","2M",mapOf("local-$id" to content)),
            SktjfCalendarBaseline(2026,"S","2M",emptyMap())))
        return SktjfExtraLessonEntity("local-$id",2026,"S","2M",id,json)
    }
    private fun group(selected: String?)=SktjfSelectionGroupEntity(2026,"S","2M","group",1,1,
        "[\"a\",\"b\"]","[{\"sourceId\":\"src\",\"page\":1}]",selected)
    private fun resolve(selected: String?, extras: List<SktjfExtraLessonEntity>, mode: SchedulerUsageMode?=SchedulerUsageMode.NITTC)=
        SktjfElectiveSchedule.create(mode,listOf(profile),listOf(group(selected)),extras,emptyList())
            .resolve(2026,TimetableTerm.FIRST,1,1,date,SktjfCycle.A)
    @Test fun unknownKeepsAlternativesAndSelectedReturnsOnlyOne() {
        val extras=listOf(extra("a","数学"),extra("b","物理"))
        val unknown=resolve(null,extras) as SktjfElectiveSlotResult.Managed
        assertEquals(SktjfElectiveResolution.Undecided(listOf("a","b")),unknown.resolution)
        assertNull(unknown.selectedLesson())
        val chosen=resolve("b",extras) as SktjfElectiveSlotResult.Managed
        assertEquals("物理",chosen.selectedLesson()!!.subject)
        assertEquals("local-b",chosen.selectedLesson()!!.extraLessonId)
        assertEquals(SktjfElectiveSlotResult.Unmanaged,resolve(null,extras,SchedulerUsageMode.GENERIC))
    }
    @Test fun missingOfficialWeekDoesNotChooseAnAlternative() {
        val timed=extra("a","数学",SktjfV2Range(LessonWeekRange(end=10),"〜10週"))
        assertEquals(SktjfElectiveSlotResult.Unconfirmed,resolve(null,listOf(timed,extra("b","物理"))))
    }
    @Test fun removedChoiceNeedsReviewWithoutSelectingAnother() {
        val result=resolve("removed",listOf(extra("a","数学"),extra("b","物理"))) as SktjfElectiveSlotResult.Managed
        assertEquals(SktjfElectiveResolution.NeedsReview("removed",listOf("a","b")),result.resolution)
        assertNull(result.selectedLesson())
    }
    @Test fun removedGroupDoesNotHideAManualLessonInTheSlot() {
        val retired=group("a").copy(lessonIdsJson="[]",retiredLessonIdsJson="[\"a\",\"b\"]")
        val schedule=SktjfElectiveSchedule.create(SchedulerUsageMode.NITTC,listOf(profile),listOf(retired),
            listOf(extra("a","数学"),extra("b","物理")),emptyList())
        assertEquals(SktjfElectiveSlotResult.Unmanaged,
            schedule.resolve(2026,TimetableTerm.FIRST,1,1,date,SktjfCycle.A))
        assertEquals(1,schedule.groupsFor(2026,TimetableTerm.FIRST,1,1).size)
    }
}
