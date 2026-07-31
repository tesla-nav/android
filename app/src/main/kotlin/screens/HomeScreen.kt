package io.github.teslanav.app.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.github.teslanav.app.R
import io.github.teslanav.app.ScreensName
import io.github.teslanav.app.SettingsManager
import io.github.teslanav.app.ui.common.ScreenWrapper
import io.github.teslanav.app.ui.settings.navigationApps

@Composable
fun HomeScreen(navController: NavController) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }

    ScreenWrapper(
        navController = navController,
        title = stringResource(R.string.home_title),
        showBackButton = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TeslaCard(navController, settingsManager)
            MapCard(navController, settingsManager)
        }
    }
}

@Composable
private fun DashboardCard(title: String, value: String, icon: ImageVector, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TeslaCard(navController: NavController, settingsManager: SettingsManager) {
    val configured = !settingsManager.teslaClientId.isNullOrBlank() && !settingsManager.teslaClientSecret.isNullOrBlank()
    val connected = !settingsManager.teslaRefreshToken.isNullOrBlank()
    val vehicle = settingsManager.teslaVehicleName?.ifBlank { null } ?: settingsManager.teslaVehicleId
    val value = when {
        !configured -> stringResource(R.string.tesla_not_configured)
        !connected -> stringResource(R.string.tesla_not_connected)
        vehicle == null -> stringResource(R.string.tesla_connected_no_vehicle)
        else -> stringResource(R.string.tesla_connected_vehicle, vehicle)
    }

    DashboardCard(
        title = stringResource(R.string.tesla_card_title),
        value = value,
        icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        onClick = { navController.navigate(ScreensName.TESLA.name) }
    )
}

@Composable
private fun MapCard(navController: NavController, settingsManager: SettingsManager) {
    val appName = navigationApps.find { it.packageName == settingsManager.preferredNavigationAppPackage }?.name
        ?: stringResource(R.string.map_no_app_chosen)
    val pollingLabel = if (settingsManager.teslaPollingEnabled) {
        stringResource(R.string.map_polling_auto)
    } else {
        stringResource(R.string.map_polling_manual)
    }

    DashboardCard(
        title = stringResource(R.string.map_card_title),
        value = stringResource(R.string.map_card_value, appName, pollingLabel),
        icon = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        onClick = { navController.navigate(ScreensName.SETTINGS.name) }
    )
}
