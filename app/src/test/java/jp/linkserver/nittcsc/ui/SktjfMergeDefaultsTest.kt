package jp.linkserver.nittcsc.ui

import jp.linkserver.nittcsc.data.SktjfSourceStateEntity
import jp.linkserver.nittcsc.sktjf.*
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SktjfMergeDefaultsTest {
    @Test fun manualAndAutomaticDefaultsDifferButApplyToBothWeeksTogether() {
        val date = LocalDate.of(2026, 4, 6)
        val document = SktjfDocument(2026, "S", date, date, "1-1", "2026.S.0",
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val source = SktjfSourceStateEntity(2026, "S", "1-1", "2026.S.0", "{}", false, "[]")
        val snapshot = SktjfApplySnapshot(null, null, emptyList(), emptyList(), emptyList(), listOf(source), emptyList())
        val a = SktjfLessonContent(1, 1, SktjfCycle.A, "英語I", emptyList(), emptyList(),
            SktjfConditions(emptySet(), emptySet()), SktjfLessonKind.NORMAL, null)
        val b = a.copy(cycle = SktjfCycle.B, subject = "数学I")
        val conflict = SktjfLessonConflict("a", SktjfConflictReason.LOCAL_CHANGED, a, null, a)
        val review = listOf(
            SktjfLessonReview("a", 1, 1, SktjfCycle.A, null, a, SktjfLessonReviewStatus.CONFLICT),
            SktjfLessonReview("b", 1, 1, SktjfCycle.B, b, b.copy(subject = "数学II"), SktjfLessonReviewStatus.AUTOMATIC)
        )
        val preview = SktjfApplyPreview(document, snapshot, listOf(conflict), emptyList(), lessonReview = review)
        assertEquals(mapOf("a" to SktjfConflictChoice.APPLY_INCOMING, "b" to SktjfConflictChoice.APPLY_INCOMING),
            initialSktjfLessonChoices(preview, automaticUpdate = false))
        assertEquals(mapOf("a" to SktjfConflictChoice.KEEP_LOCAL, "b" to SktjfConflictChoice.KEEP_LOCAL),
            initialSktjfLessonChoices(preview, automaticUpdate = true))
        val addedToEmptySlot = preview.copy(lessonConflicts = listOf(conflict.copy(previous = null)))
        assertEquals(SktjfConflictChoice.KEEP_LOCAL, initialSktjfLessonChoices(addedToEmptySlot, false)["a"])
        assertEquals(SktjfConflictChoice.KEEP_LOCAL, initialSktjfLessonChoices(addedToEmptySlot, false)["b"])
        val baseline = SktjfStoredBaseline(SktjfBaseline(2026, "S", "1-1", mapOf("a" to a)),
            SktjfCalendarBaseline(2026, "S", "1-1", emptyMap()))
        val staleState = source.copy(baselineJson = SktjfBaselineCodec.encode(baseline), requiresRelinking = true)
        val relink = addedToEmptySlot.copy(snapshot = snapshot.copy(sourceStates = listOf(staleState)))
        assertEquals(SktjfConflictChoice.APPLY_INCOMING, initialSktjfLessonChoices(relink, false)["a"])
        assertEquals(SktjfConflictChoice.KEEP_LOCAL, initialSktjfLessonChoices(relink, true)["a"])
        val missingLocal = relink.copy(lessonConflicts = emptyList(), lessonReview = listOf(review[1]))
        assertEquals(SktjfConflictChoice.KEEP_LOCAL, initialSktjfLessonChoices(missingLocal, true)["b"])
    }
}
