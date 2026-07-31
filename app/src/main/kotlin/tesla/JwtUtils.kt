package io.github.teslanav.app.tesla

import android.content.Context
import android.util.Base64
import io.github.teslanav.app.R
import org.json.JSONObject

/** Reads the "exp" claim (epoch seconds) from a JWT without verifying the signature — UI use only. */
fun decodeJwtExpirySeconds(token: String): Long? {
    return try {
        val payload = token.split(".").getOrNull(1) ?: return null
        val decoded = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
        val exp = JSONObject(String(decoded, Charsets.UTF_8)).optLong("exp", -1)
        exp.takeIf { it > 0 }
    } catch (e: Exception) {
        null
    }
}

/** E.g. "expires in 2h 15min" or "expired 3min ago". */
fun formatTokenExpiry(context: Context, expirySeconds: Long): String {
    val nowSeconds = System.currentTimeMillis() / 1000
    val deltaMinutes = (expirySeconds - nowSeconds) / 60
    val expired = deltaMinutes < 0
    val totalMinutes = kotlin.math.abs(deltaMinutes)
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    val duration = if (hours > 0) {
        context.getString(R.string.duration_hours_minutes, hours, minutes)
    } else {
        context.getString(R.string.duration_minutes, minutes)
    }
    return context.getString(if (expired) R.string.token_expiry_past else R.string.token_expiry_future, duration)
}
