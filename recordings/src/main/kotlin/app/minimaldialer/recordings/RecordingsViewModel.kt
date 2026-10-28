package app.minimaldialer.recordings

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.minimaldialer.recording.domain.RecordingLibrary
import app.minimaldialer.recording.domain.RecordingMeta
import app.minimaldialer.recordings.domain.GroupBy
import app.minimaldialer.recordings.domain.RecordingCatalog
import app.minimaldialer.recordings.domain.RecordingGroup
import app.minimaldialer.recordings.domain.RenameRules
import app.minimaldialer.recordings.domain.SortOrder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId

data class RecordingsUiState(
    val groups: List<RecordingGroup> = emptyList(),
    val totalCount: Int = 0,
    val query: String = "",
    val sort: SortOrder = SortOrder.NEWEST,
    val groupBy: GroupBy = GroupBy.DATE,
)

class RecordingsViewModel(
    private val library: RecordingLibrary,
    val player: RecordingPlayer,
    private val sharer: RecordingSharer,
) : ViewModel() {

    private data class Filters(val query: String = "", val sort: SortOrder = SortOrder.NEWEST, val groupBy: GroupBy = GroupBy.DATE)
    private val filters = MutableStateFlow(Filters())

    private val _messages = MutableStateFlow<String?>(null)
    val messages: StateFlow<String?> = _messages

    private val _shareIntents = MutableStateFlow<Intent?>(null)
    val shareIntents: StateFlow<Intent?> = _shareIntents

    val state: StateFlow<RecordingsUiState> = combine(library.items, filters) { items, f ->
        val visible = RecordingCatalog.sort(RecordingCatalog.filter(items, f.query), f.sort)
        RecordingsUiState(
            groups = RecordingCatalog.group(visible, f.groupBy, ZoneId.systemDefault(), LocalDate.now()),
            totalCount = items.size,
            query = f.query, sort = f.sort, groupBy = f.groupBy,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecordingsUiState())

    init {
        viewModelScope.launch { library.refresh() }
    }

    fun onQuery(q: String) { filters.value = filters.value.copy(query = q) }
    fun onSort(s: SortOrder) { filters.value = filters.value.copy(sort = s) }
    fun onGroupBy(g: GroupBy) { filters.value = filters.value.copy(groupBy = g) }

    /** Returns an error message for the dialog, or null when the rename was applied. */
    fun rename(meta: RecordingMeta, input: String): String? = when (val r = RenameRules.validate(input)) {
        RenameRules.Result.TooLong -> "Use at most ${RenameRules.MAX_LENGTH} characters."
        is RenameRules.Result.Valid -> {
            viewModelScope.launch {
                runCatching { library.rename(meta.id, r.title) }.onFailure { _messages.value = "Couldn't rename: ${it.message}" }
            }
            null
        }
    }

    fun delete(meta: RecordingMeta) {
        if (player.state.value.recordingId == meta.id) player.stop()
        viewModelScope.launch {
            val n = library.delete(listOf(meta.id))
            _messages.value = if (n == 1) "Recording deleted" else "Couldn't delete the recording"
        }
    }

    fun share(meta: RecordingMeta) {
        viewModelScope.launch {
            runCatching { sharer.shareIntent(meta) }
                .onSuccess { _shareIntents.value = it }
                .onFailure { _messages.value = "Couldn't prepare the recording for sharing: ${it.message}" }
        }
    }

    fun shareHandled() { _shareIntents.value = null }
    fun messageShown() { _messages.value = null }

    override fun onCleared() {
        player.stop()
    }
}
