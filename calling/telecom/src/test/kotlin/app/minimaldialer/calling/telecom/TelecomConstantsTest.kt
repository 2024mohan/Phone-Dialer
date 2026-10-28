package app.minimaldialer.calling.telecom

import android.telecom.Call
import android.telecom.CallAudioState
import app.minimaldialer.calling.domain.AudioRouteMask
import app.minimaldialer.calling.domain.TelecomStates
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * :calling:domain duplicates framework constants so it can stay SDK-free. These are
 * compile-time constants, so this runs as a plain JVM test against the SDK stubs.
 */
class TelecomConstantsTest {
    @Test fun callStatesMatchFramework() {
        assertEquals(Call.STATE_NEW, TelecomStates.STATE_NEW)
        assertEquals(Call.STATE_DIALING, TelecomStates.STATE_DIALING)
        assertEquals(Call.STATE_RINGING, TelecomStates.STATE_RINGING)
        assertEquals(Call.STATE_HOLDING, TelecomStates.STATE_HOLDING)
        assertEquals(Call.STATE_ACTIVE, TelecomStates.STATE_ACTIVE)
        assertEquals(Call.STATE_DISCONNECTED, TelecomStates.STATE_DISCONNECTED)
        assertEquals(Call.STATE_SELECT_PHONE_ACCOUNT, TelecomStates.STATE_SELECT_PHONE_ACCOUNT)
        assertEquals(Call.STATE_CONNECTING, TelecomStates.STATE_CONNECTING)
        assertEquals(Call.STATE_DISCONNECTING, TelecomStates.STATE_DISCONNECTING)
        assertEquals(Call.STATE_PULLING_CALL, TelecomStates.STATE_PULLING_CALL)
        assertEquals(Call.STATE_AUDIO_PROCESSING, TelecomStates.STATE_AUDIO_PROCESSING)
        assertEquals(Call.STATE_SIMULATED_RINGING, TelecomStates.STATE_SIMULATED_RINGING)
    }

    @Suppress("DEPRECATION")
    @Test fun audioRoutesMatchFramework() {
        assertEquals(CallAudioState.ROUTE_EARPIECE, AudioRouteMask.ROUTE_EARPIECE)
        assertEquals(CallAudioState.ROUTE_BLUETOOTH, AudioRouteMask.ROUTE_BLUETOOTH)
        assertEquals(CallAudioState.ROUTE_WIRED_HEADSET, AudioRouteMask.ROUTE_WIRED_HEADSET)
        assertEquals(CallAudioState.ROUTE_SPEAKER, AudioRouteMask.ROUTE_SPEAKER)
        assertEquals(CallAudioState.ROUTE_STREAMING, AudioRouteMask.ROUTE_STREAMING)
    }
}
