package app.minimaldialer.recording.domain

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdtsAndSignalTest {
    @Test fun `ADTS header for 44_1 kHz stereo matches reference bytes`() {
        // Hand-computed per ISO/IEC 14496-3 1.A.2: 371-byte payload (frame 378 = 0x17A), AAC-LC, 44.1 kHz (index 4), 2 ch.
        val h = AdtsHeader.header(371, 44_100, 2)
        assertArrayEquals(
            byteArrayOf(0xFF.toByte(), 0xF1.toByte(), 0x50, 0x80.toByte(), 0x2F, 0x5F, 0xFC.toByte()),
            h,
        )
        assertEquals(378, AdtsHeader.frameLength(h, 0))
    }

    @Test fun `ADTS header round-trips frame length for the recorder format`() {
        for (len in listOf(0, 1, 200, 1500, 8000)) {
            assertEquals(len + 7, AdtsHeader.frameLength(AdtsHeader.header(len, 16_000, 1), 0))
        }
        assertEquals(-1, AdtsHeader.frameLength(byteArrayOf(0, 1, 2, 3, 4, 5, 6), 0))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `unsupported sample rate is rejected`() {
        AdtsHeader.header(10, 17_000, 1)
    }

    @Test fun `scanner stops at a partial frame`() {
        val one = AdtsHeader.header(10, 16_000, 1) + ByteArray(10)
        val bytes = one + one + one.copyOf(9)
        assertEquals(34 to 2, AdtsScanner.wholeFrames(bytes))
    }

    @Test fun `all-zero capture is reported as silent`() {
        val m = SignalMonitor(sampleRate = 16_000)
        repeat(10) { m.accept(ShortArray(1600)) }
        assertTrue(m.isEntirelySilent)
        assertEquals(1000, m.durationMillis)
        assertEquals(0, m.audibleMillis)
        assertEquals(0f, m.lastLevel)
    }

    @Test fun `speech-level signal counts as audible`() {
        val m = SignalMonitor(sampleRate = 16_000)
        m.accept(ShortArray(1600))
        m.accept(ShortArray(1600) { if (it % 2 == 0) 3000 else -3000 })
        assertFalse(m.isEntirelySilent)
        assertEquals(100, m.audibleMillis)
        assertTrue(m.lastLevel in 0.5f..0.9f)
        assertEquals(1f, SignalMonitor.levelOf(32767))
    }

    @Test fun `dither-level noise stays silent`() {
        val m = SignalMonitor(sampleRate = 8_000)
        m.accept(ShortArray(800) { (it % 3 - 1).toShort() })
        assertTrue(m.isEntirelySilent)
    }
}
