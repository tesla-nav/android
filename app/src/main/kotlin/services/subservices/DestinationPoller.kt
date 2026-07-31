package io.github.teslanav.app.services.subservices

import android.content.Context
import android.util.Log
import io.github.teslanav.app.R
import io.github.teslanav.app.SettingsManager
import io.github.teslanav.app.services.SubService
import io.github.teslanav.app.services.helpers.OverlayToast
import io.github.teslanav.app.tesla.SyncResult
import io.github.teslanav.app.tesla.TeslaDestinationSync
import io.github.teslanav.app.tesla.toMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "DestinationPoller"
private const val POLLING_INTERVAL_MS = 45_000L

/** Periodically syncs the active Tesla destination into the configured navigation app. */
class DestinationPoller(private val context: Context) : SubService {
    private val pollerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val overlayToast by lazy { OverlayToast(context) }

    override fun start() {
        if (!SettingsManager(context).teslaPollingEnabled) {
            Log.d(TAG, "Automatic polling disabled, manual updates only")
            return
        }
        Log.d(TAG, "Automatic polling enabled, starting the loop")
        val destinationSync = TeslaDestinationSync(context)
        var lastLatitude: Double? = null
        var lastLongitude: Double? = null

        pollerScope.launch {
            while (true) {
                when (val result = destinationSync.sync(lastLatitude, lastLongitude)) {
                    is SyncResult.DestinationOpened -> {
                        lastLatitude = result.latitude
                        lastLongitude = result.longitude
                        Log.d(TAG, "New destination opened: ${result.destinationName}")
                        overlayToast.show(result.toMessage(context))
                    }
                    is SyncResult.Error -> {
                        Log.w(TAG, "Sync error: ${result.message}")
                        overlayToast.show(context.getString(R.string.sync_error_update, result.message))
                    }
                    is SyncResult.DestinationUnchanged -> Unit
                    SyncResult.NoActiveDestination,
                    SyncResult.VehicleAsleep,
                    SyncResult.NoVehicleSelected,
                    SyncResult.NoNavigationAppConfigured -> Unit
                }
                delay(POLLING_INTERVAL_MS)
            }
        }
    }

    override fun stop() {
        pollerScope.cancel()
    }
}
