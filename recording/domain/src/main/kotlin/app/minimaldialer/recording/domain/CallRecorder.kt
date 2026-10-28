package app.minimaldialer.recording.domain

import kotlinx.coroutines.flow.StateFlow

/** The call a recording belongs to. Supplied by the app layer, so recording stays independent of Telecom. */
data class RecordingTarget(
    val callId: String,
    val number: String,
    val contactName: String?,
    val incoming: Boolean,
)

/**
 * Recording lifecycle as seen by the UI. Recording only ever starts from an explicit user
 * action, and while it runs a foreground-service notification is shown; the system's own
 * microphone privacy indicator is never suppressed.
 */
interface CallRecorder {
    val state: StateFlow<RecorderState>
    val capability: StateFlow<RecordingCapability>

    /** Re-reads permissions, role and privileged status (e.g. after the user grants something). */
    fun refreshCapability()
    fun start(target: RecordingTarget)
    fun stop()
    /** Clears a Saved/Failed state after the UI has shown it. */
    fun acknowledge()
}
