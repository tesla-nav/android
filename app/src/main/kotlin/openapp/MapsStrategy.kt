package io.github.teslanav.app.openapp

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

object MapsStrategy : OpenAppStrategy() {
    override val appName = "Maps"
    override val packageName = "com.google.android.apps.maps"

    override fun open(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, "geo:".toUri())
        launch(context, intent)
    }

    override fun openNavigation(context: Context, latitude: Double, longitude: Double) {
        val intent = Intent(Intent.ACTION_VIEW, "geo:$latitude,$longitude?q=$latitude,$longitude".toUri())
        launch(context, intent)
    }
}