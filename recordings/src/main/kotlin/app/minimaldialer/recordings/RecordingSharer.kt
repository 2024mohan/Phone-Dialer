package app.minimaldialer.recordings

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import app.minimaldialer.recording.domain.RecordingLibrary
import app.minimaldialer.recording.domain.RecordingMeta
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds a share-sheet intent for one recording. This necessarily writes a decrypted copy to
 * the app cache (the receiving app can't read our ciphertext). Copies are removed on the next
 * app start and from Settings > Storage.
 */
class RecordingSharer(private val context: Context, private val library: RecordingLibrary) {

    private val dir get() = File(context.cacheDir, "shared_recordings")

    suspend fun shareIntent(meta: RecordingMeta): Intent = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val stamp = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.ROOT).format(Date(meta.startedAtMillis))
        val safeTitle = meta.displayTitle.replace(Regex("[^\\p{L}\\p{N} ._+-]"), "_").take(40).trim()
        val file = File(dir, "${safeTitle}_$stamp.aac")
        file.outputStream().use { library.export(meta.id, it) }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.recordings.share", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("audio/aac")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        Intent.createChooser(send, "Share recording").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** Deletes decrypted share copies. Returns bytes freed. */
    fun clearSharedCopies(): Long {
        val files = dir.listFiles().orEmpty()
        val bytes = files.sumOf { it.length() }
        files.forEach { it.delete() }
        return bytes
    }

    fun sharedCopiesBytes(): Long = dir.listFiles().orEmpty().sumOf { it.length() }
}
