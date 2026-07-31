package io.github.teslanav.app.services

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import io.github.teslanav.app.SettingsManager
import io.github.teslanav.app.services.subservices.DestinationPoller
import io.github.teslanav.app.services.subservices.NetworkMonitor
import io.github.teslanav.app.services.subservices.OverlayController
import io.github.teslanav.app.services.subservices.StartupNavOpener

private const val TAG = "ForegroundService"

class ForegroundService : Service() {
    companion object {
        @Volatile
        var isRunning = false
            private set
    }

    private val subServices = mutableListOf<SubService>()

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
        isRunning = true
        ServiceNotification.createChannel(this)

        subServices += StartupNavOpener(this)
        subServices += OverlayController(this)
        subServices += DestinationPoller(this)
        subServices += NetworkMonitor(this)

        subServices.forEach { it.start() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand")
        val vehicleName = SettingsManager(this).teslaVehicleName?.ifBlank { null }
        val notification = ServiceNotification.build(this, vehicleName)
        startForeground(SERVICE_NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy")
        isRunning = false
        subServices.forEach { it.stop() }
        subServices.clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
}
