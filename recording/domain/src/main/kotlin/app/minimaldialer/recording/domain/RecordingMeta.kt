package app.minimaldialer.recording.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Stored next to each recording, encrypted. Holds no audio. */
@Serializable
data class RecordingMeta(
    val id: String,
    val number: String,
    val contactName: String? = null,
    /** User-chosen title; null means "use contact name or number". */
    val title: String? = null,
    val startedAtMillis: Long,
    val durationMillis: Long,
    val audibleMillis: Long,
    val source: CaptureSource,
    val silencedBySystem: Boolean = false,
    val incoming: Boolean = false,
    val sampleRate: Int,
    val channels: Int = 1,
    val mimeType: String = MIME_AAC_ADTS,
    /** Size of the encrypted audio file on disk. */
    val sizeBytes: Long = 0,
    val formatVersion: Int = 1,
) {
    val displayTitle: String get() = title?.takeIf { it.isNotBlank() } ?: contactName?.takeIf { it.isNotBlank() } ?: number.ifBlank { "Unknown number" }

    val quality: CaptureQuality get() = RecordingResult(id, source, durationMillis, audibleMillis, silencedBySystem).quality

    companion object {
        const val MIME_AAC_ADTS = "audio/aac"
    }
}

object RecordingMetaCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    fun encode(meta: RecordingMeta): ByteArray = json.encodeToString(RecordingMeta.serializer(), meta).encodeToByteArray()
    fun decode(bytes: ByteArray): RecordingMeta = json.decodeFromString(RecordingMeta.serializer(), bytes.decodeToString())
}
