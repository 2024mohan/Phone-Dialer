package app.minimaldialer.calling.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.minimaldialer.calling.domain.AudioRoute
import app.minimaldialer.calling.domain.AudioRouteMask
import app.minimaldialer.calling.domain.CallAudio
import app.minimaldialer.calling.domain.CallController
import app.minimaldialer.calling.domain.CallSelection
import app.minimaldialer.calling.domain.CallSnapshot
import app.minimaldialer.calling.domain.CallState
import app.minimaldialer.core.model.Durations
import app.minimaldialer.recording.domain.CallRecorder
import app.minimaldialer.recording.domain.RecorderState
import app.minimaldialer.recording.domain.RecordingCapability
import app.minimaldialer.recording.domain.RecordingTarget
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class InCallUiState(
    val primary: CallSnapshot? = null,
    val secondary: CallSnapshot? = null,
    val audio: CallAudio = CallAudio(),
    val nowMillis: Long = System.currentTimeMillis(),
    val recorder: RecorderState = RecorderState.Idle,
    val capability: RecordingCapability? = null,
) {
    val elapsedText: String?
        get() {
            val p = primary ?: return null
            if (p.state != CallState.ACTIVE && p.state != CallState.ON_HOLD) return null
            if (p.connectTimeMillis == null) return null
            return Durations.formatSeconds(p.elapsedSeconds(nowMillis))
        }
}

class InCallViewModel(
    private val calls: CallController,
    private val recorder: CallRecorder,
) : ViewModel() {

    private val clock = MutableStateFlow(System.currentTimeMillis())

    init {
        viewModelScope.launch {
            while (isActive) {
                clock.value = System.currentTimeMillis()
                delay(1_000 - clock.value % 1_000)
            }
        }
        recorder.refreshCapability()
    }

    val state: StateFlow<InCallUiState> = combine(
        calls.calls, calls.audio, clock, recorder.state, recorder.capability, ::build,
    ).stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000),
        // Start from the current values: an empty initial state would read as "no call" and close the screen.
        build(calls.calls.value, calls.audio.value, clock.value, recorder.state.value, recorder.capability.value),
    )

    private fun build(
        list: List<CallSnapshot>, audio: CallAudio, now: Long, rec: RecorderState, cap: RecordingCapability,
    ): InCallUiState {
        val primary = CallSelection.primary(list)
        return InCallUiState(primary, CallSelection.secondary(list, primary), audio, now, rec, cap)
    }

    private fun primaryId(): String? = state.value.primary?.id ?: CallSelection.primary(calls.calls.value)?.id

    fun answer() = primaryId()?.let(calls::answer)
    fun decline() = primaryId()?.let(calls::reject)
    fun hangUp() = primaryId()?.let(calls::hangUp)
    fun toggleMute() = calls.setMuted(!calls.audio.value.muted)
    fun toggleHold() {
        val p = CallSelection.primary(calls.calls.value) ?: return
        calls.setOnHold(p.id, hold = p.state != CallState.ON_HOLD)
    }
    fun swap() = calls.swap()

    fun toggleSpeaker() {
        val a = calls.audio.value
        calls.setRoute(AudioRouteMask.speakerToggleTarget(a.route, a.supportedRoutes))
    }

    fun setRoute(route: AudioRoute) = calls.setRoute(route)

    fun dtmf(c: Char) {
        val id = primaryId() ?: return
        calls.playDtmf(id, c)
        calls.stopDtmf(id)
    }

    fun refreshCapability() = recorder.refreshCapability()

    fun toggleRecording() {
        if (recorder.state.value.isBusy) {
            recorder.stop()
            return
        }
        val p = CallSelection.primary(calls.calls.value)?.takeIf { it.state == CallState.ACTIVE || it.state == CallState.ON_HOLD } ?: return
        recorder.start(RecordingTarget(p.id, p.number, p.displayName, p.isIncoming))
    }

    /** After the permission dialog: start only if the microphone permission was granted. */
    fun onRecordPermissionsResult() {
        recorder.refreshCapability()
        if (recorder.capability.value.canAttempt) toggleRecording()
    }

    fun acknowledgeRecording() = recorder.acknowledge()
}
