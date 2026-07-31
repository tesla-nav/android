package io.github.teslanav.app.tesla

import android.content.Context
import android.util.Log
import io.github.teslanav.app.R
import io.github.teslanav.app.SettingsManager
import io.github.teslanav.app.openapp.OpenAppProvider

private const val TAG = "TeslaDestinationSync"

sealed class SyncResult {
    data class DestinationOpened(val destinationName: String?, val latitude: Double, val longitude: Double) : SyncResult()
    data class DestinationUnchanged(val latitude: Double, val longitude: Double) : SyncResult()
    object NoActiveDestination : SyncResult()
    object VehicleAsleep : SyncResult()
    object NoVehicleSelected : SyncResult()
    object NoNavigationAppConfigured : SyncResult()
    data class Error(val message: String) : SyncResult()
}

/**
 * Fetches the vehicle's active GPS destination and launches the preferred
 * navigation app if it changed. Shared between polling (ForegroundService)
 * and manual triggering (overlay bubble).
 */
class TeslaDestinationSync(
    private val context: Context,
    private val settingsManager: SettingsManager = SettingsManager(context)
) {
    /**
     * @param previousLatitude/previousLongitude last known destination (polling) — if the
     * new destination is identical, the navigation app isn't relaunched needlessly.
     * Leave null to always force opening (manual trigger).
     */
    suspend fun sync(previousLatitude: Double? = null, previousLongitude: Double? = null): SyncResult {
        val vehicleId = settingsManager.teslaVehicleId
            ?: return SyncResult.NoVehicleSelected

        val strategy = OpenAppProvider.getPreferredStrategy(context)
            ?: return SyncResult.NoNavigationAppConfigured

        val accessToken = settingsManager.teslaToken
        if (accessToken.isNullOrBlank()) {
            return SyncResult.Error(context.getString(R.string.sync_no_token))
        }
        val clientId = settingsManager.teslaClientId
        val clientSecret = settingsManager.teslaClientSecret
        if (clientId.isNullOrBlank() || clientSecret.isNullOrBlank()) {
            return SyncResult.Error(context.getString(R.string.sync_no_client_credentials))
        }

        val vehicleData = fetchVehicleData(clientId, clientSecret, accessToken, vehicleId).getOrElse { error ->
            if (error.message?.contains("HTTP 408") == true) {
                Log.d(TAG, "HTTP 408: the vehicle didn't respond in time (probably asleep)")
                return SyncResult.VehicleAsleep
            }
            Log.w(TAG, "Failed to fetch vehicle_data", error)
            return SyncResult.Error(error.message ?: context.getString(R.string.unknown_error))
        }

        val driveState = vehicleData?.driveState
        if (driveState == null) {
            if (vehicleData?.state == "asleep" || vehicleData?.state == "offline") {
                Log.d(TAG, "Vehicle ${vehicleData.state}, no drive_state available")
                return SyncResult.VehicleAsleep
            }
            Log.d(TAG, "No drive_state in the response (state=${vehicleData?.state})")
        }
        val latitude = driveState?.activeRouteLatitude
        val longitude = driveState?.activeRouteLongitude
        if (latitude == null || longitude == null) {
            Log.d(TAG, "No active destination (active_route_latitude/longitude missing)")
            return SyncResult.NoActiveDestination
        }

        if (latitude == previousLatitude && longitude == previousLongitude) {
            Log.d(TAG, "Destination unchanged ($latitude, $longitude)")
            return SyncResult.DestinationUnchanged(latitude, longitude)
        }

        Log.d(TAG, "New destination: ${driveState.activeRouteDestination} ($latitude, $longitude)")
        strategy.openNavigation(context, latitude, longitude)
        return SyncResult.DestinationOpened(driveState.activeRouteDestination, latitude, longitude)
    }

    private suspend fun fetchVehicleData(
        clientId: String,
        clientSecret: String,
        accessToken: String,
        vehicleId: String
    ): Result<VehicleData?> {
        val client = TeslaClient(clientId = clientId, clientSecret = clientSecret, accessToken = accessToken)
        val result = client.getVehicleData(vehicleId)

        val error = result.exceptionOrNull()
        if (error != null && error.message?.contains("HTTP 401") == true) {
            Log.d(TAG, "Access token expired, refreshing...")
            val refreshToken = settingsManager.teslaRefreshToken
                ?: return Result.failure(Exception(context.getString(R.string.token_expired_no_refresh)))

            val refreshed = client.refreshToken(refreshToken).getOrElse { refreshError ->
                Log.w(TAG, "Failed to refresh token", refreshError)
                return Result.failure(refreshError)
            }
            settingsManager.teslaToken = refreshed.accessToken
            settingsManager.teslaRefreshToken = refreshed.refreshToken

            return TeslaClient(clientId = clientId, clientSecret = clientSecret, accessToken = refreshed.accessToken)
                .getVehicleData(vehicleId)
        }

        return result
    }
}

/** Localized message for a [SyncResult], shared between the overlay and the foreground service. */
fun SyncResult.toMessage(context: Context): String = when (this) {
    is SyncResult.DestinationOpened ->
        context.getString(R.string.sync_destination_opened, destinationName ?: context.getString(R.string.sync_destination_no_name))
    is SyncResult.DestinationUnchanged -> context.getString(R.string.sync_destination_unchanged)
    SyncResult.NoActiveDestination -> context.getString(R.string.sync_no_active_destination)
    SyncResult.VehicleAsleep -> context.getString(R.string.sync_vehicle_asleep)
    SyncResult.NoVehicleSelected -> context.getString(R.string.sync_no_vehicle_selected)
    SyncResult.NoNavigationAppConfigured -> context.getString(R.string.sync_no_nav_app)
    is SyncResult.Error -> context.getString(R.string.error_prefix, message)
}
