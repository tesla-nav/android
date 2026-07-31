package io.github.teslanav.app.tesla

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import io.github.teslanav.app.R
import io.github.teslanav.app.SettingsManager
import java.security.SecureRandom
import java.math.BigInteger

private const val TAG = "TeslaAuthManager"

/**
 * Local loopback port used to capture the Tesla OAuth callback.
 * Must exactly match the redirect URI registered on developer.tesla.com
 * (e.g. "Allowed Redirect URI(s)" = http://localhost:8765/callback).
 */
const val TESLA_AUTH_REDIRECT_PORT = 8765
private const val TESLA_AUTH_REDIRECT_URI = "http://localhost:$TESLA_AUTH_REDIRECT_PORT/callback"
private const val TESLA_AUTHORIZE_URL = "https://auth.tesla.com/oauth2/v3/authorize"
private const val TESLA_SCOPE = "openid email offline_access vehicle_device_data vehicle_location"

class TeslaAuthException(message: String) : Exception(message)

class TeslaAuthManager(
    private val context: Context,
    private val settingsManager: SettingsManager = SettingsManager(context)
) {
    private fun randomState(): String = BigInteger(130, SecureRandom()).toString(32)

    /** Opens the system browser on the Tesla login page. */
    fun startLogin(): String {
        val clientId = settingsManager.teslaClientId
            ?: throw TeslaAuthException(context.getString(R.string.no_client_id_configured))
        val state = randomState()

        val authorizeUri = Uri.parse(TESLA_AUTHORIZE_URL).buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", TESLA_AUTH_REDIRECT_URI)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("scope", TESLA_SCOPE)
            .appendQueryParameter("state", state)
            .build()

        Log.d(TAG, "Opening the browser for Tesla login (state=$state)")
        context.startActivity(Intent(Intent.ACTION_VIEW, authorizeUri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return state
    }

    /**
     * Waits for the browser callback on the local loopback server, then exchanges
     * the code for an access/refresh token. Call from a coroutine right
     * after [startLogin].
     */
    suspend fun awaitLoginResult(expectedState: String, client: TeslaClient): Result<TokenResponse> {
        Log.d(TAG, "Waiting for the OAuth callback on port $TESLA_AUTH_REDIRECT_PORT...")
        val server = LoopbackRedirectServer(context, TESLA_AUTH_REDIRECT_PORT)
        val params = try {
            server.awaitCallback()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to receive the callback", e)
            return Result.failure(e)
        }

        val error = params["error"]
        if (error != null) {
            Log.w(TAG, "Tesla refused the login: $error")
            return Result.failure(TeslaAuthException(context.getString(R.string.auth_refused, error)))
        }

        val code = params["code"]
        if (code == null) {
            Log.w(TAG, "Callback received without a 'code' parameter: $params")
            return Result.failure(TeslaAuthException(context.getString(R.string.no_code_param)))
        }
        if (params["state"] != expectedState) {
            Log.w(TAG, "State mismatch (possible interception): expected=$expectedState received=${params["state"]}")
            return Result.failure(TeslaAuthException(context.getString(R.string.state_mismatch)))
        }

        Log.d(TAG, "Code received, exchanging for tokens...")
        return client.exchangeCodeForToken(code, TESLA_AUTH_REDIRECT_URI)
            .onSuccess { tokens ->
                Log.d(TAG, "Tokens obtained successfully (expires in ${tokens.expiresIn}s)")
                settingsManager.teslaToken = tokens.accessToken
                settingsManager.teslaRefreshToken = tokens.refreshToken
            }
            .onFailure { e -> Log.w(TAG, "Failed to exchange the code for tokens", e) }
    }
}
