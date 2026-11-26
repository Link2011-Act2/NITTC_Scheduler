package jp.linkserver.nittcsc.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import jp.linkserver.nittcsc.sktjf.*
import jp.linkserver.nittcsc.data.SktjfSelectionGroupEntity
import jp.linkserver.nittcsc.data.UiDesignMode
import jp.linkserver.nittcsc.ui.theme.AppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class SktjfElectiveUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun unknownCandidatesShareSlotAndRemainIndividuallyClickable() {
        fun item(id: String, subject: String) = SktjfVisibleExtra(id, id,
            SktjfLessonContent(1, 1, SktjfCycle.WEEKLY, subject, emptyList(), emptyList(),
                SktjfConditions(emptySet(), emptySet()), SktjfLessonKind.NORMAL, null), "")
        var opened: String? = null
        compose.setContent {
            MaterialTheme {
                Box(Modifier.width(300.dp).height(120.dp)) {
                    SktjfElectiveCards(listOf(item("a", "授業A"), item("b", "授業B")), { opened = it.id })
                }
            }
        }
        compose.onNodeWithText("授業A").assertIsDisplayed()
        compose.onNodeWithText("授業B").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals("b", opened) }
    }

    @Test fun importSelectionCanChooseThirdCandidateOrRemainUnknown() {
        val source = SktjfReference("src", 1, null)
        fun lesson(id: String, subject: String, conditions: SktjfConditions = SktjfConditions(emptySet(), emptySet())) =
            SktjfLesson(id, 1, 1, SktjfCycle.WEEKLY, subject, emptyList(), emptyList(), conditions,
                SktjfLessonKind.NORMAL, null, listOf(source))
        val lessons = listOf(lesson("a", "候補A"), lesson("b", "候補B"), lesson("c", "候補C"),
            lesson("dd", "DD限定", SktjfConditions(setOf("DD"), emptySet())))
        val date = LocalDate.of(2026, 4, 6)
        val document = SktjfDocument(2026, "S", date, date, "1-1", "2026.S.0.Beta", emptyList(), emptyList(),
            emptyList(), lessons, emptyList())
        val group = SktjfSelectionGroup("choice", 1, 1, lessons.map { it.id }, listOf(source))
        val candidate = SktjfV3Candidate(SktjfV2Candidate(document, emptyMap(), emptyMap()), listOf(group))
        var result: Map<String, String?>? = null
        compose.setContent { MaterialTheme {
            SktjfElectiveImportDialog(candidate, emptySet(), setOf("choice"), onConfirm = { result = it }, onCancel = {})
        } }
        compose.onNodeWithText("DD限定").assertDoesNotExist()
        compose.onNodeWithText("候補C").performClick()
        compose.onNodeWithText("選択を確定").performClick()
        compose.runOnIdle { assertEquals(mapOf("choice" to "c"), result) }
        compose.onNodeWithText("わからない").performClick()
        compose.onNodeWithText("選択を確定").performClick()
        compose.runOnIdle { assertEquals(mapOf("choice" to null), result) }
    }

    @Test fun removedGroupIsExplainedWithoutInventingAReplacement() {
        val date = LocalDate.of(2026, 4, 6)
        val document = SktjfDocument(2026, "S", date, date, "1-1", "2026.S.1.Beta", emptyList(), emptyList(),
            emptyList(), emptyList(), emptyList())
        val candidate = SktjfV3Candidate(SktjfV2Candidate(document, emptyMap(), emptyMap()), emptyList())
        val old = SktjfSelectionGroupEntity(2026, "S", "1-1", "choice", 1, 1, "[\"a\",\"b\"]",
            "[{\"sourceId\":\"src\",\"page\":1}]", "a")
        var result: Map<String, String?>? = null
        compose.setContent { MaterialTheme {
            SktjfElectiveImportDialog(candidate, emptySet(), emptySet(), listOf(old), { result = it }, {})
        } }
        compose.onNodeWithText("選択していた授業が配信から削除されました。時間割を確認してください。").assertIsDisplayed()
        compose.onNodeWithText("確認").performClick()
        compose.runOnIdle { assertEquals(emptyMap<String, String?>(), result) }
    }

    @Test fun narrowSlotAllowsThreeCandidatesInBothThemes() {
        val mode = mutableStateOf(UiDesignMode.MATERIAL_3)
        fun item(id: String) = SktjfVisibleExtra(id, id,
            SktjfLessonContent(1, 1, SktjfCycle.WEEKLY, "候補$id", emptyList(), emptyList(),
                SktjfConditions(emptySet(), emptySet()), SktjfLessonKind.NORMAL, null), "")
        var opened: String? = null
        compose.setContent {
            AppTheme(mode.value, dynamicColor = false) {
                Box(Modifier.width(150.dp).height(120.dp)) {
                    SktjfElectiveCards(listOf(item("A"), item("B"), item("C")), { opened = it.id })
                }
            }
        }
        for (theme in UiDesignMode.entries) {
            compose.runOnIdle { mode.value = theme; opened = null }
            compose.onNodeWithText("候補C").performScrollTo().assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals("C", opened) }
        }
    }

    @Test fun candidateCardsRemainClickableAtLargeFontSizesAndBothColorModes() {
        val mode = mutableStateOf(UiDesignMode.MATERIAL_3)
        val dark = mutableStateOf(false)
        val dynamic = mutableStateOf(false)
        val fontScale = mutableStateOf(1f)
        fun item(id: String, subject: String) = SktjfVisibleExtra(id, id,
            SktjfLessonContent(1, 1, SktjfCycle.WEEKLY, subject, listOf("担当教員"), listOf("教室"),
                SktjfConditions(emptySet(), emptySet()), SktjfLessonKind.NORMAL, null), "")
        var opened: String? = null
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale.value)) {
                AppTheme(mode.value, darkTheme = dark.value, dynamicColor = dynamic.value) {
                    Box(Modifier.width(320.dp).height(140.dp)) {
                        SktjfElectiveCards(listOf(item("a", "材料工学実験"), item("b", "生物工学実験")), { opened = it.id })
                    }
                }
            }
        }
        for (theme in UiDesignMode.entries) for (darkMode in listOf(false, true))
            for (dynamicColor in listOf(false, true)) for (scale in listOf(1f, 1.3f, 1.5f, 2f)) {
                compose.runOnIdle { mode.value = theme; dark.value = darkMode; dynamic.value = dynamicColor; fontScale.value = scale; opened = null }
                compose.onNodeWithText("材料工学実験").assertIsDisplayed().performClick()
                compose.runOnIdle { assertEquals("a", opened); opened = null }
                compose.onNodeWithText("生物工学実験").assertIsDisplayed().performClick()
                compose.runOnIdle { assertEquals("b", opened) }
            }
    }
}
