package app.minimaldialer.dialer

import android.Manifest
import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.provider.Settings
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.minimaldialer.contacts.domain.ContactMatch
import app.minimaldialer.core.designsystem.Avatar
import app.minimaldialer.core.designsystem.DialerTheme
import app.minimaldialer.core.designsystem.KeySpec
import app.minimaldialer.core.designsystem.Keypad
import app.minimaldialer.core.designsystem.rememberPermissions
import app.minimaldialer.dialer.domain.DialKey
import java.util.Locale

val DialKeySpecs: List<List<KeySpec>> = DialKey.layout.map { row -> row.map { KeySpec(it.symbol, it.letters) } }

/**
 * Full-screen dialer. Everything the thumb needs (keypad, call, backspace) sits in the lower
 * two-thirds; the number and matches sit above.
 */
@Composable
fun DialerScreen(
    viewModel: DialerViewModel,
    contentPadding: PaddingValues,
    onOpenSettings: () -> Unit,
    banner: (@Composable () -> Unit)? = null,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val callPermission = rememberPermissions(Manifest.permission.CALL_PHONE) { granted -> if (granted) viewModel.call() }
    val tones = rememberDtmfFeedback()

    LaunchedEffect(message) {
        if (message == DialerMessage.NeedsCallPermission) {
            viewModel.messageShown()
            callPermission.request()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.End) {
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        banner?.invoke()
        (message as? DialerMessage.CallFailed)?.let {
            Text(
                "Couldn't place the call: ${it.reason}", color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 24.dp),
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
            Suggestions(state.suggestions.takeIf { state.input.text.isNotEmpty() }.orEmpty()) { viewModel.call(it) }
        }
        NumberDisplay(state.input.text, state.exactMatchName)
        Spacer(Modifier.height(20.dp))
        Keypad(
            rows = DialKeySpecs,
            onPress = { c ->
                tones.play(c)
                DialKey.fromSymbol(c)?.let(viewModel::press)
            },
            onLongPress = { c ->
                DialKey.fromSymbol(c)?.let { key ->
                    if (key.longPressSymbol != null) viewModel.longPress(key) else viewModel.press(key)
                }
            },
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(20.dp))
        CallRow(
            canBackspace = !state.input.isEmpty,
            onCall = { viewModel.call() },
            onBackspace = viewModel::backspace,
            onClear = viewModel::clear,
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun NumberDisplay(text: String, name: String?) {
    val context = LocalContext.current
    val formatted = remember(text) { formatForDisplay(context, text) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 24.dp)) {
        Text(
            text = name.orEmpty(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.height(24.dp),
        )
        val size = when {
            formatted.length <= 12 -> 40.sp
            formatted.length <= 16 -> 32.sp
            else -> 24.sp
        }
        Text(
            text = formatted,
            fontSize = size,
            fontWeight = FontWeight.Light,
            maxLines = 1,
            textAlign = TextAlign.Center,
            overflow = TextOverflow.StartEllipsis,
            modifier = Modifier.fillMaxWidth().height(52.dp).semantics { contentDescription = "Number: $text" },
        )
    }
}

@Composable
private fun Suggestions(matches: List<ContactMatch>, onCall: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        matches.forEach { m ->
            val number = m.matchedNumber ?: m.contact.primaryNumber?.number ?: return@forEach
            Surface(
                onClick = { onCall(number) },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(m.contact.displayName, size = 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(m.contact.displayName, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(number, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CallRow(canBackspace: Boolean, onCall: () -> Unit, onBackspace: () -> Unit, onClear: () -> Unit) {
    val colors = DialerTheme.callColors
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 40.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(Modifier.size(64.dp))
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(colors.call)
                .combinedClickable(role = Role.Button, onClickLabel = "Call", onClick = onCall),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Call, contentDescription = "Call", tint = colors.onCall, modifier = Modifier.size(34.dp))
        }
        Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            androidx.compose.animation.AnimatedVisibility(visible = canBackspace, enter = fadeIn(), exit = fadeOut()) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .combinedClickable(
                            role = Role.Button,
                            onClickLabel = "Delete digit",
                            onLongClickLabel = "Clear number",
                            onLongClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onClear()
                            },
                            onClick = onBackspace,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.Backspace, contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Shown on the keypad when this app is not the default phone app. */
@Composable
fun DefaultDialerBanner(onRequest: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Set as default phone app to show the in-call screen and recent calls.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onRequest) { Text("Set") }
        }
    }
}

private fun formatForDisplay(context: Context, raw: String): String {
    if (raw.isEmpty() || raw.any { it == '*' || it == '#' || it == ',' || it == ';' }) return raw
    val iso = context.getSystemService(TelephonyManager::class.java)?.networkCountryIso?.takeIf { it.isNotBlank() }
        ?: Locale.getDefault().country
    return PhoneNumberUtils.formatNumber(raw, iso.uppercase(Locale.ROOT)) ?: raw
}

/** Local key-press tones, respecting the system "dial pad tones" setting. */
private class DtmfFeedback(private val context: Context) {
    private var generator: ToneGenerator? = null

    fun play(c: Char) {
        val enabled = Settings.System.getInt(context.contentResolver, Settings.System.DTMF_TONE_WHEN_DIALING, 1) == 1
        if (!enabled) return
        val tone = when (c) {
            in '0'..'9' -> ToneGenerator.TONE_DTMF_0 + (c - '0')
            '*' -> ToneGenerator.TONE_DTMF_S
            '#' -> ToneGenerator.TONE_DTMF_P
            else -> return
        }
        val gen = generator ?: runCatching { ToneGenerator(AudioManager.STREAM_DTMF, 70) }.getOrNull()?.also { generator = it }
        gen?.startTone(tone, 120)
    }

    fun release() {
        generator?.release()
        generator = null
    }
}

@Composable
private fun rememberDtmfFeedback(): DtmfFeedback {
    val context = LocalContext.current
    val feedback = remember { DtmfFeedback(context.applicationContext) }
    DisposableEffect(Unit) { onDispose { feedback.release() } }
    return feedback
}
