package app.minimaldialer.recording.domain

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer

class RecordingStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val cipher = TinkTestCipher()
    private var nextId = 0
    private fun store(dir: File = tmp.root) = RecordingStore(dir, cipher) { "rec${nextId++}" }

    private fun draft() = RecordingMeta(
        id = "", number = "+919845012345", contactName = "Mohan", startedAtMillis = 1_000,
        durationMillis = 0, audibleMillis = 0, source = CaptureSource.MICROPHONE, sampleRate = 16_000,
    )

    /** [count] ADTS frames with 100-byte payloads of a recognisable pattern. */
    private fun adts(count: Int): ByteArray = ByteArrayOutputStream().apply {
        repeat(count) { i ->
            write(AdtsHeader.header(100, 16_000, 1))
            write(ByteArray(100) { (i + it).toByte() })
        }
    }.toByteArray()

    @Test fun `commit stores encrypted audio and metadata`() {
        val s = store()
        val audio = adts(50)
        val w = s.begin(draft())
        w.stream.write(audio)
        val meta = w.commit { it.copy(durationMillis = 3200, audibleMillis = 3000) }

        assertEquals(listOf(meta), s.list())
        assertTrue(meta.sizeBytes > audio.size) // ciphertext carries header and tags

        // No plaintext on disk: neither the audio pattern nor the phone number appears.
        tmp.root.listFiles()!!.forEach { f ->
            val bytes = f.readBytes()
            assertFalse(f.name, bytes.decodeToString(throwOnInvalidSequence = false).contains("9845012345"))
            assertFalse(f.name, indexOf(bytes, audio.copyOfRange(7, 107)) >= 0)
        }

        val out = ByteArrayOutputStream()
        s.exportTo(meta.id, out)
        assertArrayEquals(audio, out.toByteArray())
    }

    @Test fun `seekable playback channel returns the right bytes`() {
        val s = store()
        val audio = adts(200) // > 4 KB segments, so seeking crosses segments
        val w = s.begin(draft()); w.stream.write(audio)
        val meta = w.commit { it }
        s.openAudio(meta.id).use { ch ->
            assertEquals(audio.size.toLong(), ch.size())
            ch.position(10_000)
            val buf = ByteBuffer.allocate(500)
            while (buf.hasRemaining() && ch.read(buf) > 0) Unit
            assertArrayEquals(audio.copyOfRange(10_000, 10_500), buf.array())
        }
    }

    @Test fun `audio cannot be read under another recording's id`() {
        val s = store()
        val w0 = s.begin(draft()); w0.stream.write(adts(3)); val a = w0.commit { it }
        val w1 = s.begin(draft()); w1.stream.write(adts(3)); val b = w1.commit { it }
        // Swap the ciphertexts on disk: authentication must fail rather than play the wrong call.
        val fa = File(tmp.root, "${a.id}${RecordingStore.AUDIO}")
        val fb = File(tmp.root, "${b.id}${RecordingStore.AUDIO}")
        val tmpBytes = fa.readBytes(); fa.writeBytes(fb.readBytes()); fb.writeBytes(tmpBytes)
        val result = runCatching { s.exportTo(a.id, ByteArrayOutputStream()) }
        assertTrue(result.isFailure)
    }

    @Test fun `rename, delete and list ordering`() {
        val s = store()
        val w0 = s.begin(draft().copy(startedAtMillis = 1)); w0.stream.write(adts(1)); val old = w0.commit { it }
        val w1 = s.begin(draft().copy(startedAtMillis = 2)); w1.stream.write(adts(1)); val new = w1.commit { it }
        assertEquals(listOf(new.id, old.id), s.list().map { it.id })

        assertEquals("Landlord", s.rename(old.id, "  Landlord ").title)
        assertNull(s.rename(old.id, "   ").title)

        assertTrue(s.delete(new.id))
        assertEquals(listOf(old.id), s.list().map { it.id })
        assertEquals(1, s.deleteAll())
        assertEquals(0L, s.totalBytes())
    }

    @Test fun `in-progress recordings are not listed and abort cleans up`() {
        val s = store()
        val w = s.begin(draft()); w.stream.write(adts(2))
        assertTrue(s.list().isEmpty())
        w.abort()
        assertTrue(tmp.root.listFiles()!!.isEmpty())
    }

    @Test fun `interrupted recording is recovered up to the last whole frame`() {
        val s = store()
        val w = s.begin(draft())
        val audio = adts(300) // 107 bytes per frame
        w.stream.write(audio)
        w.stream.flush()
        // Simulate a crash: the stream is never closed, so the final segment is missing.
        val part = File(tmp.root, "${w.meta.id}${RecordingStore.PART}")
        val truncated = part.readBytes().copyOf((part.length() * 2 / 3).toInt())
        part.writeBytes(truncated)

        val recovered = s.recoverInterrupted()
        assertEquals(1, recovered.size)
        val out = ByteArrayOutputStream(); s.exportTo(recovered[0].id, out)
        val bytes = out.toByteArray()
        assertTrue(bytes.isNotEmpty())
        assertEquals(0, bytes.size % 107)
        assertArrayEquals(audio.copyOf(bytes.size), bytes)
        assertEquals(bytes.size / 107 * 1024L * 1000 / 16_000, recovered[0].durationMillis)
        assertFalse(part.exists())
    }

    private fun indexOf(hay: ByteArray, needle: ByteArray): Int {
        outer@ for (i in 0..hay.size - needle.size) {
            for (j in needle.indices) if (hay[i + j] != needle[j]) continue@outer
            return i
        }
        return -1
    }
}
