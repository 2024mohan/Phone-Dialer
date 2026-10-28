package app.minimaldialer.core.designsystem

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

/** Circular initials avatar with a stable, muted colour per name. */
@Composable
fun Avatar(name: String, modifier: Modifier = Modifier, size: Dp = 44.dp) {
    val palette = listOf(
        Color(0xFF4F6D7A), Color(0xFF6B5B7B), Color(0xFF5B7B6B), Color(0xFF7B6B5B),
        Color(0xFF5B6B7B), Color(0xFF7A5C61), Color(0xFF55707A), Color(0xFF6E7550),
    )
    val bg = palette[abs(name.hashCode()) % palette.size]
    val initials = name.split(' ').filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { "#" }
        .takeIf { it.first().isLetter() } ?: "#"
    Box(
        modifier = modifier.size(size).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, color = Color.White, fontSize = (size.value * 0.38f).sp, fontWeight = FontWeight.Medium)
    }
}

/** Pulsing red "REC" chip. Shown whenever a recording is running; it is not optional. */
@Composable
fun RecordingIndicator(label: String, modifier: Modifier = Modifier) {
    val pulse by rememberInfiniteTransition(label = "rec").animateFloat(
        initialValue = 1f, targetValue = 0.3f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "recAlpha",
    )
    val red = DialerTheme.callColors.recording
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(red.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .semantics { contentDescription = "Recording in progress. $label" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).alpha(pulse).clip(CircleShape).background(red))
        Spacer(Modifier.width(8.dp))
        Text(label, color = red, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String? = null, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (body != null) {
            Spacer(Modifier.height(8.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

/** Explains why a permission is needed and offers one button to request it. */
@Composable
fun PermissionCard(title: String, body: String, buttonLabel: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(14.dp))
            Button(onClick = onClick) { Text(buttonLabel) }
        }
    }
}
