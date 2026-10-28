package app.minimaldialer.recording.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class RecorderAndPolicyTest {
    private fun facts(
        sdk: Int = 34, capture: Boolean = false, mic: Boolean = true, dialer: Boolean = true, oem: String = "Google",
    ) = DeviceFacts(sdk, oem, "Pixel", capture, mic, dialer)

    @Test fun `ordinary app on Android 10+ gets microphone with all limitations listed`() {
        val c = CapabilityPolicy.evaluate(facts())
        assertEquals(CaptureSource.MICROPHONE, c.source)
        assertTrue(Limitation.CALL_AUDIO_REQUIRES_PRIVILEGED_APP in c.limitations)
        assertTrue(Limitation.MIC_SILENCED_DURING_VOICE_CALLS in c.limitations)
        assertTrue(Limitation.FAR_END_ONLY_VIA_SPEAKER in c.limitations)
        assertTrue(c.oemHint!!.contains("Google Phone"))
    }

    @Test fun `privileged install gets call audio`() {
        val c = CapabilityPolicy.evaluate(facts(capture = true))
        assertEquals(CaptureSource.CALL_AUDIO, c.source)
        assertTrue(c.limitations.isEmpty())
        assertNull(c.oemHint)
    }

    @Test fun `no microphone permission means no attempt`() {
        val c = CapabilityPolicy.evaluate(facts(mic = false, dialer = false))
        assertNull(c.source)
        assertEquals(listOf(Limitation.NO_RECORD_AUDIO_PERMISSION, Limitation.NOT_DEFAULT_DIALER), c.limitations)
    }

    @Test fun `unknown manufacturer has no hint`() {
        assertNull(CapabilityPolicy.oemHint("Acme"))
        assertTrue(CapabilityPolicy.oemHint(" SAMSUNG ")!!.startsWith("Samsung"))
    }

    @Test fun `happy path through the recorder state machine`() {
        var s: RecorderState = RecorderState.Idle
        s = RecorderReducer.reduce(s, RecorderEvent.StartRequested(CaptureSource.MICROPHONE))
        assertTrue(s is RecorderState.Starting)
        s = RecorderReducer.reduce(s, RecorderEvent.Started("call1", 5_000))
        s = RecorderReducer.reduce(s, RecorderEvent.Progress(1_000, 0.4f, 800, silencedBySystem = true))
        s = RecorderReducer.reduce(s, RecorderEvent.Progress(2_000, 0.5f, 1_800, silencedBySystem = false))
        s as RecorderState.Recording
        assertTrue("silenced flag is sticky", s.silencedBySystem)
        assertEquals(2_000, s.elapsedMillis)
        s = RecorderReducer.reduce(s, RecorderEvent.StopRequested)
        assertTrue(s is RecorderState.Stopping)
        val result = RecordingResult("r1", CaptureSource.MICROPHONE, 2_000, 1_800, true)
        s = RecorderReducer.reduce(s, RecorderEvent.Completed(result))
        assertEquals(RecorderState.Saved(result), s)
        assertSame(RecorderState.Idle, RecorderReducer.reduce(s, RecorderEvent.Acknowledge))
    }

    @Test fun `start while busy and stray events are ignored`() {
        val rec = RecorderState.Recording(CaptureSource.MICROPHONE, "c", 0)
        assertSame(rec, RecorderReducer.reduce(rec, RecorderEvent.StartRequested(CaptureSource.CALL_AUDIO)))
        assertSame(RecorderState.Idle, RecorderReducer.reduce(RecorderState.Idle, RecorderEvent.StopRequested))
        assertSame(RecorderState.Idle, RecorderReducer.reduce(RecorderState.Idle, RecorderEvent.Started("c", 0)))
        assertSame(rec, RecorderReducer.reduce(rec, RecorderEvent.Acknowledge))
    }

    @Test fun `errors are terminal until acknowledged`() {
        val s = RecorderReducer.reduce(RecorderState.Starting(CaptureSource.MICROPHONE), RecorderEvent.Error(FailureReason.SOURCE_UNAVAILABLE))
        assertEquals(RecorderState.Failed(FailureReason.SOURCE_UNAVAILABLE), s)
        assertSame(RecorderState.Idle, RecorderReducer.reduce(s, RecorderEvent.Acknowledge))
    }

    @Test fun `quality reflects what was captured, not what was attempted`() {
        assertEquals(CaptureQuality.SILENT, RecordingResult("r", CaptureSource.CALL_AUDIO, 5_000, 0, false).quality)
        assertEquals(CaptureQuality.MICROPHONE_ONLY, RecordingResult("r", CaptureSource.MICROPHONE, 5_000, 10, true).quality)
        assertEquals(CaptureQuality.CALL_AUDIO, RecordingResult("r", CaptureSource.CALL_AUDIO, 5_000, 10, false).quality)
    }

    @Test fun `metadata codec round trip tolerates unknown fields`() {
        val m = RecordingMeta("id", "123", "A", null, 1, 2, 3, CaptureSource.MICROPHONE, true, false, 16_000)
        assertEquals(m, RecordingMetaCodec.decode(RecordingMetaCodec.encode(m)))
        val withExtra = RecordingMetaCodec.encode(m).decodeToString().replaceFirst("{", "{\"future\":1,")
        assertEquals(m, RecordingMetaCodec.decode(withExtra.encodeToByteArray()))
    }
}
