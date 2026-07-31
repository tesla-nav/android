package io.github.teslanav.app.openapp

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import io.github.teslanav.app.SettingsManager

object WazeStrategy : OpenAppStrategy() {
    override val appName: String = "Waze"
    override val packageName: String = "com.waze"

    override fun open(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, "waze://".toUri())
        launch(context, intent)
    }

    override fun openNavigation(context: Context, latitude: Double, longitude: Double) {
        val intent = Intent(Intent.ACTION_VIEW, "waze://?ll=$latitude,$longitude&navigate=yes".toUri())
        launch(context, intent)
    }
}