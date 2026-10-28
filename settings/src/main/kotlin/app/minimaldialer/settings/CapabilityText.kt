package app.minimaldialer.settings

import app.minimaldialer.recording.domain.CaptureSource
import app.minimaldialer.recording.domain.Limitation
import app.minimaldialer.recording.domain.RecordingCapability

object CapabilityText {
    fun headline(c: RecordingCapability): String = when (c.source) {
        CaptureSource.CALL_AUDIO -> "Two-way call recording is available (privileged install)."
        CaptureSource.MICROPHONE -> "Microphone-only recording. Two-way call recording is not available to this app."
        null -> "Recording is not available until microphone access is allowed."
    }

    fun limitation(l: Limitation): String = when (l) {
        Limitation.NO_RECORD_AUDIO_PERMISSION -> "Microphone permission has not been granted."
        Limitation.NOT_DEFAULT_DIALER -> "This app is not the default phone app, so it can't show its own in-call screen."
        Limitation.CALL_AUDIO_REQUIRES_PRIVILEGED_APP ->
            "Since Android 10, capturing call audio (VOICE_CALL / uplink / downlink) needs the CAPTURE_AUDIO_OUTPUT permission, " +
                "which Android only grants to apps pre-installed by the manufacturer."
        Limitation.MIC_SILENCED_DURING_VOICE_CALLS ->
            "During a cellular call, Android's audio policy normally gives ordinary apps silence from the microphone. " +
                "The app measures the signal and labels silent recordings, so you won't get a recording that only looks like it worked."
        Limitation.FAR_END_ONLY_VIA_SPEAKER ->
            "Where the microphone isn't silenced, it records your side; the other person is only audible if the speaker is on."
    }
}
