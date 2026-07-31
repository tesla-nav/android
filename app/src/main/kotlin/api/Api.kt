package io.github.teslanav.app.api

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

sealed class State<out T> {
    object Idle : State<Nothing>()
    object Loading : State<Nothing>()
    data class Success<T>(val data: T) : State<T>()
    data class Error(val message: String) : State<Nothing>()
}

class RequestManager<T>(
    private val coroutineScope: CoroutineScope,
    private val onRequest: suspend () -> Result<T>
) {
    var state by mutableStateOf<State<T>>(State.Idle)
        private set

    fun execute() {
        state = State.Loading
        coroutineScope.launch {
            onRequest().fold(
                onSuccess = { responseData -> state = State.Success(responseData) },
                onFailure = { exception -> state = State.Error(exception.message ?: "Unknown error") }
            )
        }
    }

    fun reset() {
        state = State.Idle
    }
}

@Composable
fun <T> rememberRequestManager(
    onRequest: suspend () -> Result<T>,
    coroutineScope: CoroutineScope = rememberCoroutineScope()
): RequestManager<T> {
    return remember(onRequest, coroutineScope) {
        RequestManager(coroutineScope, onRequest)
    }
}

@Composable
fun <T> RequestStateView(
    requestManager: RequestManager<T>,
    idleContent: @Composable (call: () -> Unit) -> Unit,
    loadingContent: @Composable () -> Unit,
    errorContent: @Composable (errorMessage: String, retryCall: () -> Unit) -> Unit,
    successContent: @Composable (data: T, refreshCall: () -> Unit) -> Unit
) {
    when (val state = requestManager.state) {
        is State.Idle -> idleContent(requestManager::execute)
        is State.Loading -> loadingContent()
        is State.Error -> errorContent(state.message, requestManager::execute)
        is State.Success -> successContent(state.data, requestManager::execute)
    }
}
