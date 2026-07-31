package io.github.teslanav.app.openapp

import android.content.Context
import io.github.teslanav.app.SettingsManager

object OpenAppProvider {
    fun getPreferredStrategy(context: Context): OpenAppStrategy? {
        val settingsManager = SettingsManager(context).preferredNavigationAppPackage
        return OpenAppManager.allStrategies.find { it.packageName == settingsManager }
    }
}