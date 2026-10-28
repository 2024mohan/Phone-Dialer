package app.minimaldialer.calling.domain

/** Call states shown to the user. Telecom has more; they are folded into these. */
enum class CallState {
    IDLE,
    DIALING,
    RINGING,
    ACTIVE,
    ON_HOLD,
    DISCONNECTING,
    DISCONNECTED;

    val isLive: Boolean get() = this == DIALING || this == RINGING || this == ACTIVE || this == ON_HOLD
}

/**
 * Maps android.telecom.Call state integers to [CallState]. The constants are duplicated here
 * so this module stays free of the Android SDK; a unit test in :calling:telecom asserts they
 * equal the framework values.
 */
object TelecomStates {
    const val STATE_NEW = 0
    const val STATE_DIALING = 1
    const val STATE_RINGING = 2
    const val STATE_HOLDING = 3
    const val STATE_ACTIVE = 4
    const val STATE_DISCONNECTED = 7
    const val STATE_SELECT_PHONE_ACCOUNT = 8
    const val STATE_CONNECTING = 9
    const val STATE_DISCONNECTING = 10
    const val STATE_PULLING_CALL = 11
    const val STATE_AUDIO_PROCESSING = 12
    const val STATE_SIMULATED_RINGING = 13

    fun map(telecomState: Int): CallState = when (telecomState) {
        STATE_NEW, STATE_SELECT_PHONE_ACCOUNT -> CallState.IDLE
        STATE_DIALING, STATE_CONNECTING, STATE_PULLING_CALL -> CallState.DIALING
        STATE_RINGING, STATE_SIMULATED_RINGING -> CallState.RINGING
        // AUDIO_PROCESSING: the call is connected but handed to e.g. a screening service.
        STATE_ACTIVE, STATE_AUDIO_PROCESSING -> CallState.ACTIVE
        STATE_HOLDING -> CallState.ON_HOLD
        STATE_DISCONNECTING -> CallState.DISCONNECTING
        STATE_DISCONNECTED -> CallState.DISCONNECTED
        else -> CallState.IDLE
    }
}
