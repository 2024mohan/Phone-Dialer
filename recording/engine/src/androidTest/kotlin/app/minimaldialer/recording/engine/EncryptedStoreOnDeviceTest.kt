package app.minimaldialer.recording.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.minimaldialer.recording.domain.AdtsHeader
import app.minimaldialer.recording.domain.CaptureSource
import app.minimaldialer.recording.domain.RecordingMeta
import app.minimaldialer.recording.domain.RecordingStore
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer

/** Exercises the real Android Keystore-backed Tink keyset on a device or emulator. */
@RunWith(AndroidJUnit4::class)
class EncryptedStoreOnDeviceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dir = File(context.cacheDir, "store-test-${System.nanoTime()}")

    @After fun cleanUp() {
        dir.deleteRecursively()
    }

    @Test fun roundTripWithKeystoreKey() {
        val store = RecordingStore(dir, TinkStreamCipher(context))
        val audio = ByteArrayOutputStream().apply {
            repeat(100) { write(AdtsHeader.header(64, 16_000, 1)); write(ByteArray(64) { b -> b.toByte() }) }
        }.toByteArray()
        val writer = store.begin(
            RecordingMeta("", "+15550100", "Test", null, 1, 0, 0, CaptureSource.MICROPHONE, sampleRate = 16_000),
        )
        writer.stream.write(audio)
        val meta = writer.commit { it.copy(durationMillis = 6_400) }

        // A second cipher instance must load the same keyset from the Keystore-wrapped prefs.
        val reopened = RecordingStore(dir, TinkStreamCipher(context))
        assertEquals(listOf(meta), reopened.list())
        reopened.openAudio(meta.id).use { ch ->
            assertEquals(audio.size.toLong(), ch.size())
            val buf = ByteBuffer.allocate(audio.size)
            while (buf.hasRemaining() && ch.read(buf) > 0) Unit
            assertArrayEquals(audio, buf.array())
        }
    }
}
