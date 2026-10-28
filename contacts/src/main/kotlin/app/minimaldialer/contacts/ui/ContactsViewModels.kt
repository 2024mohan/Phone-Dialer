package app.minimaldialer.contacts.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.minimaldialer.contacts.domain.CallLogRepository
import app.minimaldialer.contacts.domain.ContactSearch
import app.minimaldialer.contacts.domain.ContactsRepository
import app.minimaldialer.contacts.domain.Favorites
import app.minimaldialer.contacts.domain.RecentEntry
import app.minimaldialer.contacts.domain.Recents
import app.minimaldialer.core.model.Contact
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ContactsUiState(
    val loading: Boolean = true,
    val query: String = "",
    val contacts: List<Contact> = emptyList(),
    val favorites: List<Contact> = emptyList(),
)

class ContactsViewModel(private val repository: ContactsRepository) : ViewModel() {
    private val query = MutableStateFlow("")

    val state: StateFlow<ContactsUiState> = combine(repository.contacts(), query) { all, q ->
        ContactsUiState(
            loading = false,
            query = q,
            contacts = if (q.isBlank()) all else ContactSearch.search(all, q, limit = 200).map { it.contact },
            favorites = Favorites.of(all),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ContactsUiState())

    fun onQueryChange(value: String) {
        query.value = value
    }

    fun setStarred(contact: Contact, starred: Boolean) {
        viewModelScope.launch { repository.setStarred(contact.id, starred) }
    }
}

data class RecentsUiState(val loading: Boolean = true, val entries: List<RecentEntry> = emptyList())

class RecentsViewModel(callLog: CallLogRepository, contacts: ContactsRepository) : ViewModel() {
    val state: StateFlow<RecentsUiState> = combine(callLog.recentCalls(), contacts.contacts()) { calls, all ->
        RecentsUiState(loading = false, entries = Recents.resolve(calls, all))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecentsUiState())
}
