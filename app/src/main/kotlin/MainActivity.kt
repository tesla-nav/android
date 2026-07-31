package io.github.teslanav.app

import android.Manifest.permission.POST_NOTIFICATIONS
import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import io.github.teslanav.app.screens.HomeScreen
import io.github.teslanav.app.services.ForegroundServiceLauncher
import io.github.teslanav.app.tesla.TeslaScreen
import io.github.teslanav.app.ui.settings.SettingsScreen
import rememberPermissionHandler

enum class ScreensName {
    HOME,
    SETTINGS,
    TESLA
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainScreen()
        }
    }
}

/** ForegroundService is core to the app (Tesla polling, WiFi recovery, overlay) — it must always be running, so it's started unconditionally as soon as the app launches (in addition to on boot, see [io.github.teslanav.app.BootReceiver]). */
@SuppressLint("InlinedApi")
@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val context = LocalContext.current

    val notificationPermissionHandler = rememberPermissionHandler(
        permission = POST_NOTIFICATIONS,
        onGranted = { ForegroundServiceLauncher.safeStartService(context) }
    )
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionHandler.requestPermission()
        } else {
            ForegroundServiceLauncher.safeStartService(context)
        }
    }

    NavHost(navController = navController, startDestination = ScreensName.HOME.name) {
        composable(ScreensName.HOME.name) {
            HomeScreen(navController)
        }
        composable(ScreensName.SETTINGS.name) {
            SettingsScreen(navController)
        }
        composable(ScreensName.TESLA.name) {
            TeslaScreen(navController)
        }
    }
}