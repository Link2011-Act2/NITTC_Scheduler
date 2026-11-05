package jp.linkserver.nittcsc.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import jp.linkserver.nittcsc.data.ResolvedLesson
import jp.linkserver.nittcsc.data.UiDesignMode
import jp.linkserver.nittcsc.logic.*
import jp.linkserver.nittcsc.ui.theme.AppTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class UpcomingLessonPageTest {
    @get:Rule val compose = createComposeRule()
    private val today = LocalDate.of(2026, 11, 9)
    private val now = today.atTime(8, 10)
    private val selection = UpcomingLessonSelection(UpcomingLessonCandidate(today, 0, "1/2校時",
        LocalTime.of(8, 40), LocalTime.of(10, 10), ResolvedLesson("数学", "先生")), UpcomingLessonPhase.UPCOMING)

    @Test fun cardTravelsWithTodayPageAndOtherPageStartsAtTop() {
        lateinit var pager: PagerState
        compose.setContent {
            AppTheme(UiDesignMode.MATERIAL_3_EXPRESSIVE) {
                pager = rememberPagerState(pageCount = { 2 })
                HorizontalPager(pager, verticalAlignment = androidx.compose.ui.Alignment.Top,
                    beyondViewportPageCount = 1, modifier = Modifier.width(320.dp)) { index ->
                    TimetableDayPage(today.plusDays(index.toLong()), now, selection, true) {
                        Text("時間割", Modifier.fillMaxWidth().height(80.dp).testTag("day-$index"))
                    }
                }
            }
        }
        val initialLeft = compose.onNodeWithTag("upcoming-lesson-card").fetchSemanticsNode().boundsInRoot.left
        compose.runOnIdle { runBlocking { pager.scrollToPage(0, 0.4f) } }
        val duringLeft = compose.onNodeWithTag("upcoming-lesson-card").fetchSemanticsNode().boundsInRoot.left
        assertTrue(duringLeft < initialLeft)
        compose.runOnIdle { runBlocking { pager.scrollToPage(1) } }
        compose.onNodeWithTag("upcoming-lesson-card").assertIsNotDisplayed()
        val otherTop = compose.onNodeWithTag("day-1").fetchSemanticsNode().boundsInRoot.top
        assertEquals(0f, otherTop, 1f)
        compose.runOnIdle { runBlocking { pager.scrollToPage(0) } }
        compose.onNodeWithTag("upcoming-lesson-card").assertIsDisplayed()
    }

    @Test fun largeFontDarkCardKeepsSubjectTimeAndCountdown() {
        compose.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                AppTheme(UiDesignMode.MATERIAL_3_EXPRESSIVE, darkTheme = true, dynamicColor = true) {
                    Box(Modifier.width(280.dp)) { ExpressiveUpcomingLessonHero(selection, now) }
                }
            }
        }
        compose.onNodeWithText("数学").assertIsDisplayed()
        compose.onNodeWithText("8:40", substring = true).assertIsDisplayed()
        compose.onNodeWithText("30分", substring = true).assertIsDisplayed()
    }

    @Test fun standardModeAndFinishedDayDoNotReserveHeroSpace() {
        compose.setContent {
            Column {
                TimetableDayPage(today, now, selection, false) { Text("通常M3") }
                TimetableDayPage(today, now, null, true) { Text("全授業終了") }
            }
        }
        compose.onNodeWithTag("upcoming-lesson-card").assertDoesNotExist()
    }
}
