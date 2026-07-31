package io.github.teslanav.app.ui.settings

import android.content.pm.PackageManager
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import io.github.teslanav.app.R
import io.github.teslanav.app.SettingsManager
import io.github.teslanav.app.services.helpers.TtsAnnouncer
import io.github.teslanav.app.ui.common.ScreenWrapper
import io.github.teslanav.app.ui.common.SectionCard

data class NavigationApp(val name: String, val packageName: String)

val navigationApps = listOf(
    NavigationApp("Google Maps", "com.google.android.apps.maps"),
    NavigationApp("Waze", "com.waze")
)

private fun isPackageInstalled(context: android.content.Context, packageName: String): Boolean {
    return try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(packageName, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }
}

@Composable
fun SettingsScreen(navController: NavHostController) {
    ScreenWrapper(
        navController = navController,
        title = stringResource(R.string.settings_title),
        showBackButton = true
    ) {
        val context = LocalContext.current
        val settingsManager = remember { SettingsManager(context) }
        var selectedNavAppPackage by remember { mutableStateOf(settingsManager.preferredNavigationAppPackage) }
        var pollingEnabled by remember { mutableStateOf(settingsManager.teslaPollingEnabled) }
        var ttsEnabled by remember { mutableStateOf(settingsManager.ttsEnabled) }
        val testTtsAnnouncer = remember { TtsAnnouncer(context) }
        DisposableEffect(Unit) {
            onDispose { testTtsAnnouncer.shutdown() }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SectionCard(title = stringResource(R.string.polling_section_title)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.polling_description),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = pollingEnabled,
                        onCheckedChange = {
                            pollingEnabled = it
                            settingsManager.teslaPollingEnabled = it
                            val label = context.getString(
                                if (it) R.string.polling_status_enabled else R.string.polling_status_disabled
                            )
                            Toast.makeText(context, context.getString(R.string.polling_toast, label), Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }

            SectionCard(title = stringResource(R.string.tts_section_title)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.tts_description),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = ttsEnabled,
                            onCheckedChange = {
                                ttsEnabled = it
                                settingsManager.ttsEnabled = it
                                val label = context.getString(
                                    if (it) R.string.tts_status_enabled else R.string.tts_status_disabled
                                )
                                Toast.makeText(context, context.getString(R.string.tts_toast, label), Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                    Button(onClick = { testTtsAnnouncer.speak(context.getString(R.string.tts_test_message)) }) {
                        Text(stringResource(R.string.tts_test_button))
                    }
                }
            }

            SectionCard(title = stringResource(R.string.nav_app_section_title)) {
                Column {
                    navigationApps.forEach { app ->
                        val installed = remember(app.packageName) { isPackageInstalled(context, app.packageName) }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .selectable(
                                    selected = (selectedNavAppPackage == app.packageName),
                                    onClick = {
                                        selectedNavAppPackage = app.packageName
                                        settingsManager.preferredNavigationAppPackage = app.packageName
                                        val message = if (installed) {
                                            context.getString(R.string.nav_app_selected_toast, app.name)
                                        } else {
                                            context.getString(R.string.nav_app_selected_not_installed_toast, app.name)
                                        }
                                        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                                    }
                                ),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (selectedNavAppPackage == app.packageName),
                                onClick = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(text = app.name)
                                Text(
                                    text = stringResource(if (installed) R.string.nav_app_installed else R.string.nav_app_not_installed),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (installed) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.error
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
