package jp.linkserver.nittcsc.logic

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveLayoutTest {
    @Test
    fun largeScreenLayoutStartsAt600Dp() {
        assertFalse(shouldUseLargeScreenLayout(599))
        assertTrue(shouldUseLargeScreenLayout(600))
    }

    @Test
    fun drawerNavigationTakesPriorityOverLargeScreenRail() {
        assertFalse(
            shouldUseNavigationRail(
                useLargeScreenLayout = true,
                useDrawerNavigation = true
            )
        )
        assertTrue(
            shouldUseNavigationRail(
                useLargeScreenLayout = true,
                useDrawerNavigation = false
            )
        )
        assertFalse(
            shouldUseNavigationRail(
                useLargeScreenLayout = false,
                useDrawerNavigation = false
            )
        )
    }

    @Test
    fun twoPaneLayoutStartsAt720Dp() {
        assertFalse(shouldUseTwoPaneLayout(719))
        assertTrue(shouldUseTwoPaneLayout(720))
    }
}
