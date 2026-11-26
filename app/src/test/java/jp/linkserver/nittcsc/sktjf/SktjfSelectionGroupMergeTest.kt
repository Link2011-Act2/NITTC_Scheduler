package jp.linkserver.nittcsc.sktjf

import jp.linkserver.nittcsc.data.SktjfSelectionGroupEntity
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class SktjfSelectionGroupMergeTest {
    private fun candidate(ids: List<String>?, groupId: String="group"): SktjfV3Candidate {
        val date=LocalDate.of(2026,4,6)
        val doc=SktjfDocument(2026,"S",date,date,"2M","2026.S.0.Beta",emptyList(),emptyList(),emptyList(),emptyList(),emptyList())
        val groups=if(ids==null) emptyList() else listOf(SktjfSelectionGroup(groupId,1,1,ids,listOf(SktjfReference("src",1,null))))
        return SktjfV3Candidate(SktjfV2Candidate(doc,emptyMap(),emptyMap()),groups)
    }
    private fun old(selected: String?)=SktjfSelectionGroupEntity(2026,"S","2M","group",1,1,
        "[\"a\",\"b\"]","[{\"sourceId\":\"src\",\"page\":1}]",selected)
    @Test fun sameGroupPreservesChoiceEvenWhenAnOptionWasRemoved() {
        val result=mergeSktjfSelectionGroups(candidate(listOf("b","c")),listOf(old("a"))).single()
        assertEquals("a",result.selectedLessonId)
        assertEquals(SktjfElectiveResolution.NeedsReview("a",listOf("b","c")),resolveSktjfElective(listOf("b","c"),listOf("b","c"),result.selectedLessonId))
    }
    @Test fun deletedGroupKeepsPriorSelectionForReviewButDropsUnselectedGroup() {
        val preserved=mergeSktjfSelectionGroups(candidate(null),listOf(old("a"))).single()
        assertEquals("[]",preserved.lessonIdsJson);assertEquals("a",preserved.selectedLessonId)
        assertTrue(mergeSktjfSelectionGroups(candidate(null),listOf(old(null))).isEmpty())
    }
    @Test fun explicitGroupKeepsAChoiceFromAnExactLegacyMembership() {
        val legacy = old("b").copy(groupId="legacy-group", retiredLessonIdsJson="[\"retired\"]")
        val merged = mergeSktjfSelectionGroups(candidate(listOf("b", "a"), "editor-group"), listOf(legacy)).single()
        assertEquals("editor-group", merged.groupId)
        assertEquals("b", merged.selectedLessonId)
        assertEquals(setOf("retired"), sktjfGroupIds(merged.retiredLessonIdsJson))
        val changed = mergeSktjfSelectionGroups(candidate(listOf("a", "c"), "other-group"), listOf(legacy))
        assertEquals(2, changed.size)
        assertNull(changed.single { it.groupId == "other-group" }.selectedLessonId)
        assertEquals("b", changed.single { it.groupId == "legacy-group" }.selectedLessonId)
    }
}
