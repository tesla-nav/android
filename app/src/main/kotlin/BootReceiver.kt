package io.github.teslanav.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import io.github.teslanav.app.services.ForegroundService
import io.github.teslanav.app.services.ForegroundServiceLauncher

private const val TAG = "BootReceiver"

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.d(TAG, "Triggered by ${intent.action}")
                if (!ForegroundService.isRunning) {
                    Log.d(TAG, "Starting ForegroundService")
                    ForegroundServiceLauncher.safeStartService(context)
                }
            }
        }
    }
}
