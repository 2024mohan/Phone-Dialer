package app.minimaldialer.settings

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.minimaldialer.core.designsystem.DialerTheme
import app.minimaldialer.core.designsystem.SectionHeader
import app.minimaldialer.core.designsystem.rememberPermissions
import app.minimaldialer.core.model.ByteSizes
import app.minimaldialer.core.model.Durations
import app.minimaldialer.core.model.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    isDefaultDialer: () -> Boolean,
    defaultDialerIntent: () -> Intent?,
    appVersion: String,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var isDefault by remember { mutableStateOf(isDefaultDialer()) }
    var confirmDeleteAll by remember { mutableStateOf(false) }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = isDefaultDialer()
        viewModel.refresh()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        isDefault = isDefaultDialer()
        viewModel.refresh()
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
        )
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item { SectionHeader("Phone app") }
            item {
                StatusRow(
                    ok = isDefault,
                    title = "Default phone app",
                    detail = if (isDefault) "Calls are handled by this app." else "Needed for the in-call screen, recent calls and incoming calls.",
                    action = if (isDefault) null else ("Set" to {
                        defaultDialerIntent()?.let { roleLauncher.launch(it) } ?: Unit
                    }),
                )
            }
            item { PermissionRow("Phone", "Place calls", Manifest.permission.CALL_PHONE) }
            item { PermissionRow("Contacts", "Names, search and favourites", Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS) }
            item { PermissionRow("Call log", "Recent calls", Manifest.permission.READ_CALL_LOG) }
            item { PermissionRow("Microphone", "Recording", Manifest.permission.RECORD_AUDIO) }
            if (Build.VERSION.SDK_INT >= 33) {
                item { PermissionRow("Notifications", "Incoming calls and the recording indicator", Manifest.permission.POST_NOTIFICATIONS) }
            }

            item { SectionHeader("Call recording on this device") }
            item {
                InfoCard {
                    Text("${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    val cap = state.capability
                    if (cap != null) {
                        Text(CapabilityText.headline(cap), style = MaterialTheme.typography.bodyLarge)
                        cap.limitations.forEach {
                            Spacer(Modifier.height(6.dp))
                            Text("• " + CapabilityText.limitation(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        cap.oemHint?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(it, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Recording starts only when you tap Record. While it runs, the in-call screen shows a recording indicator and " +
                            "a notification stays visible; Android's own microphone indicator is never hidden. " +
                            "Laws on recording calls differ by country and state. Some require every party's consent. " +
                            "You are responsible for telling the other person.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item { SectionHeader("Storage") }
            item {
                val s = state.storage
                InfoCard {
                    if (s == null) {
                        Text("Calculating…")
                    } else {
                        Text("${s.count} recording${if (s.count == 1) "" else "s"} · ${ByteSizes.format(s.totalBytes)} · ${Durations.formatMillis(s.totalDurationMillis)}")
                        if (s.silentCount > 0) Text("${s.silentCount} contain only silence", color = DialerTheme.callColors.hangUp, style = MaterialTheme.typography.bodyMedium)
                        if (s.freeBytes >= 0) {
                            Text(
                                "Free space: ${ByteSizes.format(s.freeBytes)}" + if (s.lowSpace) " (low; recordings may stop early)" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (s.lowSpace) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row {
                            OutlinedButton(onClick = { viewModel.deleteOlderThan(30) }) { Text("Delete > 30 days") }
                            Spacer(Modifier.width(8.dp))
                            OutlinedButton(onClick = { viewModel.deleteOlderThan(90) }) { Text("> 90 days") }
                        }
                        TextButton(onClick = { confirmDeleteAll = true }, enabled = s.count > 0) {
                            Text("Delete all recordings", color = MaterialTheme.colorScheme.error)
                        }
                        if (state.sharedCopiesBytes > 0) {
                            TextButton(onClick = viewModel::clearSharedCopies) { Text("Remove unencrypted share copies (${ByteSizes.format(state.sharedCopiesBytes)})") }
                        }
                    }
                    message?.let {
                        Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                        LaunchedEffect(it) { kotlinx.coroutines.delay(3_000); viewModel.messageShown() }
                    }
                }
            }

            item { SectionHeader("Privacy") }
            item {
                InfoCard {
                    Text(
                        "This app has no internet permission and cannot upload anything. Recordings and their details are encrypted " +
                            "(AES-256-GCM via Google Tink) with a key protected by the Android Keystore, are excluded from backups, " +
                            "and are deleted when the app is uninstalled. Sharing creates an unencrypted copy only after you confirm.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            item { SectionHeader("Appearance") }
            ThemeMode.entries.forEach { mode ->
                item {
                    Row(
                        Modifier.fillMaxWidth()
                            .selectable(selected = state.theme == mode, role = Role.RadioButton) { viewModel.setTheme(mode) }
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = state.theme == mode, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            when (mode) {
                                ThemeMode.SYSTEM -> "Follow system"
                                ThemeMode.LIGHT -> "Light"
                                ThemeMode.DARK -> "Dark"
                            },
                        )
                    }
                }
            }
            item {
                Text(
                    "Version $appVersion",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp),
                )
            }
        }
    }

    if (confirmDeleteAll) {
        AlertDialog(
            onDismissRequest = { confirmDeleteAll = false },
            title = { Text("Delete all recordings?") },
            text = { Text("This permanently deletes every recording on this phone.") },
            confirmButton = { TextButton(onClick = { viewModel.deleteAll(); confirmDeleteAll = false }) { Text("Delete all") } },
            dismissButton = { TextButton(onClick = { confirmDeleteAll = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(16.dp)) { content() }
    }
}

@Composable
private fun StatusRow(ok: Boolean, title: String, detail: String, action: Pair<String, () -> Unit>?) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(
            if (ok) Icons.Filled.CheckCircle else Icons.Outlined.ErrorOutline, contentDescription = if (ok) "Granted" else "Not granted",
            tint = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        action?.let { (label, onClick) -> TextButton(onClick = onClick) { Text(label) } }
    }
}

@Composable
private fun PermissionRow(title: String, detail: String, vararg permissions: String) {
    val context = LocalContext.current
    val handle = rememberPermissions(*permissions)
    var asked by remember { mutableStateOf(false) }
    StatusRow(
        ok = handle.granted,
        title = title,
        detail = detail,
        action = when {
            handle.granted -> null
            // After one refusal Android may stop showing the dialog; offer the system page instead.
            asked -> "Open settings" to {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
            else -> "Allow" to { asked = true; handle.request() }
        },
    )
}
