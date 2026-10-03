package jp.linkserver.nittcsc.logic

import org.junit.Assert.*
import org.junit.Test

class QrFragmentDotsTest {
    @Test fun capacityUsesWidthDiameterSpacingAndReservesOnlyLastRowForOverflow() {
        val layout = qrFragmentDotLayout(95f, 5f, 4f, 22f, 50)
        assertEquals(11, layout.columns)
        assertEquals(8, layout.lastRowColumns)
        assertEquals(30, layout.capacity)
        assertEquals(33, qrFragmentDotLayout(95f, 5f, 4f, 22f, 33).capacity)
        assertEquals(21, qrFragmentDotLayout(65f, 5f, 4f, 22f, 11).capacity)
        assertEquals(2, qrFragmentDotLayout(8f, 5f, 4f, 22f, 50).capacity)
    }

    @Test fun startingAtSixFillsSixRatherThanRotatingTheList() {
        val window = qrFragmentDotWindow(11, 15, emptySet())
        val received = setOf(5, 6, 7, 0)
        assertEquals((0 until 11).toList(), window.indices)
        assertEquals(listOf(true, false, false, false, false, true, true, true, false, false, false),
            window.indices.map { it in received })
        assertEquals(0, window.overflow)
        assertNull(qrFragmentToRetire(11, 15, received, emptySet()))
    }

    @Test fun retirementKeepsMissingFragmentsAndAscendingIdentity() {
        val received = setOf(5, 6, 7)
        var retired = emptySet<Int>()
        repeat(3) {
            val next = qrFragmentToRetire(50, 30, received, retired)!!
            assertEquals(5 + it, next)
            retired = retired + next
        }
        val window = qrFragmentDotWindow(50, 30, retired)
        assertEquals((0 until 50).filter { it !in received }.take(30), window.indices)
        assertEquals((0..4).toList(), window.indices.take(5))
        assertEquals(17, window.overflow)
        assertNull(qrFragmentToRetire(50, 30, received, retired))
    }

    @Test fun offscreenReceivedFragmentCanRetireWithoutDroppingMissingDots() {
        val before = qrFragmentDotWindow(50, 30, emptySet())
        assertEquals(49, qrFragmentToRetire(50, 30, setOf(49), emptySet()))
        val retired = setOf(49)
        val after = qrFragmentDotWindow(50, 30, retired)
        assertEquals(before.indices, after.indices)
        assertEquals(19, after.overflow)
        // Collectorが重複を無視して同じsnapshotを渡しても、同じdotを再退場させない。
        assertNull(qrFragmentToRetire(50, 30, setOf(49), retired))
        assertEquals(after, qrFragmentDotWindow(50, 30, retired))
    }

    @Test fun visibleReceivedDotRetiresBeforeOffscreenReceivedDot() {
        assertEquals(5, qrFragmentToRetire(50, 30, setOf(49, 5), emptySet()))
        val after = qrFragmentDotWindow(50, 30, setOf(5))
        assertEquals(30, after.indices.last())
        assertEquals(19, after.overflow)
    }

    @Test fun stopsExactlyWhenRemainingDotsFitAndThenAllCanFill() {
        var received = (0 until 20).toSet()
        var retired = emptySet<Int>()
        repeat(20) {
            val index = qrFragmentToRetire(50, 30, received, retired)!!
            assertTrue(index in received)
            retired = retired + index
            assertEquals(19 - it, qrFragmentDotWindow(50, 30, retired).overflow)
        }
        val finalIndices = qrFragmentDotWindow(50, 30, retired).indices
        assertEquals((20 until 50).toList(), finalIndices)
        for (index in finalIndices) {
            received = received + index
            assertNull(qrFragmentToRetire(50, 30, received, retired))
            assertEquals(finalIndices, qrFragmentDotWindow(50, 30, retired).indices)
        }
        assertTrue(finalIndices.all { it in received })
        // 穴埋めフェーズ確定後は、端末回転で表示容量が減っても退場を再開しない。
        assertNull(qrFragmentToRetire(50, 15, received, retired, fixed = true))
    }

    @Test fun hundredsOfOutOfOrderReceiptsNeverRetireAMissingFragment() {
        val order = (0 until 250).shuffled(kotlin.random.Random(46))
        var received = emptySet<Int>()
        var retired = emptySet<Int>()
        for (index in order) {
            received = received + index
            while (true) {
                val next = qrFragmentToRetire(250, 30, received, retired) ?: break
                assertTrue(next in received)
                retired = retired + next
                assertTrue(retired.all { it in received })
                assertEquals((0 until 250).filter { it !in retired }.take(30), qrFragmentDotWindow(250, 30, retired).indices)
            }
        }
        assertEquals(220, retired.size)
        val window = qrFragmentDotWindow(250, 30, retired)
        assertEquals(0, window.overflow)
        assertTrue(window.indices.all { it in received })
    }
}
