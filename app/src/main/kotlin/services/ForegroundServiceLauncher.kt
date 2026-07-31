package io.github.teslanav.app.services

import android.content.Context
import android.content.Intent

object ForegroundServiceLauncher {
    fun safeStartService(context: Context) {
        val serviceIntent = Intent(context, ForegroundService::class.java)
        context.startForegroundService(serviceIntent)
    }
}