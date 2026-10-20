package jp.linkserver.nittcsc.ui

import jp.linkserver.pyrite.screen.PyriteNavigationDirection

/** App-owned route hierarchy; editors and date movement keep their own internal transitions. */
internal fun schedulerNavigationDirection(initial: String, target: String): PyriteNavigationDirection =
    if (schedulerScreenDepth(target) < schedulerScreenDepth(initial)) {
        PyriteNavigationDirection.Backward
    } else {
        PyriteNavigationDirection.Forward
    }

private fun schedulerScreenDepth(screen: String): Int = when (screen) {
    "main" -> 0
    "settings", "lessonChange", "examTimetablePeriods", "qrShare", "lessonSearch", "taskPlanCalendar", "vlm" -> 1
    "about", "sync", "specialTimetableSettings", "examTimetableEditor" -> 2
    "oss", "update", "syncDiscovery" -> 3
    "nearbySync" -> 4
    else -> 1
}
