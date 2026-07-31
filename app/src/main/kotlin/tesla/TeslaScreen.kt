package io.github.teslanav.app.tesla

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import io.github.teslanav.app.R
import io.github.teslanav.app.SettingsManager
import io.github.teslanav.app.api.RequestStateView
import io.github.teslanav.app.api.rememberRequestManager
import io.github.teslanav.app.ui.common.ScreenWrapper
import io.github.teslanav.app.ui.common.SectionCard
import kotlinx.coroutines.launch

@Composable
fun TeslaScreen(navController: NavHostController) {
    ScreenWrapper(
        navController = navController,
        title = stringResource(R.string.tesla_card_title),
        showBackButton = true
    ) {
        val context = LocalContext.current
        val settingsManager = remember { SettingsManager(context) }
        var clientId by remember { mutableStateOf(settingsManager.teslaClientId) }
        var clientSecret by remember { mutableStateOf(settingsManager.teslaClientSecret) }
        val client = remember(clientId, clientSecret) {
            TeslaClient(
                clientId = clientId.orEmpty(),
                clientSecret = clientSecret.orEmpty(),
                accessToken = settingsManager.teslaToken
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AppConfigSection(
                settingsManager = settingsManager,
                onSaved = { newClientId, newClientSecret ->
                    clientId = newClientId
                    clientSecret = newClientSecret
                }
            )
            if (clientId.isNullOrBlank() || clientSecret.isNullOrBlank()) {
                Text(
                    stringResource(R.string.tesla_config_missing_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else {
                LoginSection(client)
                ProductsSection(client)
            }
        }
    }
}

@Composable
private fun AppConfigSection(
    settingsManager: SettingsManager,
    onSaved: (String?, String?) -> Unit
) {
    var clientIdInput by remember { mutableStateOf(settingsManager.teslaClientId ?: "") }
    var clientSecretInput by remember { mutableStateOf(settingsManager.teslaClientSecret ?: "") }
    val focusManager = LocalFocusManager.current

    fun save() {
        val trimmedId = clientIdInput.trim().ifBlank { null }
        val trimmedSecret = clientSecretInput.trim().ifBlank { null }
        settingsManager.teslaClientId = trimmedId
        settingsManager.teslaClientSecret = trimmedSecret
        onSaved(trimmedId, trimmedSecret)
    }

    SectionCard(title = stringResource(R.string.tesla_config_section_title)) {
        Text(
            stringResource(R.string.tesla_config_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = clientIdInput,
            onValueChange = { clientIdInput = it },
            label = { Text(stringResource(R.string.client_id_label)) },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { if (!it.isFocused) save() },
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Next)
        )
        OutlinedTextField(
            value = clientSecretInput,
            onValueChange = { clientSecretInput = it },
            label = { Text(stringResource(R.string.client_secret_label)) },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { if (!it.isFocused) save() },
            singleLine = true,
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                save()
                focusManager.clearFocus()
            })
        )
    }
}

private sealed class LoginState {
    object Idle : LoginState()
    object WaitingForBrowser : LoginState()
    data class Error(val message: String) : LoginState()
    object Success : LoginState()
}

@Composable
private fun LoginSection(client: TeslaClient) {
    val context = LocalContext.current
    val authManager = remember { TeslaAuthManager(context) }
    val settingsManager = remember { SettingsManager(context) }
    val coroutineScope = rememberCoroutineScope()
    var loginState by remember {
        mutableStateOf<LoginState>(
            if (settingsManager.teslaRefreshToken.isNullOrBlank()) LoginState.Idle else LoginState.Success
        )
    }

    fun startLogin() {
        loginState = LoginState.WaitingForBrowser
        val state = try {
            authManager.startLogin()
        } catch (e: TeslaAuthException) {
            loginState = LoginState.Error(e.message ?: context.getString(R.string.unknown_error))
            return
        }
        coroutineScope.launch {
            authManager.awaitLoginResult(state, client).fold(
                onSuccess = {
                    loginState = LoginState.Success
                    Toast.makeText(context, context.getString(R.string.login_success_toast), Toast.LENGTH_SHORT).show()
                },
                onFailure = { error ->
                    val message = error.message ?: context.getString(R.string.unknown_error)
                    loginState = LoginState.Error(message)
                    Toast.makeText(context, context.getString(R.string.login_failed_toast, message), Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    SectionCard(title = stringResource(R.string.connexion_section_title)) {
        when (val currentState = loginState) {
            is LoginState.Idle -> Button(onClick = ::startLogin) { Text(stringResource(R.string.login_button)) }
            is LoginState.WaitingForBrowser -> {
                CircularProgressIndicator()
                Text(stringResource(R.string.login_waiting))
            }
            is LoginState.Error -> {
                Text(stringResource(R.string.error_prefix, currentState.message), color = MaterialTheme.colorScheme.error)
                Button(onClick = ::startLogin) { Text(stringResource(R.string.login_retry_button)) }
            }
            is LoginState.Success -> {
                Text(stringResource(R.string.login_success))
                TokenExpiryInfo(settingsManager.teslaToken)
                Button(onClick = ::startLogin) { Text(stringResource(R.string.login_reconnect_button)) }
            }
        }
    }
}

@Composable
private fun TokenExpiryInfo(accessToken: String?) {
    val context = LocalContext.current
    val expiry = accessToken?.let(::decodeJwtExpirySeconds)
    Text(
        text = if (expiry != null) {
            stringResource(R.string.token_expiry_known, formatTokenExpiry(context, expiry))
        } else {
            stringResource(R.string.token_expiry_unknown)
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun ProductsSection(client: TeslaClient) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager(context) }
    var selectedVehicleId by remember { mutableStateOf(settingsManager.teslaVehicleId) }
    val requestManager = rememberRequestManager({ client.getProducts() })

    fun selectVehicle(product: ProductEntry) {
        selectedVehicleId = product.id.toString()
        settingsManager.teslaVehicleId = selectedVehicleId
        settingsManager.teslaVehicleName = product.label
        Toast.makeText(context, context.getString(R.string.vehicle_selected_toast, product.label), Toast.LENGTH_SHORT).show()
    }

    SectionCard(title = stringResource(R.string.vehicle_section_title)) {
        RequestStateView(
            requestManager = requestManager,
            idleContent = { call -> Button(onClick = call) { Text(stringResource(R.string.load_vehicles_button)) } },
            loadingContent = { CircularProgressIndicator() },
            errorContent = { errorMessage, retryCall ->
                Text(stringResource(R.string.error_prefix, errorMessage), color = MaterialTheme.colorScheme.error)
                Button(onClick = retryCall) { Text(stringResource(R.string.login_retry_button)) }
            },
            successContent = { products, refreshCall ->
                LaunchedEffect(products) {
                    if (selectedVehicleId == null && products.size == 1) {
                        selectVehicle(products.first())
                    } else {
                        // Fix a stale stored name (e.g. empty display_name fetched before the VIN fallback).
                        val current = products.find { it.id.toString() == selectedVehicleId }
                        if (current != null && settingsManager.teslaVehicleName != current.label) {
                            settingsManager.teslaVehicleName = current.label
                        }
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    products.forEach { product ->
                        val productId = product.id.toString()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectVehicle(product) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedVehicleId == productId,
                                onClick = { selectVehicle(product) }
                            )
                            Text(product.label)
                        }
                    }
                }
                Button(onClick = refreshCall) { Text(stringResource(R.string.refresh_button)) }
            }
        )
    }
}
