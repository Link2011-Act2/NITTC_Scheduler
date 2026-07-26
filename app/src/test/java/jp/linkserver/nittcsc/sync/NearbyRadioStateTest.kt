package jp.linkserver.nittcsc.sync

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyRadioStateTest {
    @Test
    fun isReady_requiresBothAvailableAndEnabledRadios() {
        assertTrue(
            NearbyRadioState(
                bluetoothAvailable = true,
                bluetoothEnabled = true,
                wifiAvailable = true,
                wifiEnabled = true
            ).isReady
        )

        assertFalse(
            NearbyRadioState(
                bluetoothAvailable = true,
                bluetoothEnabled = false,
                wifiAvailable = true,
                wifiEnabled = true
            ).isReady
        )
        assertFalse(
            NearbyRadioState(
                bluetoothAvailable = true,
                bluetoothEnabled = true,
                wifiAvailable = true,
                wifiEnabled = false
            ).isReady
        )
        assertFalse(
            NearbyRadioState(
                bluetoothAvailable = false,
                bluetoothEnabled = false,
                wifiAvailable = true,
                wifiEnabled = true
            ).isReady
        )
    }
}
