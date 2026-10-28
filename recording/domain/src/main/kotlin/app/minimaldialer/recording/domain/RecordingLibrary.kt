package app.minimaldialer.recording.domain

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.OutputStream

/** Observable facade over [RecordingStore], shared by the recorder and the library screen. */
class RecordingLibrary(
    val store: RecordingStore,
    private val io: CoroutineDispatcher,
) {
    private val _items = MutableStateFlow<List<RecordingMeta>>(emptyList())
    val items: StateFlow<List<RecordingMeta>> = _items.asStateFlow()

    suspend fun refresh() = withContext(io) { _items.value = store.list() }

    suspend fun rename(id: String, title: String?): RecordingMeta = withContext(io) {
        store.rename(id, title).also { _items.value = store.list() }
    }

    suspend fun delete(ids: Collection<String>): Int = withContext(io) {
        ids.count { store.delete(it) }.also { _items.value = store.list() }
    }

    suspend fun deleteAll(): Int = withContext(io) { store.deleteAll().also { _items.value = store.list() } }

    suspend fun export(id: String, out: OutputStream) = withContext(io) { store.exportTo(id, out) }

    suspend fun totalBytes(): Long = withContext(io) { store.totalBytes() }

    suspend fun recoverInterrupted(): List<RecordingMeta> = withContext(io) {
        store.recoverInterrupted().also { _items.value = store.list() }
    }

    /** Called by the recorder after a commit so observers see the new item immediately. */
    fun published(meta: RecordingMeta) {
        _items.value = (listOf(meta) + _items.value.filterNot { it.id == meta.id }).sortedByDescending { it.startedAtMillis }
    }
}
