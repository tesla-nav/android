package io.github.teslanav.app.services.subservices

import android.content.Context
import android.util.Log
import io.github.teslanav.app.openapp.OpenAppProvider
import io.github.teslanav.app.services.SubService

private const val TAG = "StartupNavOpener"

/** Opens the configured navigation app once, when the service starts. */
class StartupNavOpener(private val context: Context) : SubService {
    override fun start() {
        val strategy = OpenAppProvider.getPreferredStrategy(context)
        if (strategy == null) {
            Log.w(TAG, "No navigation app configured, skipping open on startup")
        } else {
            strategy.open(context)
        }
    }

    override fun stop() = Unit
}
