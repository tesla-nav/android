import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

// Define the combined permission status
enum class PermissionStatus {
    GRANTED, // Permission has been granted
    DENIED, // Permission has been denied, but we can ask again
    DENIED_PERMANENTLY, // Permission has been denied and the user selected "Don't ask again"
    SHOW_RATIONALE // Permission was denied, and we should show a rationale before requesting again
}

data class PermissionHandlerState(
    val status: PermissionStatus,
    val requestPermission: () -> Unit,
)

@Composable
fun rememberPermissionHandler(
    permission: String,
    onGranted: (() -> Unit)? = null,
    onDenied: (() -> Unit)? = null,
    onShowRationale: (() -> Unit)? = null
): PermissionHandlerState {
    val context = LocalContext.current
    fun Context.findActivity(): Activity? {
        var ctx = this
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }
    val activity = remember(context) { context.findActivity() }

    var currentStatus by remember(permission) {
        val initialStatus = when {
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED -> {
                PermissionStatus.GRANTED
            }
            activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, permission) -> {
                PermissionStatus.SHOW_RATIONALE
            }
            else -> {
                PermissionStatus.DENIED
            }
        }
        mutableStateOf(initialStatus)
    }

    var justRequestedPermission by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        justRequestedPermission = false
        val newStatus: PermissionStatus
        if (isGranted) {
            newStatus = PermissionStatus.GRANTED
            currentStatus = newStatus
            onGranted?.invoke()
        } else {
            newStatus = if (activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)) {
                PermissionStatus.SHOW_RATIONALE
            } else {
                PermissionStatus.DENIED_PERMANENTLY
            }
            currentStatus = newStatus
            onDenied?.invoke() // Call onDenied for both SHOW_RATIONALE and DENIED_PERMANENTLY originating from a denial
            if (newStatus == PermissionStatus.SHOW_RATIONALE) {
                onShowRationale?.invoke()
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, permission, context, activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (!justRequestedPermission) {
                    val statusOnResume = when {
                        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED -> {
                            PermissionStatus.GRANTED
                        }
                        activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, permission) -> {
                            PermissionStatus.SHOW_RATIONALE
                        }
                        currentStatus == PermissionStatus.DENIED_PERMANENTLY -> PermissionStatus.DENIED_PERMANENTLY
                        else -> PermissionStatus.DENIED
                    }
                    if (currentStatus != statusOnResume) {
                        currentStatus = statusOnResume
                        // Potentially trigger callbacks if status changed due to settings change
                        // For example, if it became GRANTED, or if it became DENIED from GRANTED
                        // This might require more nuanced logic depending on desired behavior
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(permission, context, activity) {
        // This effect ensures the status is correct on initial composition or if key parameters change.
        // It's similar to the initial status determination.
        val initialCheckStatus = when {
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED -> {
                PermissionStatus.GRANTED
            }
            activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, permission) -> {
                PermissionStatus.SHOW_RATIONALE
            }
            else -> {
                if (currentStatus == PermissionStatus.DENIED_PERMANENTLY) { // Preserve DENIED_PERMANENTLY if already set
                    PermissionStatus.DENIED_PERMANENTLY
                } else {
                    PermissionStatus.DENIED
                }
            }
        }
        if (currentStatus != initialCheckStatus) {
            currentStatus = initialCheckStatus
        }
    }

    return PermissionHandlerState(
        status = currentStatus,
        requestPermission = {
            when (currentStatus) {
                PermissionStatus.GRANTED -> {
                    onGranted?.invoke() // Permission already granted
                }
                PermissionStatus.SHOW_RATIONALE -> {
                    if (onShowRationale != null) {
                        onShowRationale.invoke()
                    } else {
                        justRequestedPermission = true
                        permissionLauncher.launch(permission)
                    }
                }
                PermissionStatus.DENIED -> {
                    justRequestedPermission = true
                    permissionLauncher.launch(permission)
                }
                PermissionStatus.DENIED_PERMANENTLY -> {
                    onDenied?.invoke() // User cannot be prompted, effectively a denial.
                    // Client should guide user to settings.
                }
            }
        }
    )
}
