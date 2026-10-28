package app.minimaldialer.recordings

import android.text.format.DateFormat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.minimaldialer.core.designsystem.DialerTheme
import app.minimaldialer.core.designsystem.EmptyState
import app.minimaldialer.core.designsystem.SectionHeader
import app.minimaldialer.core.model.ByteSizes
import app.minimaldialer.core.model.Durations
import app.minimaldialer.recording.domain.CaptureQuality
import app.minimaldialer.recording.domain.RecordingMeta
import app.minimaldialer.recordings.domain.GroupBy
import app.minimaldialer.recordings.domain.GroupKey
import app.minimaldialer.recordings.domain.RelativeDay
import app.minimaldialer.recordings.domain.SortOrder
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Date

@Composable
fun RecordingsScreen(viewModel: RecordingsViewModel, contentPadding: PaddingValues, onOpenSettings: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val player by viewModel.player.state.collectAsStateWithLifecycle()
    val message by viewModel.messages.collectAsStateWithLifecycle()
    val shareIntent by viewModel.shareIntents.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var renaming by remember { mutableStateOf<RecordingMeta?>(null) }
    var deleting by remember { mutableStateOf<RecordingMeta?>(null) }
    var confirmShare by remember { mutableStateOf<RecordingMeta?>(null) }

    LaunchedEffect(shareIntent) {
        shareIntent?.let {
            runCatching { context.startActivity(it) }
            viewModel.shareHandled()
        }
    }

    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Recordings", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, contentDescription = "Recording storage and settings") }
        }
        if (state.totalCount == 0) {
            EmptyState(
                Icons.Outlined.Mic, "No recordings",
                "Recordings you make during calls appear here. They are encrypted and stored only on this phone.",
            )
            return@Column
        }
        OutlinedTextField(
            value = state.query, onValueChange = viewModel::onQuery,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search by name or number") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true, shape = RoundedCornerShape(28.dp),
        )
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(state.groupBy == GroupBy.DATE, onClick = { viewModel.onGroupBy(GroupBy.DATE) }, label = { Text("By date") })
            FilterChip(state.groupBy == GroupBy.CONTACT, onClick = { viewModel.onGroupBy(GroupBy.CONTACT) }, label = { Text("By contact") })
            Spacer(Modifier.weight(1f))
            SortMenu(state.sort, viewModel::onSort)
        }
        message?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            LaunchedEffect(it) { kotlinx.coroutines.delay(3_000); viewModel.messageShown() }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            state.groups.forEach { group ->
                item(key = "h-${group.key}") { SectionHeader(headerText(group.key)) }
                items(group.items, key = { it.id }) { meta ->
                    RecordingCard(
                        meta = meta,
                        player = player.takeIf { it.recordingId == meta.id },
                        onToggle = { viewModel.player.toggle(meta.id, meta.durationMillis) },
                        onSeek = viewModel.player::seekTo,
                        onRename = { renaming = meta },
                        onShare = { confirmShare = meta },
                        onDelete = { deleting = meta },
                    )
                }
            }
        }
    }

    renaming?.let { meta -> RenameDialog(meta, onDismiss = { renaming = null }) { input -> viewModel.rename(meta, input).also { if (it == null) renaming = null } } }
    deleting?.let { meta ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete recording?") },
            text = { Text("\"${meta.displayTitle}\" will be permanently deleted from this phone.") },
            confirmButton = { TextButton(onClick = { viewModel.delete(meta); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
    confirmShare?.let { meta ->
        AlertDialog(
            onDismissRequest = { confirmShare = null },
            title = { Text("Share recording?") },
            text = {
                Text(
                    "An unencrypted copy will be handed to the app you choose next, which may upload it. " +
                        "This app never uploads recordings itself.",
                )
            },
            confirmButton = { TextButton(onClick = { viewModel.share(meta); confirmShare = null }) { Text("Choose app") } },
            dismissButton = { TextButton(onClick = { confirmShare = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SortMenu(current: SortOrder, onSort: (SortOrder) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Column {
        AssistChip(
            onClick = { open = true },
            label = { Text(sortLabel(current)) },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = null) },
        )
        DropdownMenu(open, onDismissRequest = { open = false }) {
            SortOrder.entries.forEach { s ->
                DropdownMenuItem(text = { Text(sortLabel(s)) }, onClick = { open = false; onSort(s) })
            }
        }
    }
}

private fun sortLabel(s: SortOrder) = when (s) {
    SortOrder.NEWEST -> "Newest"
    SortOrder.OLDEST -> "Oldest"
    SortOrder.LONGEST -> "Longest"
    SortOrder.LARGEST -> "Largest"
}

private fun headerText(key: GroupKey): String = when (key) {
    is GroupKey.Day -> when (key.relative) {
        RelativeDay.TODAY -> "Today"
        RelativeDay.YESTERDAY -> "Yesterday"
        RelativeDay.OTHER -> key.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    }
    is GroupKey.Person -> key.label
    GroupKey.All -> "All"
}

@Composable
private fun RecordingCard(
    meta: RecordingMeta,
    player: PlayerState?,
    onToggle: () -> Unit,
    onSeek: (Float) -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val time = remember(meta.startedAtMillis) {
        val d = Date(meta.startedAtMillis)
        "${DateFormat.getMediumDateFormat(context).format(d)}, ${DateFormat.getTimeFormat(context).format(d)}"
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalIconButton(onClick = onToggle) {
                    Icon(
                        if (player?.playing == true) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (player?.playing == true) "Pause" else "Play",
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(meta.displayTitle, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    val numberPart = if (meta.displayTitle != meta.number && meta.number.isNotBlank()) "${meta.number} · " else ""
                    Text(
                        "$numberPart$time · ${Durations.formatMillis(meta.durationMillis)} · ${ByteSizes.format(meta.sizeBytes)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    QualityLabel(meta)
                }
                IconButton(onClick = { menu = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                    DropdownMenu(menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Rename") }, onClick = { menu = false; onRename() })
                        DropdownMenuItem(text = { Text("Share") }, onClick = { menu = false; onShare() })
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { menu = false; onDelete() })
                    }
                }
            }
            AnimatedVisibility(player != null) {
                Column(Modifier.padding(end = 12.dp)) {
                    player?.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                    if (player != null && player.durationMillis > 0) {
                        Slider(
                            value = (player.positionMillis.toFloat() / player.durationMillis).coerceIn(0f, 1f),
                            onValueChange = onSeek,
                        )
                        Row {
                            Text(Durations.formatMillis(player.positionMillis), style = MaterialTheme.typography.labelSmall)
                            Spacer(Modifier.weight(1f))
                            Text(Durations.formatMillis(player.durationMillis), style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QualityLabel(meta: RecordingMeta) {
    val (text, color) = when (meta.quality) {
        CaptureQuality.CALL_AUDIO -> "Call audio" to MaterialTheme.colorScheme.primary
        CaptureQuality.MICROPHONE_ONLY -> "Microphone only" to MaterialTheme.colorScheme.onSurfaceVariant
        CaptureQuality.SILENT -> (if (meta.silencedBySystem) "Silent: blocked by Android" else "Silent: no audio captured") to DialerTheme.callColors.hangUp
    }
    Text(text, style = MaterialTheme.typography.labelSmall, color = color)
}

@Composable
private fun RenameDialog(meta: RecordingMeta, onDismiss: () -> Unit, onConfirm: (String) -> String?) {
    var text by remember { mutableStateOf(meta.title ?: "") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = {
            Column {
                OutlinedTextField(
                    value = text, onValueChange = { text = it; error = null }, singleLine = true,
                    placeholder = { Text(meta.contactName ?: meta.number) },
                    isError = error != null,
                    supportingText = { Text(error ?: "Leave empty to use the contact name or number.") },
                )
                Spacer(Modifier.height(4.dp))
            }
        },
        confirmButton = { TextButton(onClick = { error = onConfirm(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
