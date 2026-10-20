package jp.linkserver.nittcsc.ui

import jp.linkserver.pyrite.screen.PyriteNavigationDirection
import org.junit.Assert.assertEquals
import org.junit.Test

class SchedulerScreenNavigationTest {
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
