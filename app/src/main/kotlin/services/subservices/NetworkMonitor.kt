package io.github.teslanav.app.services.subservices

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import io.github.teslanav.app.R
import io.github.teslanav.app.SettingsManager
import io.github.teslanav.app.services.SubService
import io.github.teslanav.app.services.helpers.TtsAnnouncer
import io.github.teslanav.app.services.helpers.WifiReconnector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "NetworkMonitor"
private const val WIFI_RETRY_INTERVAL_MS = 20_000L
/** Every N attempts, do a full reset (off/on) instead of a plain reconnect(). */
private const val WIFI_FULL_RESET_EVERY_N_ATTEMPTS = 3

/** Watches connectivity, announces changes via TTS, and drives WiFi recovery while offline. */
class NetworkMonitor(private val context: Context) : SubService {
    private val monitorScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)
    private var ttsAnnouncer: TtsAnnouncer? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var hasInternet = true
    private var wifiRecoveryJob: Job? = null

    override fun start() {
        if (!SettingsManager(context).ttsEnabled) {
            Log.d(TAG, "Voice announcements disabled, skipping network monitoring")
            return
        }
        Log.d(TAG, "Voice announcements enabled, starting network monitoring")
        val tts = TtsAnnouncer(context)
        ttsAnnouncer = tts

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                if (hasInternet) {
                    hasInternet = false
                    Log.d(TAG, "Internet connection lost")
                    tts.speak(context.getString(R.string.tts_internet_lost))
                }
                attemptWifiRecovery()
            }

            override fun onAvailable(network: Network) {
                if (!hasInternet) {
                    hasInternet = true
                    Log.d(TAG, "Internet connection restored")
                    tts.speak(context.getString(R.string.tts_internet_restored))
                }
                wifiRecoveryJob?.cancel()
            }
        }
        networkCallback = callback
        connectivityManager.registerNetworkCallback(request, callback)

        // registerNetworkCallback only reports future transitions — if there's no internet
        // already at startup (e.g. WiFi failed to auto-connect after boot), onLost() never
        // fires since there was never a network to lose, so the recovery loop must be
        // kicked off explicitly here.
        val activeCapabilities = connectivityManager.activeNetwork
            ?.let { connectivityManager.getNetworkCapabilities(it) }
        val hasInternetNow = activeCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        hasInternet = hasInternetNow
        if (!hasInternetNow) {
            Log.d(TAG, "No internet at startup")
            attemptWifiRecovery()
        }
    }

    override fun stop() {
        monitorScope.cancel()
        networkCallback?.let { connectivityManager.unregisterNetworkCallback(it) }
        ttsAnnouncer?.shutdown()
    }

    /** Reconnection loop while there's no internet: light reconnect(), full reset (off/on) every [WIFI_FULL_RESET_EVERY_N_ATTEMPTS] attempts. */
    private fun attemptWifiRecovery() {
        if (wifiRecoveryJob?.isActive == true) return

        wifiRecoveryJob = monitorScope.launch {
            var attempt = 0
            while (!hasInternet) {
                attempt++
                if (attempt % WIFI_FULL_RESET_EVERY_N_ATTEMPTS == 0) {
                    Log.d(TAG, "WiFi reconnection attempt #$attempt: full reset (off/on)")
                    WifiReconnector.disableWifi(context)
                    delay(3_000)
                    WifiReconnector.enableWifi(context)
                } else {
                    Log.d(TAG, "WiFi reconnection attempt #$attempt: reconnect()")
                    WifiReconnector.reconnect(context)
                }
                delay(WIFI_RETRY_INTERVAL_MS)
            }
            Log.d(TAG, "Connection restored, stopping the reconnection loop")
        }
    }
}
