package app.minimaldialer.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.minimaldialer.core.model.ThemeMode

/** Colours with a fixed meaning that Material's scheme doesn't name. */
@Immutable
data class CallColors(
    val call: Color,
    val onCall: Color,
    val hangUp: Color,
    val onHangUp: Color,
    val recording: Color,
    val keypadKey: Color,
    val onKeypadKey: Color,
    val subtle: Color,
)

private val Charcoal = Color(0xFF121314)
private val CharcoalRaised = Color(0xFF1C1D20)
private val CharcoalHigh = Color(0xFF26272B)
private val Sage = Color(0xFF8CC7AE)
private val CallGreen = Color(0xFF2FB872)
private val HangUpRed = Color(0xFFE5484D)

private val DarkScheme = darkColorScheme(
    primary = Sage,
    onPrimary = Color(0xFF0D2219),
    primaryContainer = Color(0xFF233A30),
    onPrimaryContainer = Color(0xFFCDEBDD),
    secondary = Color(0xFFB4BCC8),
    onSecondary = Color(0xFF1E2329),
    background = Charcoal,
    onBackground = Color(0xFFE8E8E6),
    surface = Charcoal,
    onSurface = Color(0xFFE8E8E6),
    surfaceVariant = CharcoalRaised,
    onSurfaceVariant = Color(0xFFA9ABB0),
    surfaceContainer = CharcoalRaised,
    surfaceContainerHigh = CharcoalHigh,
    surfaceContainerLow = Color(0xFF17181A),
    outline = Color(0xFF3A3C40),
    outlineVariant = Color(0xFF2A2B2F),
    error = Color(0xFFFF8A80),
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF2E6B53),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD3EDE1),
    onPrimaryContainer = Color(0xFF0D2219),
    secondary = Color(0xFF545B66),
    background = Color(0xFFF6F6F4),
    onBackground = Color(0xFF17181A),
    surface = Color(0xFFF6F6F4),
    onSurface = Color(0xFF17181A),
    surfaceVariant = Color(0xFFECECE9),
    onSurfaceVariant = Color(0xFF5C5E63),
    surfaceContainer = Color(0xFFEEEEEB),
    surfaceContainerHigh = Color(0xFFE6E6E3),
    surfaceContainerLow = Color(0xFFF1F1EE),
    outline = Color(0xFFC6C7C4),
    outlineVariant = Color(0xFFDDDDDA),
)

private val DarkCallColors = CallColors(
    call = CallGreen, onCall = Color.White, hangUp = HangUpRed, onHangUp = Color.White,
    recording = Color(0xFFFF5A5F), keypadKey = CharcoalRaised, onKeypadKey = Color(0xFFF2F2F0),
    subtle = Color(0xFF8A8C91),
)

private val LightCallColors = CallColors(
    call = Color(0xFF1F9D5C), onCall = Color.White, hangUp = Color(0xFFD93D42), onHangUp = Color.White,
    recording = Color(0xFFD93D42), keypadKey = Color(0xFFE9E9E6), onKeypadKey = Color(0xFF17181A),
    subtle = Color(0xFF7A7C80),
)

val LocalCallColors = staticCompositionLocalOf { DarkCallColors }

private val AppTypography = Typography().let { base ->
    base.copy(
        displayLarge = TextStyle(fontSize = 44.sp, fontWeight = FontWeight.Light, letterSpacing = 1.sp),
        displayMedium = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Normal),
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Normal),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Medium),
    )
}

@Composable
fun MinimalDialerTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val scheme: ColorScheme = if (dark) DarkScheme else LightScheme
    androidx.compose.runtime.CompositionLocalProvider(LocalCallColors provides if (dark) DarkCallColors else LightCallColors) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
    }
}

object DialerTheme {
    val callColors: CallColors
        @Composable get() = LocalCallColors.current
}
