package io.github.teslanav.app.openapp

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import io.github.teslanav.app.R

private const val TAG = "OpenAppStrategy"

abstract class OpenAppStrategy {
    abstract val appName: String
    abstract val packageName: String

    abstract fun open(context: Context)
    abstract fun openNavigation(context: Context, latitude: Double, longitude: Double)

    fun launch(context: Context, intent: Intent) {
        try {
            intent.setPackage(packageName)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            Log.d(TAG, "$appName launched (${intent.data})")
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "$appName is not installed, cannot launch")
            Toast.makeText(context, context.getString(R.string.app_not_installed_toast, appName), Toast.LENGTH_SHORT).show()
        }
    }
}


