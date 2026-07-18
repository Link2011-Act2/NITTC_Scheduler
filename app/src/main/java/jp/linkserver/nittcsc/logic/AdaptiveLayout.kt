package jp.linkserver.nittcsc.logic

internal const val LARGE_SCREEN_MIN_WIDTH_DP = 600

internal fun shouldUseLargeScreenLayout(windowWidthDp: Int): Boolean =
    windowWidthDp >= LARGE_SCREEN_MIN_WIDTH_DP
