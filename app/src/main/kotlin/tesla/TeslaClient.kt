package io.github.teslanav.app.tesla

import io.github.teslanav.app.api.ApiClient
import io.ktor.client.call.body
import io.ktor.http.HttpMethod
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Regional Fleet API endpoint (EU Tesla account). Adjust if the account is NA/APAC. */
const val TESLA_FLEET_API_AUDIENCE = "https://fleet-api.prd.eu.vn.cloud.tesla.com"
private const val TESLA_TOKEN_URL = "https://auth.tesla.com/oauth2/v3/token"

@Serializable
data class TeslaProductsResponse(
    @SerialName("response") val response: List<ProductEntry>,
    @SerialName("count") val count: Int,
    @SerialName("error") val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null
)

@Serializable
data class ProductEntry(
    @SerialName("id") val id: Long,
    @SerialName("vehicle_id") val vehicleId: Long?,
    @SerialName("vin") val vin: String? = null,
    @SerialName("display_name") val displayName: String?,
    @SerialName("state") val state: String?,
    @SerialName("in_service") val inService: Boolean?
) {
    /** Human-readable name for the UI: display_name is often empty on Tesla's side if the car was never renamed. */
    val label: String
        get() = displayName?.ifBlank { null } ?: vin?.takeLast(6)?.let { "Vehicle $it" } ?: id.toString()
}

@Serializable
data class TeslaVehicleDataResponse(
    @SerialName("response") val response: VehicleData?,
    @SerialName("error") val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null
)

@Serializable
data class VehicleData(
    @SerialName("id") val id: Long,
    @SerialName("vehicle_id") val vehicleId: Long,
    @SerialName("vin") val vin: String,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("state") val state: String?,
    // Absent (not just null) if "endpoints" isn't specified in the request, or the vehicle is asleep.
    @SerialName("drive_state") val driveState: DriveState? = null
)

@Serializable
data class DriveState(
    @SerialName("shift_state") val shiftState: String? = null,
    @SerialName("speed") val speed: Int? = null,
    @SerialName("power") val power: Int? = null,
    @SerialName("latitude") val latitude: Double? = null,
    @SerialName("longitude") val longitude: Double? = null,
    @SerialName("heading") val heading: Int? = null,
    @SerialName("gps_as_of") val gpsAsOf: Long? = null,
    @SerialName("active_route_destination") val activeRouteDestination: String? = null,
    @SerialName("active_route_latitude") val activeRouteLatitude: Double? = null,
    @SerialName("active_route_longitude") val activeRouteLongitude: Double? = null,
    @SerialName("active_route_miles_to_arrival") val activeRouteMilesToArrival: Double? = null,
    @SerialName("active_route_minutes_to_arrival") val activeRouteMinutesToArrival: Double? = null,
    @SerialName("active_route_traffic_minutes_delay") val activeRouteTrafficMinutesDelay: Double? = null
)

@Serializable
data class RefreshTokenRequest(
    @SerialName("grant_type") val grantType: String = "refresh_token",
    @SerialName("client_id") val clientId: String,
    @SerialName("client_secret") val clientSecret: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("audience") val audience: String = TESLA_FLEET_API_AUDIENCE
)

@Serializable
data class AuthorizationCodeTokenRequest(
    @SerialName("grant_type") val grantType: String = "authorization_code",
    @SerialName("client_id") val clientId: String,
    @SerialName("client_secret") val clientSecret: String,
    @SerialName("code") val code: String,
    @SerialName("redirect_uri") val redirectUri: String,
    @SerialName("audience") val audience: String = TESLA_FLEET_API_AUDIENCE
)

@Serializable
data class TokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Int,
    @SerialName("token_type") val tokenType: String
)

class TeslaClient(
    private val clientId: String,
    private val clientSecret: String,
    accessToken: String? = null
) : ApiClient(
    baseUrl = "$TESLA_FLEET_API_AUDIENCE/api/1/",
    accessToken = accessToken
) {
    suspend fun getProducts(): Result<List<ProductEntry>> {
        return safeRequest(
            method = HttpMethod.Get,
            endpoint = "products"
        ) { response -> response.body<TeslaProductsResponse>().response }
    }

    suspend fun getVehicleData(vehicleId: String): Result<VehicleData?> {
        return safeRequest(
            method = HttpMethod.Get,
            // "endpoints" must be specified explicitly, otherwise Tesla omits drive_state from the response.
            endpoint = "vehicles/$vehicleId/vehicle_data?endpoints=drive_state"
        ) { response -> response.body<TeslaVehicleDataResponse>().response }
    }

    suspend fun wakeUpVehicle(vehicleId: String): Result<Unit> {
        return safeRequest(
            method = HttpMethod.Post,
            endpoint = "vehicles/$vehicleId/wake_up"
        ) { }
    }

    suspend fun refreshToken(refreshToken: String): Result<TokenResponse> {
        return safeRequest(
            method = HttpMethod.Post,
            fullUrl = TESLA_TOKEN_URL,
            body = RefreshTokenRequest(clientId = clientId, clientSecret = clientSecret, refreshToken = refreshToken)
        ) { response -> response.body<TokenResponse>() }
    }

    suspend fun exchangeCodeForToken(code: String, redirectUri: String): Result<TokenResponse> {
        return safeRequest(
            method = HttpMethod.Post,
            fullUrl = TESLA_TOKEN_URL,
            body = AuthorizationCodeTokenRequest(
                clientId = clientId,
                clientSecret = clientSecret,
                code = code,
                redirectUri = redirectUri
            )
        ) { response -> response.body<TokenResponse>() }
    }
}
