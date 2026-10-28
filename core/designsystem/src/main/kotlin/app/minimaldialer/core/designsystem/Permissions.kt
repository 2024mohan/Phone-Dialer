package app.minimaldialer.core.designsystem

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

fun Context.hasPermissions(vararg permissions: String): Boolean =
    permissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }

/** Permission state that refreshes on resume (the user may change it in system settings). */
class PermissionHandle internal constructor(
    granted: Boolean,
    private val launch: () -> Unit,
) {
    var granted by mutableStateOf(granted)
        internal set

    fun request() = launch()
}

@Composable
fun rememberPermissions(vararg permissions: String, onResult: (Boolean) -> Unit = {}): PermissionHandle {
    val context = LocalContext.current
    val perms = remember { permissions.toList().toTypedArray() }
    lateinit var handle: PermissionHandle
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val ok = perms.all { result[it] == true || context.hasPermissions(it) }
        handle.granted = ok
        onResult(ok)
    }
    handle = remember { PermissionHandle(context.hasPermissions(*perms)) { launcher.launch(perms) } }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { handle.granted = context.hasPermissions(*perms) }
    return handle
}
