package app.minimaldialer.settings

import android.os.StatFs
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.minimaldialer.core.model.ThemeMode
import app.minimaldialer.recording.domain.CallRecorder
import app.minimaldialer.recording.domain.RecordingCapability
import app.minimaldialer.recording.domain.RecordingLibrary
import app.minimaldialer.recordings.domain.RecordingCatalog
import app.minimaldialer.recordings.domain.StorageSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class SettingsUiState(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val capability: RecordingCapability? = null,
    val storage: StorageSummary? = null,
    val sharedCopiesBytes: Long = 0,
)

/** Decrypted share copies live in the recordings module; settings only needs to size and clear them. */
interface SharedCopies {
    fun bytes(): Long
    fun clear(): Long
}

class SettingsViewModel(
    private val preferences: AppPreferences,
    private val recorder: CallRecorder,
    private val library: RecordingLibrary,
    private val sharedCopies: SharedCopies,
    private val storageDir: File,
) : ViewModel() {

    private val storage = MutableStateFlow<Pair<StorageSummary, Long>?>(null)
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message

    val state: StateFlow<SettingsUiState> = combine(preferences.themeMode, recorder.capability, storage) { theme, cap, st ->
        SettingsUiState(theme, cap, st?.first, st?.second ?: 0)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        refresh()
    }

    fun refresh() {
        recorder.refreshCapability()
        viewModelScope.launch {
            library.refresh()
            storage.value = withContext(Dispatchers.IO) {
                val free = runCatching { StatFs(storageDir.path).availableBytes }.getOrDefault(-1L)
                RecordingCatalog.summary(library.items.value, library.totalBytes(), free) to sharedCopies.bytes()
            }
        }
    }

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun deleteOlderThan(days: Long) {
        viewModelScope.launch {
            val old = RecordingCatalog.olderThan(library.items.value, days, System.currentTimeMillis())
            val n = library.delete(old.map { it.id })
            _message.value = "Deleted $n recording${if (n == 1) "" else "s"} older than $days days"
            refresh()
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            val n = library.deleteAll()
            _message.value = "Deleted $n recording${if (n == 1) "" else "s"}"
            refresh()
        }
    }

    fun clearSharedCopies() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { sharedCopies.clear() }
            _message.value = "Removed shared copies"
            refresh()
        }
    }

    fun messageShown() { _message.value = null }
}
