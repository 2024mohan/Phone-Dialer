package app.minimaldialer.calling.domain

import kotlinx.coroutines.flow.StateFlow

/**
 * Everything the UI may do with calls. The UI depends only on this interface; the Telecom
 * implementation lives in :calling:telecom.
 */
interface CallController {
    val calls: StateFlow<List<CallSnapshot>>
    val audio: StateFlow<CallAudio>

    fun placeCall(number: String): PlaceCallResult
    fun answer(callId: String)
    fun reject(callId: String)
    fun hangUp(callId: String)
    fun setOnHold(callId: String, hold: Boolean)
    fun setMuted(muted: Boolean)
    fun setRoute(route: AudioRoute)
    fun playDtmf(callId: String, digit: Char)
    fun stopDtmf(callId: String)
    /** Swap between an active and a held call, if both exist. */
    fun swap()
}

object CallSelection {
    /** The call the in-call screen should focus on. */
    fun primary(calls: List<CallSnapshot>): CallSnapshot? =
        calls.firstOrNull { it.state == CallState.RINGING }
            ?: calls.firstOrNull { it.state == CallState.ACTIVE }
            ?: calls.firstOrNull { it.state == CallState.DIALING }
            ?: calls.firstOrNull { it.state == CallState.ON_HOLD }
            ?: calls.firstOrNull { it.state == CallState.DISCONNECTING }
            ?: calls.lastOrNull()

    /** A held call other than [primary], shown as a "swap" chip. */
    fun secondary(calls: List<CallSnapshot>, primary: CallSnapshot?): CallSnapshot? =
        calls.firstOrNull { it.id != primary?.id && it.state.isLive }

    fun hasLiveCall(calls: List<CallSnapshot>): Boolean = calls.any { it.state.isLive }
}
