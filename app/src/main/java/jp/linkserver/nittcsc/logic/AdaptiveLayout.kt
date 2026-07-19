package jp.linkserver.nittcsc.logic

internal const val LARGE_SCREEN_MIN_WIDTH_DP = 600
internal const val TWO_PANE_MIN_WIDTH_DP = 720

internal fun shouldUseLargeScreenLayout(windowWidthDp: Int): Boolean =
    windowWidthDp >= LARGE_SCREEN_MIN_WIDTH_DP

internal fun shouldUseNavigationRail(
    useLargeScreenLayout: Boolean,
    useDrawerNavigation: Boolean
): Boolean = useLargeScreenLayout && !useDrawerNavigation

internal fun shouldUseTwoPaneLayout(windowWidthDp: Int): Boolean =
    windowWidthDp >= TWO_PANE_MIN_WIDTH_DP
