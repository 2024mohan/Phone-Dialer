package app.minimaldialer.calling.ui

import android.Manifest
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.SwapCalls
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.minimaldialer.calling.domain.AudioRoute
import app.minimaldialer.calling.domain.CallState
import app.minimaldialer.core.designsystem.Avatar
import app.minimaldialer.core.designsystem.DialerTheme
import app.minimaldialer.core.designsystem.KeySpec
import app.minimaldialer.core.designsystem.Keypad
import app.minimaldialer.core.designsystem.RecordingIndicator
import app.minimaldialer.core.designsystem.rememberPermissions
import app.minimaldialer.dialer.domain.DialKey
import app.minimaldialer.recording.domain.CaptureSource

private val DtmfKeys = DialKey.layout.map { row -> row.map { KeySpec(it.symbol, it.letters) } }

@Composable
fun InCallScreen(viewModel: InCallViewModel, onFinished: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showKeypad by rememberSaveable { mutableStateOf(false) }
    var dtmfTyped by rememberSaveable { mutableStateOf("") }
    val primary = state.primary
    val colors = DialerTheme.callColors

    // Recording needs the microphone; notification permission makes the persistent
    // recording notification visible on Android 13+.
    val recordPermissions = if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS)
    } else {
        arrayOf(Manifest.permission.RECORD_AUDIO)
    }
    val micPermission = rememberPermissions(*recordPermissions) { _ -> viewModel.onRecordPermissionsResult() }

    if (primary == null) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onFinished() }
        return
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))
            Box(Modifier.height(36.dp), contentAlignment = Alignment.Center) {
                RecordingMessages.indicatorLabel(state.recorder)?.let { RecordingIndicator(it) }
            }
            Spacer(Modifier.height(24.dp))
            Avatar(primary.title, size = 104.dp)
            Spacer(Modifier.height(20.dp))
            Text(
                primary.title, style = MaterialTheme.typography.displayMedium, maxLines = 2,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
            if (primary.displayName != null && primary.number.isNotBlank()) {
                Text(primary.number, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = statusText(state),
                style = MaterialTheme.typography.titleMedium,
                color = if (primary.state == CallState.DISCONNECTED) colors.hangUp else MaterialTheme.colorScheme.primary,
            )
            state.secondary?.let { other ->
                Spacer(Modifier.height(12.dp))
                Surface(
                    onClick = viewModel::swap,
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                ) {
                    Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.SwapCalls, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("${other.title} · ${if (other.state == CallState.ON_HOLD) "on hold" else "waiting"} · Swap")
                    }
                }
            }
            RecordingMessages.outcome(state.recorder)?.let { msg ->
                Spacer(Modifier.height(12.dp))
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                    Column(Modifier.padding(14.dp)) {
                        Text(msg, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = viewModel::acknowledgeRecording, modifier = Modifier.align(Alignment.End)) { Text("OK") }
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            AnimatedContent(
                targetState = when {
                    primary.state == CallState.RINGING -> Panel.RINGING
                    showKeypad -> Panel.KEYPAD
                    else -> Panel.CONTROLS
                },
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "panel",
            ) { panel ->
                when (panel) {
                    Panel.RINGING -> RingingActions(onAnswer = viewModel::answer, onDecline = viewModel::decline)
                    Panel.KEYPAD -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(dtmfTyped, style = MaterialTheme.typography.headlineMedium, maxLines = 1, modifier = Modifier.height(40.dp))
                        Keypad(rows = DtmfKeys, onPress = { c -> dtmfTyped = (dtmfTyped + c).takeLast(24); viewModel.dtmf(c) }, maxKeySize = 68.dp)
                        TextButton(onClick = { showKeypad = false }) { Text("Hide keypad") }
                    }
                    Panel.CONTROLS -> Controls(
                        state = state,
                        onMute = viewModel::toggleMute,
                        onKeypad = { showKeypad = true },
                        onSpeaker = viewModel::toggleSpeaker,
                        onRoute = viewModel::setRoute,
                        onHold = viewModel::toggleHold,
                        onRecord = {
                            if (state.recorder.isBusy || micPermission.granted) viewModel.toggleRecording() else micPermission.request()
                        },
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            AnimatedVisibility(primary.state != CallState.RINGING && primary.state != CallState.DISCONNECTED, enter = fadeIn(), exit = fadeOut()) {
                RoundAction(Icons.Filled.CallEnd, "Hang up", colors.hangUp, colors.onHangUp, onClick = viewModel::hangUp)
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

private enum class Panel { RINGING, KEYPAD, CONTROLS }

private fun statusText(state: InCallUiState): String {
    val p = state.primary ?: return ""
    return when (p.state) {
        CallState.IDLE, CallState.DIALING -> "Calling…"
        CallState.RINGING -> "Incoming call"
        CallState.ACTIVE -> state.elapsedText ?: "Connected"
        CallState.ON_HOLD -> "On hold" + (state.elapsedText?.let { " · $it" } ?: "")
        CallState.DISCONNECTING -> "Ending…"
        CallState.DISCONNECTED -> p.disconnectReason?.let { "Call ended · $it" } ?: "Call ended"
    }
}

@Composable
private fun RingingActions(onAnswer: () -> Unit, onDecline: () -> Unit) {
    val colors = DialerTheme.callColors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RoundAction(Icons.Filled.CallEnd, "Decline", colors.hangUp, colors.onHangUp, onClick = onDecline)
            Spacer(Modifier.height(8.dp)); Text("Decline")
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            RoundAction(Icons.Filled.Call, "Answer", colors.call, colors.onCall, onClick = onAnswer)
            Spacer(Modifier.height(8.dp)); Text("Answer")
        }
    }
}

@Composable
private fun Controls(
    state: InCallUiState,
    onMute: () -> Unit,
    onKeypad: () -> Unit,
    onSpeaker: () -> Unit,
    onRoute: (AudioRoute) -> Unit,
    onHold: () -> Unit,
    onRecord: () -> Unit,
) {
    val primary = state.primary ?: return
    val connected = primary.state == CallState.ACTIVE || primary.state == CallState.ON_HOLD
    val audio = state.audio
    val hasExternalRoute = AudioRoute.BLUETOOTH in audio.supportedRoutes || AudioRoute.WIRED_HEADSET in audio.supportedRoutes
    var routeMenu by remember { mutableStateOf(false) }
    val recording = state.recorder.isBusy
    val micOnly = state.capability?.source == CaptureSource.MICROPHONE

    Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Toggle(if (audio.muted) Icons.Filled.MicOff else Icons.Filled.Mic, "Mute", audio.muted, enabled = primary.canMute, onClick = onMute)
            Toggle(Icons.Filled.Dialpad, "Keypad", false, onClick = onKeypad)
            Box {
                Toggle(
                    icon = when (audio.route) {
                        AudioRoute.BLUETOOTH -> Icons.Filled.Bluetooth
                        AudioRoute.WIRED_HEADSET -> Icons.Filled.Headset
                        AudioRoute.SPEAKER -> Icons.AutoMirrored.Filled.VolumeUp
                        else -> if (hasExternalRoute) Icons.Filled.PhoneInTalk else Icons.AutoMirrored.Filled.VolumeUp
                    },
                    label = if (hasExternalRoute) routeLabel(audio.route, audio.bluetoothDeviceName) else "Speaker",
                    checked = audio.route == AudioRoute.SPEAKER,
                    onClick = { if (hasExternalRoute) routeMenu = true else onSpeaker() },
                )
                DropdownMenu(expanded = routeMenu, onDismissRequest = { routeMenu = false }) {
                    audio.supportedRoutes.filter { it != AudioRoute.STREAMING }.forEach { r ->
                        DropdownMenuItem(
                            text = { Text(routeLabel(r, audio.bluetoothDeviceName)) },
                            onClick = { routeMenu = false; onRoute(r) },
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Toggle(Icons.Filled.Pause, if (primary.state == CallState.ON_HOLD) "Resume" else "Hold", primary.state == CallState.ON_HOLD, enabled = primary.canHold && connected, onClick = onHold)
            Toggle(
                icon = if (recording) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                label = when {
                    recording -> "Stop rec"
                    micOnly -> "Record (mic)"
                    else -> "Record"
                },
                checked = recording,
                enabled = connected || recording,
                checkedColor = DialerTheme.callColors.recording,
                onClick = onRecord,
            )
            // Keeps the grid balanced without adding a feature that isn't implemented.
            Spacer(Modifier.size(width = 76.dp, height = 1.dp))
        }
    }
}

private fun routeLabel(route: AudioRoute, btName: String?): String = when (route) {
    AudioRoute.EARPIECE -> "Phone"
    AudioRoute.SPEAKER -> "Speaker"
    AudioRoute.BLUETOOTH -> btName ?: "Bluetooth"
    AudioRoute.WIRED_HEADSET -> "Headset"
    AudioRoute.STREAMING -> "Streaming"
}

@Composable
private fun Toggle(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    enabled: Boolean = true,
    checkedColor: Color = MaterialTheme.colorScheme.primary,
    onClick: () -> Unit,
) {
    val bg = if (checked) checkedColor else MaterialTheme.colorScheme.surfaceContainerHigh
    val fg = if (checked) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onSurface
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(76.dp).semantics(mergeDescendants = true) {
            stateDescription = if (checked) "On" else "Off"
        },
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(bg.copy(alpha = if (enabled) 1f else 0.4f))
                .clickable(enabled = enabled, role = Role.Switch, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = fg.copy(alpha = if (enabled) 1f else 0.5f))
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun RoundAction(icon: ImageVector, description: String, bg: Color, fg: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .size(76.dp)
            .clip(CircleShape)
            .background(bg)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(34.dp))
    }
}
