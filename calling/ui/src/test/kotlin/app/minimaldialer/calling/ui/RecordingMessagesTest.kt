package app.minimaldialer.calling.ui

import app.minimaldialer.recording.domain.CaptureSource
import app.minimaldialer.recording.domain.FailureReason
import app.minimaldialer.recording.domain.RecorderState
import app.minimaldialer.recording.domain.RecordingResult
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecordingMessagesTest {
    @Test fun `silent capture is never reported as a successful call recording`() {
        val msg = RecordingMessages.outcome(RecorderState.Saved(RecordingResult("r", CaptureSource.CALL_AUDIO, 60_000, 0, true)))!!
        assertTrue(msg.contains("only silence"))
        assertTrue(!msg.contains("both sides"))
    }

    @Test fun `microphone capture is labelled as microphone only`() {
        val msg = RecordingMessages.outcome(RecorderState.Saved(RecordingResult("r", CaptureSource.MICROPHONE, 60_000, 30_000, false)))!!
        assertTrue(msg.contains("microphone only"))
        assertTrue(RecordingMessages.indicatorLabel(RecorderState.Recording(CaptureSource.MICROPHONE, "c", 0, 61_000))!!.contains("mic only 1:01"))
    }

    @Test fun `silenced flag shows in the live indicator`() {
        val label = RecordingMessages.indicatorLabel(RecorderState.Recording(CaptureSource.MICROPHONE, "c", 0, silencedBySystem = true))!!
        assertTrue(label.contains("silenced by Android"))
    }

    @Test fun `every failure has a message and idle has none`() {
        FailureReason.entries.forEach { assertTrue(RecordingMessages.outcome(RecorderState.Failed(it))!!.isNotBlank()) }
        assertNull(RecordingMessages.outcome(RecorderState.Idle))
        assertNull(RecordingMessages.indicatorLabel(RecorderState.Idle))
    }
}
