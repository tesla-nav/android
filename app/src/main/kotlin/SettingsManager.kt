package io.github.teslanav.app

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class SettingsManager(context: Context) {
    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("TeslaNavSettings", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_TESLA_CLIENT_ID = "tesla_client_id"
        private const val KEY_TESLA_CLIENT_SECRET = "tesla_client_secret"
        private const val KEY_TESLA_TOKEN = "tesla_token"
        private const val KEY_TESLA_REFRESH_TOKEN = "tesla_refresh_token"
        private const val KEY_TESLA_VEHICLE_ID = "tesla_vehicle_id"
        private const val KEY_TESLA_VEHICLE_NAME = "tesla_vehicle_name"
        private const val KEY_TESLA_POLLING_ENABLED = "tesla_polling_enabled"
        private const val KEY_TTS_ENABLED = "tts_enabled"
        private const val KEY_PREFERRED_NAVIGATION_APP_PACKAGE = "preferred_navigation_app_package"
    }

    /** Tesla app credentials (developer.tesla.com), entered at runtime — never baked into the build. */
    var teslaClientId: String?
        get() = sharedPreferences.getString(KEY_TESLA_CLIENT_ID, null)
        set(value) = sharedPreferences.edit { putString(KEY_TESLA_CLIENT_ID, value) }

    var teslaClientSecret: String?
        get() = sharedPreferences.getString(KEY_TESLA_CLIENT_SECRET, null)
        set(value) = sharedPreferences.edit { putString(KEY_TESLA_CLIENT_SECRET, value) }

    var teslaToken: String?
        get() = sharedPreferences.getString(KEY_TESLA_TOKEN, null)
        set(value) = sharedPreferences.edit { putString(KEY_TESLA_TOKEN, value) }

    var teslaRefreshToken: String?
        get() = sharedPreferences.getString(KEY_TESLA_REFRESH_TOKEN, null)
        set(value) = sharedPreferences.edit { putString(KEY_TESLA_REFRESH_TOKEN, value) }

    var teslaVehicleId: String?
        get() = sharedPreferences.getString(KEY_TESLA_VEHICLE_ID, null)
        set(value) = sharedPreferences.edit { putString(KEY_TESLA_VEHICLE_ID, value) }

    var teslaVehicleName: String?
        get() = sharedPreferences.getString(KEY_TESLA_VEHICLE_NAME, null)
        set(value) = sharedPreferences.edit { putString(KEY_TESLA_VEHICLE_NAME, value) }

    /** If false, the destination is only updated manually (overlay button). */
    var teslaPollingEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_TESLA_POLLING_ENABLED, false)
        set(value) = sharedPreferences.edit { putBoolean(KEY_TESLA_POLLING_ENABLED, value) }

    /** Voice announcement (internet connection lost/restored). */
    var ttsEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_TTS_ENABLED, false)
        set(value) = sharedPreferences.edit { putBoolean(KEY_TTS_ENABLED, value) }

    var preferredNavigationAppPackage: String?
        get() = sharedPreferences.getString(KEY_PREFERRED_NAVIGATION_APP_PACKAGE, null)
        set(value) = sharedPreferences.edit {
            putString(
                KEY_PREFERRED_NAVIGATION_APP_PACKAGE,
                value
            )
        }

    fun clear() {
        sharedPreferences.edit { clear() }
    }
}