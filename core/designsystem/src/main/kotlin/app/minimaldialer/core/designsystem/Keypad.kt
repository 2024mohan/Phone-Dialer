package app.minimaldialer.core.designsystem

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp

/** One key: the symbol and the small caption printed under it ("ABC", "+"). */
data class KeySpec(val symbol: Char, val caption: String)

/**
 * 4x3 circular keypad. Key size follows the available width (capped), so it fills a small
 * phone and stays thumb-sized on a large one.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Keypad(
    rows: List<List<KeySpec>>,
    onPress: (Char) -> Unit,
    modifier: Modifier = Modifier,
    onLongPress: ((Char) -> Unit)? = null,
    onRelease: (() -> Unit)? = null,
    maxKeySize: Dp = 80.dp,
) {
    val haptics = LocalHapticFeedback.current
    BoxWithConstraints(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val keySize = min(maxKeySize, maxWidth / 4.4f)
        val gap = keySize / 3.2f
        Column(verticalArrangement = Arrangement.spacedBy(gap / 1.6f), horizontalAlignment = Alignment.CenterHorizontally) {
            rows.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { spec ->
                        Key(
                            spec = spec,
                            size = keySize,
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onPress(spec.symbol)
                                onRelease?.invoke()
                            },
                            onLongClick = onLongPress?.let { cb ->
                                {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    cb(spec.symbol)
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Key(spec: KeySpec, size: Dp, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
    val colors = DialerTheme.callColors
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.keypadKey)
            .combinedClickable(
                interactionSource = interaction,
                indication = ripple(),
                role = Role.Button,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .semantics { contentDescription = spec.symbol.toString() },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = spec.symbol.toString(),
                fontSize = (size.value * 0.38f).sp,
                color = colors.onKeypadKey,
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = spec.caption.ifEmpty { " " },
                fontSize = (size.value * 0.13f).sp,
                letterSpacing = 1.5.sp,
                color = colors.subtle,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}
