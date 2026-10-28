package app.minimaldialer.contacts.ui

import android.Manifest
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.minimaldialer.contacts.domain.RecentEntry
import app.minimaldialer.core.designsystem.Avatar
import app.minimaldialer.core.designsystem.DialerTheme
import app.minimaldialer.core.designsystem.EmptyState
import app.minimaldialer.core.designsystem.PermissionCard
import app.minimaldialer.core.designsystem.SectionHeader
import app.minimaldialer.core.designsystem.rememberPermissions
import app.minimaldialer.core.model.CallDirection
import app.minimaldialer.core.model.Contact

@Composable
fun ContactsScreen(viewModel: ContactsViewModel, onCall: (String) -> Unit, contentPadding: PaddingValues) {
    val permission = rememberPermissions(Manifest.permission.READ_CONTACTS)
    if (!permission.granted) {
        Column(Modifier.padding(contentPadding)) {
            PermissionCard(
                title = "Contacts",
                body = "Allow access to show and search your contacts. They stay on this device.",
                buttonLabel = "Allow",
                onClick = permission::request,
            )
        }
        return
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize().padding(contentPadding)) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search contacts") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            ),
        )
        when {
            state.loading -> Unit
            state.contacts.isEmpty() && state.query.isNotBlank() -> EmptyState(Icons.Filled.Search, "No matches")
            state.contacts.isEmpty() -> EmptyState(Icons.Outlined.People, "No contacts with phone numbers")
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(state.contacts, key = { it.id }) { contact ->
                    PersonRow(
                        title = contact.displayName,
                        subtitle = contact.primaryNumber?.let { n -> listOfNotNull(n.label, n.number).joinToString(" · ") },
                        onClick = { contact.primaryNumber?.let { onCall(it.number) } },
                        trailing = {
                            IconButton(onClick = { viewModel.setStarred(contact, !contact.starred) }) {
                                Icon(
                                    if (contact.starred) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                    contentDescription = if (contact.starred) "Remove from favourites" else "Add to favourites",
                                    tint = if (contact.starred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun FavoritesScreen(viewModel: ContactsViewModel, onCall: (String) -> Unit, contentPadding: PaddingValues) {
    val permission = rememberPermissions(Manifest.permission.READ_CONTACTS)
    if (!permission.granted) {
        Column(Modifier.padding(contentPadding)) {
            PermissionCard("Favourites", "Favourites are your starred contacts. Allow contacts access to show them.", "Allow", permission::request)
        }
        return
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.loading && state.favorites.isEmpty()) {
        EmptyState(
            Icons.Outlined.StarBorder, "No favourites yet",
            "Star a contact in the Contacts tab to pin it here.",
            Modifier.padding(contentPadding),
        )
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 104.dp),
        contentPadding = PaddingValues(
            start = 12.dp, end = 12.dp,
            top = contentPadding.calculateTopPadding() + 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 12.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(state.favorites, key = { it.id }) { FavoriteTile(it, onCall) }
    }
}

@Composable
private fun FavoriteTile(contact: Contact, onCall: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { contact.primaryNumber?.let { onCall(it.number) } }
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Avatar(contact.displayName, size = 72.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            contact.displayName, style = MaterialTheme.typography.bodyMedium, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun RecentsScreen(viewModel: RecentsViewModel, onCall: (String) -> Unit, contentPadding: PaddingValues) {
    val permission = rememberPermissions(Manifest.permission.READ_CALL_LOG, Manifest.permission.READ_CONTACTS)
    if (!permission.granted) {
        Column(Modifier.padding(contentPadding)) {
            PermissionCard(
                "Recent calls",
                "Allow call log and contacts access to show recent calls. Android only offers call-log access to the default phone app.",
                "Allow", permission::request,
            )
        }
        return
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.loading && state.entries.isEmpty()) {
        EmptyState(Icons.Outlined.History, "No recent calls", modifier = Modifier.padding(contentPadding))
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = contentPadding) {
        item { SectionHeader("Recent") }
        items(state.entries, key = { it.call.id }) { entry -> RecentRow(entry, onCall) }
    }
}

@Composable
private fun RecentRow(entry: RecentEntry, onCall: (String) -> Unit) {
    val call = entry.call
    val colors = DialerTheme.callColors
    val (icon, tint, label) = when (call.direction) {
        CallDirection.INCOMING -> Triple(Icons.AutoMirrored.Filled.CallReceived, null, "Incoming")
        CallDirection.OUTGOING -> Triple(Icons.AutoMirrored.Filled.CallMade, null, "Outgoing")
        CallDirection.MISSED -> Triple(Icons.AutoMirrored.Filled.CallMissed, colors.hangUp, "Missed")
        CallDirection.REJECTED -> Triple(Icons.AutoMirrored.Filled.CallMissed, null, "Declined")
        CallDirection.BLOCKED -> Triple(Icons.Filled.Block, null, "Blocked")
        CallDirection.VOICEMAIL, CallDirection.UNKNOWN -> Triple(Icons.AutoMirrored.Filled.CallReceived, null, "Call")
    }
    val time = DateUtils.getRelativeTimeSpanString(
        call.timestampMillis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE,
    ).toString()
    val subtitle = buildString {
        if (entry.contact != null && call.number.isNotBlank()) append(call.number).append(" · ")
        append(label).append(" · ").append(time)
    }
    PersonRow(
        title = entry.title,
        subtitle = subtitle,
        leadingIcon = icon,
        leadingTint = tint,
        onClick = { if (call.number.isNotBlank()) onCall(call.number) },
    )
}
