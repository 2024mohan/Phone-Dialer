package app.minimaldialer.recording.domain

enum class FailureReason {
    NO_PERMISSION,
    NO_ACTIVE_CALL,
    /** AudioRecord could not be initialised for the requested source. */
    SOURCE_UNAVAILABLE,
    ENCODER_ERROR,
    STORAGE_ERROR,
    /** The system did not let the foreground service start (e.g. background-start restrictions). */
    SERVICE_NOT_ALLOWED,
}

sealed interface RecorderState {
    data object Idle : RecorderState

    data class Starting(val source: CaptureSource) : RecorderState

    data class Recording(
        val source: CaptureSource,
        val callId: String,
        val startedAtMillis: Long,
        val elapsedMillis: Long = 0,
        val level: Float = 0f,
        /** The framework reported this capture as silenced (AudioRecordingConfiguration.isClientSilenced). */
        val silencedBySystem: Boolean = false,
        /** Milliseconds captured that were above the silence threshold. */
        val audibleMillis: Long = 0,
    ) : RecorderState

    data class Stopping(val source: CaptureSource) : RecorderState

    data class Saved(val result: RecordingResult) : RecorderState

    data class Failed(val reason: FailureReason, val detail: String? = null) : RecorderState

    val isBusy: Boolean get() = this is Starting || this is Recording || this is Stopping
}

/** What was actually captured. The UI words its message from this, never from what was intended. */
data class RecordingResult(
    val recordingId: String,
    val source: CaptureSource,
    val durationMillis: Long,
    val audibleMillis: Long,
    val silencedBySystem: Boolean,
) {
    val quality: CaptureQuality get() = when {
        audibleMillis == 0L -> CaptureQuality.SILENT
        source == CaptureSource.CALL_AUDIO -> CaptureQuality.CALL_AUDIO
        else -> CaptureQuality.MICROPHONE_ONLY
    }
}

enum class CaptureQuality {
    /** Both call directions captured from the telephony path. */
    CALL_AUDIO,
    /** Microphone signal was captured; the far end is only present if it was audible acoustically. */
    MICROPHONE_ONLY,
    /** The file holds only silence, usually because Android silenced the capture during the call. */
    SILENT,
}

sealed interface RecorderEvent {
    data class StartRequested(val source: CaptureSource) : RecorderEvent
    data class Started(val callId: String, val atMillis: Long) : RecorderEvent
    data class Progress(val elapsedMillis: Long, val level: Float, val audibleMillis: Long, val silencedBySystem: Boolean) : RecorderEvent
    data object StopRequested : RecorderEvent
    data class Completed(val result: RecordingResult) : RecorderEvent
    data class Error(val reason: FailureReason, val detail: String? = null) : RecorderEvent
    /** Clears a terminal Saved/Failed state once the UI has shown it. */
    data object Acknowledge : RecorderEvent
}

/** Pure transition function. Events that don't apply to the current state are ignored. */
object RecorderReducer {
    fun reduce(state: RecorderState, event: RecorderEvent): RecorderState = when (event) {
        is RecorderEvent.StartRequested ->
            if (state.isBusy) state else RecorderState.Starting(event.source)
        is RecorderEvent.Started ->
            if (state is RecorderState.Starting) RecorderState.Recording(state.source, event.callId, event.atMillis) else state
        is RecorderEvent.Progress ->
            if (state is RecorderState.Recording) state.copy(
                elapsedMillis = event.elapsedMillis,
                level = event.level,
                audibleMillis = event.audibleMillis,
                // Sticky: once silenced during this recording, the user is told even if it clears.
                silencedBySystem = state.silencedBySystem || event.silencedBySystem,
            ) else state
        RecorderEvent.StopRequested -> when (state) {
            is RecorderState.Recording -> RecorderState.Stopping(state.source)
            is RecorderState.Starting -> RecorderState.Stopping(state.source)
            else -> state
        }
        is RecorderEvent.Completed ->
            if (state is RecorderState.Recording || state is RecorderState.Stopping) RecorderState.Saved(event.result) else state
        is RecorderEvent.Error -> RecorderState.Failed(event.reason, event.detail)
        RecorderEvent.Acknowledge ->
            if (state is RecorderState.Saved || state is RecorderState.Failed) RecorderState.Idle else state
    }
}
