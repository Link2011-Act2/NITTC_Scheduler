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
}
