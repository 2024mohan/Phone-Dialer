package app.minimaldialer.recording.domain

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.SeekableByteChannel
import java.nio.file.StandardOpenOption
import java.util.UUID

/**
 * Encrypted, app-private recording storage. For each recording id:
 *  - `<id>.aac.part` while recording (encrypted ADTS stream),
 *  - `<id>.aac.enc` once finalised,
 *  - `<id>.meta.enc` with the encrypted [RecordingMeta].
 * Nothing is written in plaintext. Blocking I/O: call from a background dispatcher.
 */
class RecordingStore(
    private val dir: File,
    private val cipher: StreamCipher,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    init {
        dir.mkdirs()
    }

    fun begin(draft: RecordingMeta): Writer {
        val id = newId()
        val meta = draft.copy(id = id)
        // Write the draft metadata first so an interrupted recording can be recovered.
        writeMeta(meta)
        val part = File(dir, "$id$PART")
        val out = cipher.encrypting(FileOutputStream(part), audioAad(id))
        return Writer(meta, part, out)
    }

    inner class Writer internal constructor(
        val meta: RecordingMeta,
        private val part: File,
        private val out: OutputStream,
    ) {
        private var closed = false
        val stream: OutputStream get() = out

        /** Closes the stream (writing the final authenticated segment) and publishes the recording. */
        fun commit(update: (RecordingMeta) -> RecordingMeta): RecordingMeta {
            check(!closed) { "already closed" }
            closed = true
            out.close()
            val final = File(dir, "${meta.id}$AUDIO")
            if (!part.renameTo(final)) throw IOException("Could not finalise ${part.name}")
            val done = update(meta).copy(id = meta.id, sizeBytes = final.length())
            writeMeta(done)
            return done
        }

        fun abort() {
            if (closed) return
            closed = true
            runCatching { out.close() }
            part.delete()
            File(dir, "${meta.id}$META").delete()
        }
    }

    fun list(): List<RecordingMeta> =
        dir.listFiles { f -> f.name.endsWith(META) }.orEmpty()
            .mapNotNull { f ->
                val id = f.name.removeSuffix(META)
                if (!File(dir, "$id$AUDIO").exists()) return@mapNotNull null
                runCatching { readMeta(id) }.getOrNull()
            }
            .sortedByDescending { it.startedAtMillis }

    fun get(id: String): RecordingMeta? = runCatching { readMeta(id) }.getOrNull()

    fun rename(id: String, title: String?): RecordingMeta {
        val updated = readMeta(id).copy(title = title?.trim()?.takeIf { it.isNotEmpty() })
        writeMeta(updated)
        return updated
    }

    fun delete(id: String): Boolean {
        val a = File(dir, "$id$AUDIO")
        val m = File(dir, "$id$META")
        val p = File(dir, "$id$PART")
        return listOf(a, m, p).map { !it.exists() || it.delete() }.all { it }
    }

    fun deleteAll(): Int = list().count { delete(it.id) }

    fun openAudio(id: String): SeekableByteChannel {
        val channel = cipher.decrypting(FileChannel.open(File(dir, "$id$AUDIO").toPath(), StandardOpenOption.READ), audioAad(id))
        // Tink's keyset-level decrypter picks the key on the first read, and size() throws until
        // then. Media players ask for the size first, so prime the channel with a 1-byte read.
        try {
            channel.read(ByteBuffer.allocate(1))
            channel.position(0)
        } catch (e: IOException) {
            channel.close()
            throw e
        }
        return channel
    }

    /** Decrypts to [out], e.g. a share-cache file the user explicitly chose to share. */
    fun exportTo(id: String, out: OutputStream) {
        cipher.decrypting(FileInputStream(File(dir, "$id$AUDIO")), audioAad(id)).use { it.copyTo(out) }
    }

    fun totalBytes(): Long = dir.listFiles().orEmpty().sumOf { it.length() }

    /**
     * Finalises recordings interrupted by a crash or power loss. The authenticated prefix of
     * the stream is kept, cut back to the last whole ADTS frame; the duration is recomputed
     * from the frame count. Returns the recovered recordings.
     */
    fun recoverInterrupted(samplesPerFrame: Int = 1024): List<RecordingMeta> {
        val parts = dir.listFiles { f -> f.name.endsWith(PART) }.orEmpty()
        return parts.mapNotNull { part ->
            val id = part.name.removeSuffix(PART)
            val meta = runCatching { readMeta(id) }.getOrNull()
            if (meta == null) {
                part.delete(); return@mapNotNull null
            }
            val plain = ByteArrayOutputStream()
            runCatching {
                cipher.decrypting(FileInputStream(part), audioAad(id)).use { input ->
                    val buf = ByteArray(8192)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        plain.write(buf, 0, n)
                    }
                }
            } // A truncated stream throws at the cut; everything read before it is authentic.
            val bytes = plain.toByteArray()
            val (validLength, frames) = AdtsScanner.wholeFrames(bytes)
            if (frames == 0) {
                delete(id); return@mapNotNull null
            }
            val final = File(dir, "$id$AUDIO")
            cipher.encrypting(FileOutputStream(final), audioAad(id)).use { it.write(bytes, 0, validLength) }
            part.delete()
            val duration = frames.toLong() * samplesPerFrame * 1000 / meta.sampleRate
            // Signal level wasn't persisted, so audibility is unknown; keep what the draft said.
            val recovered = meta.copy(durationMillis = duration, sizeBytes = final.length())
            writeMeta(recovered)
            recovered
        }
    }

    private fun writeMeta(meta: RecordingMeta) {
        val tmp = File(dir, "${meta.id}$META.tmp")
        cipher.encrypting(FileOutputStream(tmp), metaAad(meta.id)).use { it.write(RecordingMetaCodec.encode(meta)) }
        val target = File(dir, "${meta.id}$META")
        if (!tmp.renameTo(target)) {
            target.delete()
            if (!tmp.renameTo(target)) throw IOException("Could not write metadata for ${meta.id}")
        }
    }

    private fun readMeta(id: String): RecordingMeta =
        cipher.decrypting(FileInputStream(File(dir, "$id$META")), metaAad(id)).use { RecordingMetaCodec.decode(it.readBytes()) }

    private fun audioAad(id: String) = "audio:$id".encodeToByteArray()
    private fun metaAad(id: String) = "meta:$id".encodeToByteArray()

    companion object {
        const val AUDIO = ".aac.enc"
        const val META = ".meta.enc"
        const val PART = ".aac.part"
    }
}

object AdtsScanner {
    /** Returns (byte length of whole frames, frame count) from the start of [bytes]. */
    fun wholeFrames(bytes: ByteArray): Pair<Int, Int> {
        var offset = 0
        var frames = 0
        while (true) {
            val len = AdtsHeader.frameLength(bytes, offset)
            if (len < AdtsHeader.SIZE || offset + len > bytes.size) break
            offset += len
            frames++
        }
        return offset to frames
    }
}
