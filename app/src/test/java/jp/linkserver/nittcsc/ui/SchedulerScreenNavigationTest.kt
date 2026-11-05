package jp.linkserver.nittcsc.ui

import jp.linkserver.pyrite.screen.PyriteNavigationDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class SchedulerScreenNavigationTest {
    @Test fun settingsDetailsUseActualInitialAndTargetRoutes() {
        for (detail in SettingsDestination.entries.filter { it != SettingsDestination.OVERVIEW }) {
            assertEquals(PyriteNavigationDirection.Forward, settingsNavigationDirection(SettingsDestination.OVERVIEW, detail))
            assertEquals(PyriteNavigationDirection.Backward, settingsNavigationDirection(detail, SettingsDestination.OVERVIEW))
        }
        assertEquals(PyriteNavigationDirection.Forward,
            settingsNavigationDirection(SettingsDestination.LESSON, SettingsDestination.LESSON_NOTIFICATIONS))
    }

    @Test fun forwardAndBackFollowSettingsAboutAndEditorParents() {
        for ((parent, child) in listOf(
            "main" to "settings", "settings" to "about", "about" to "oss", "about" to "update",
            "settings" to "specialTimetableSettings", "settings" to "sync", "sync" to "syncDiscovery",
            "sync" to "nearbySync", "syncDiscovery" to "nearbySync", "examTimetablePeriods" to "examTimetableEditor",
            "main" to "lessonChange", "main" to "taskPlanCalendar"
        )) {
            assertEquals("$parent -> $child", PyriteNavigationDirection.Forward, schedulerNavigationDirection(parent, child))
            assertEquals("$child -> $parent", PyriteNavigationDirection.Backward, schedulerNavigationDirection(child, parent))
        }
    }
}
