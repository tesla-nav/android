package io.github.teslanav.app.services.helpers

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log

private const val TAG = "WifiReconnector"

object WifiReconnector {
    /** Requests a reconnect to the currently configured network (lightweight, standard permission). */
    fun reconnect(context: Context) {
        val wifiManager = wifiManager(context) ?: return
        try {
            val result = wifiManager.reconnect()
            Log.d(TAG, "wifiManager.reconnect() -> $result")
        } catch (e: SecurityException) {
            Log.w(TAG, "No permission to force a WiFi reconnect", e)
        }
    }

    /** Turns WiFi off then on — more aggressive, requires being a system app (as this one is). */
    fun disableWifi(context: Context) {
        val wifiManager = wifiManager(context) ?: return
        try {
            @Suppress("DEPRECATION")
            wifiManager.isWifiEnabled = false
            Log.d(TAG, "WiFi disabled")
        } catch (e: SecurityException) {
            Log.w(TAG, "No permission to disable WiFi", e)
        }
    }

    fun enableWifi(context: Context) {
        val wifiManager = wifiManager(context) ?: return
        try {
            @Suppress("DEPRECATION")
            wifiManager.isWifiEnabled = true
            Log.d(TAG, "WiFi enabled")
        } catch (e: SecurityException) {
            Log.w(TAG, "No permission to enable WiFi", e)
        }
    }

    private fun wifiManager(context: Context): WifiManager? {
        return context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    }
}
