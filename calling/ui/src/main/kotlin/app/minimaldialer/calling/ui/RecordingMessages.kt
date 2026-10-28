package app.minimaldialer.calling.ui

import app.minimaldialer.recording.domain.CaptureQuality
import app.minimaldialer.recording.domain.CaptureSource
import app.minimaldialer.recording.domain.FailureReason
import app.minimaldialer.recording.domain.RecorderState

/** User-facing wording. It describes what was captured, never what was attempted. */
object RecordingMessages {

    fun indicatorLabel(state: RecorderState): String? = when (state) {
        is RecorderState.Starting -> "Starting recording…"
        is RecorderState.Recording -> buildString {
            append(if (state.source == CaptureSource.CALL_AUDIO) "REC" else "REC · mic only")
            append(" ").append(app.minimaldialer.core.model.Durations.formatMillis(state.elapsedMillis))
            if (state.silencedBySystem) append(" · silenced by Android")
        }
        is RecorderState.Stopping -> "Saving recording…"
        else -> null
    }

    fun outcome(state: RecorderState): String? = when (state) {
        is RecorderState.Saved -> {
            val r = state.result
            when (r.quality) {
                CaptureQuality.CALL_AUDIO -> "Recording saved (both sides of the call)."
                CaptureQuality.MICROPHONE_ONLY ->
                    "Recording saved from the microphone only. The other person is audible only if the speaker was on." +
                        if (r.silencedBySystem) " Android silenced the microphone for part of the call." else ""
                CaptureQuality.SILENT ->
                    if (r.silencedBySystem) {
                        "Saved, but it contains only silence: Android blocked microphone access during this call. " +
                            "This device does not allow call recording by third-party apps."
                    } else {
                        "Saved, but no sound was captured. The microphone appears to be unavailable during calls on this device."
                    }
            }
        }
        is RecorderState.Failed -> when (state.reason) {
            FailureReason.NO_PERMISSION -> "Recording needs microphone permission."
            FailureReason.NO_ACTIVE_CALL -> "There's no connected call to record."
            FailureReason.SOURCE_UNAVAILABLE -> "Android did not allow the app to open the microphone during this call. Nothing was recorded."
            FailureReason.ENCODER_ERROR -> "Recording failed: no audio could be encoded. Nothing was saved."
            FailureReason.STORAGE_ERROR -> "Recording failed: it could not be saved to storage."
            FailureReason.SERVICE_NOT_ALLOWED -> "Android did not allow recording to start from the background. Open the call screen and try again."
        }
        else -> null
    }
}
