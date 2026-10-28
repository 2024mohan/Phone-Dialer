package app.minimaldialer.calling.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallStateTest {
    @Test fun `telecom states map to the six user-facing states`() {
        assertEquals(CallState.DIALING, TelecomStates.map(TelecomStates.STATE_DIALING))
        assertEquals(CallState.DIALING, TelecomStates.map(TelecomStates.STATE_CONNECTING))
        assertEquals(CallState.RINGING, TelecomStates.map(TelecomStates.STATE_RINGING))
        assertEquals(CallState.RINGING, TelecomStates.map(TelecomStates.STATE_SIMULATED_RINGING))
        assertEquals(CallState.ACTIVE, TelecomStates.map(TelecomStates.STATE_ACTIVE))
        assertEquals(CallState.ON_HOLD, TelecomStates.map(TelecomStates.STATE_HOLDING))
        assertEquals(CallState.DISCONNECTED, TelecomStates.map(TelecomStates.STATE_DISCONNECTED))
        assertEquals(CallState.IDLE, TelecomStates.map(TelecomStates.STATE_NEW))
        assertEquals(CallState.IDLE, TelecomStates.map(999))
    }

    @Test fun liveness() {
        assertTrue(CallState.ON_HOLD.isLive)
        assertFalse(CallState.DISCONNECTED.isLive)
        assertFalse(CallState.IDLE.isLive)
    }

    @Test fun `primary call prefers ringing then active`() {
        val held = call("a", CallState.ON_HOLD)
        val active = call("b", CallState.ACTIVE)
        val ringing = call("c", CallState.RINGING)
        assertEquals("c", CallSelection.primary(listOf(held, active, ringing))?.id)
        assertEquals("b", CallSelection.primary(listOf(held, active))?.id)
        assertEquals("a", CallSelection.secondary(listOf(held, active), active)?.id)
        assertNull(CallSelection.primary(emptyList()))
    }

    @Test fun `elapsed time counts from connect`() {
        val c = call("a", CallState.ACTIVE).copy(connectTimeMillis = 10_000)
        assertEquals(65, c.elapsedSeconds(75_500))
        assertEquals(0, c.copy(connectTimeMillis = null).elapsedSeconds(75_500))
        assertEquals(0, c.elapsedSeconds(5_000))
    }

    @Test fun `title falls back to number then placeholder`() {
        assertEquals("123", call("a", CallState.ACTIVE).title)
        assertEquals("Unknown number", call("a", CallState.ACTIVE).copy(number = "").title)
    }

    @Test fun `audio route mask round trip and speaker toggle`() {
        val supported = AudioRouteMask.supported(0x1 or 0x2 or 0x8)
        assertEquals(setOf(AudioRoute.EARPIECE, AudioRoute.BLUETOOTH, AudioRoute.SPEAKER), supported)
        AudioRoute.entries.forEach { assertEquals(it, AudioRouteMask.toRoute(AudioRouteMask.toMask(it))) }
        assertEquals(AudioRoute.SPEAKER, AudioRouteMask.speakerToggleTarget(AudioRoute.EARPIECE, supported))
        assertEquals(AudioRoute.BLUETOOTH, AudioRouteMask.speakerToggleTarget(AudioRoute.SPEAKER, supported))
        assertEquals(AudioRoute.EARPIECE, AudioRouteMask.speakerToggleTarget(AudioRoute.SPEAKER, setOf(AudioRoute.EARPIECE)))
    }

    private fun call(id: String, state: CallState) =
        CallSnapshot(id, "123", null, state, isIncoming = false, connectTimeMillis = null, canHold = true)
}
