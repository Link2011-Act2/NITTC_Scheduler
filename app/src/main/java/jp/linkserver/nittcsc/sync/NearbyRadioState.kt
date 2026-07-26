package jp.linkserver.nittcsc.sync

import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.wifi.WifiManager

data class NearbyRadioState(
    val bluetoothAvailable: Boolean,
    val bluetoothEnabled: Boolean,
    val wifiAvailable: Boolean,
    val wifiEnabled: Boolean
) {
    val isReady: Boolean
        get() = bluetoothAvailable && bluetoothEnabled && wifiAvailable && wifiEnabled
}

/** Nearby Connections が使用する無線機能の現在状態を取得する。 */
fun readNearbyRadioState(context: Context): NearbyRadioState {
    val bluetoothAdapter = context.getSystemService(BluetoothManager::class.java)?.adapter
    val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java)
    return NearbyRadioState(
        bluetoothAvailable = bluetoothAdapter != null,
        bluetoothEnabled = bluetoothAdapter != null && runCatching { bluetoothAdapter.isEnabled }.getOrDefault(false),
        wifiAvailable = wifiManager != null,
        wifiEnabled = wifiManager?.isWifiEnabled == true
    )
}
