package jp.linkserver.nittcsc.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import jp.linkserver.nittcsc.data.*
import jp.linkserver.nittcsc.sktjf.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate

class SktjfImportDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun applyIncomingForAllConflictsEnablesApply() {
        val date = LocalDate.of(2026, 4, 6)
        val doc = SktjfDocument(2026, "S", date, date, "1-1", "2026.S.0",
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val incoming = SktjfLessonContent(1, 1, SktjfCycle.WEEKLY, "数学", emptyList(), emptyList(),
            SktjfConditions(emptySet(), emptySet()), SktjfLessonKind.NORMAL, null)
        val conflict = SktjfLessonConflict("source", SktjfConflictReason.BOTH_CHANGED, null,
            SktjfLocalLesson("local", "source", incoming.copy(subject = "物理")), incoming)
        val snapshot = SktjfApplySnapshot(null, null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val prepared = SktjfApplyPreview(doc, snapshot, listOf(conflict), emptyList())
        var choices: Map<String, SktjfConflictChoice>? = null
        compose.setContent { MaterialTheme {
            SktjfImportDialog(UsageModeEntity(mode = "NITTC"),
                listOf(NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", false, false)), 2026,
                { _, _ -> SktjfDiscoveryResult.NotPublished }, { _, _ -> SktjfFetchResult.ReleaseNotFound },
                { SktjfPreviewResult.Blocked },
                { _, lessons, _ -> choices = lessons; SktjfApplyResult.Applied }, {}, {}, initialPreview = prepared)
        } }
        compose.onNodeWithText("時間割に適用").assertIsNotEnabled()
        compose.onNodeWithText("新しい内容をまとめて選ぶ").performClick()
        compose.onNodeWithText("時間割に適用").assertIsEnabled().performClick()
        compose.waitForIdle()
        assertEquals(mapOf("source" to SktjfConflictChoice.APPLY_INCOMING), choices)
    }

    @Test fun conflictsRequireExplicitChoiceAndCanBeSelectedTogether() {
        val date = LocalDate.of(2026, 4, 6)
        val doc = SktjfDocument(2026, "S", date, date, "1-1", "2026.S.0",
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val incoming = SktjfLessonContent(1, 1, SktjfCycle.WEEKLY, "数学", emptyList(), emptyList(),
            SktjfConditions(emptySet(), emptySet()), SktjfLessonKind.NORMAL, null)
        val local = SktjfLocalLesson("local", "source", incoming.copy(subject = "物理"))
        val conflict = SktjfLessonConflict("source", SktjfConflictReason.BOTH_CHANGED, null, local, incoming)
        val snapshot = SktjfApplySnapshot(null, null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val prepared = SktjfApplyPreview(doc, snapshot, listOf(conflict), emptyList())
        val manifest = SktjfManifest(2026, "S", "2026.S.0", Instant.parse("2026-04-01T00:00:00Z"), emptyList())
        var choices: Map<String, SktjfConflictChoice>? = null
        var applied = false
        compose.setContent { MaterialTheme {
            SktjfImportDialog(UsageModeEntity(mode = "NITTC"),
                listOf(NittcProfileEntity(2026, "UNDERGRADUATE", 1, "1-1", false, false)), 2026,
                { _, _ -> SktjfDiscoveryResult.Found("2026.S.0") },
                { _, _ -> SktjfFetchResult.Success(manifest, doc) }, { SktjfPreviewResult.Ready(prepared) },
                { _, lessons, _ -> choices = lessons; SktjfApplyResult.Applied }, { applied = true }, {})
        } }
        compose.onNodeWithText("配信を確認").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("時間割に適用").assertIsNotEnabled()
        compose.onNodeWithText("現在の内容をまとめて選ぶ").performClick()
        compose.onNodeWithText("時間割に適用").performClick()
        compose.waitForIdle()
        assertEquals(mapOf("source" to SktjfConflictChoice.KEEP_LOCAL), choices)
        assertTrue(applied)
    }

    @Test fun staleNextYearPreviewKeepsItsYearForRechecking() {
        val date = LocalDate.of(2027, 10, 4)
        val doc = SktjfDocument(2027, "F", date, date, "2M", "2027.F.0",
            emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val snapshot = SktjfApplySnapshot(null, null, emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
        val prepared = SktjfApplyPreview(doc, snapshot, emptyList(), emptyList())
        compose.setContent { MaterialTheme {
            SktjfImportDialog(UsageModeEntity(mode = "NITTC"),
                listOf(NittcProfileEntity(2027, "UNDERGRADUATE", 2, "2M", false, false)), 2026,
                { _, _ -> SktjfDiscoveryResult.NotPublished }, { _, _ -> SktjfFetchResult.ReleaseNotFound },
                { SktjfPreviewResult.Blocked }, { _, _, _ -> SktjfApplyResult.Stale }, {}, {}, initialPreview = prepared)
        } }
        compose.onNodeWithText("時間割に適用").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("2027").assertExists()
        compose.onNodeWithText("時間割が変更されました。もう一度確認してください").assertExists()
    }
}
