package app.minimaldialer.calling.domain

/** Immutable snapshot of one call, built by the Telecom layer. */
data class CallSnapshot(
    val id: String,
    val number: String,
    val displayName: String?,
    val state: CallState,
    val isIncoming: Boolean,
    /** Wall-clock time the call connected (Call.Details.connectTimeMillis), or null before that. */
    val connectTimeMillis: Long?,
    val canHold: Boolean,
    val canMute: Boolean = true,
    val disconnectReason: String? = null,
    val isConference: Boolean = false,
) {
    val title: String get() = displayName?.takeIf { it.isNotBlank() } ?: number.ifBlank { "Unknown number" }

    fun elapsedSeconds(nowMillis: Long): Long =
        connectTimeMillis?.let { ((nowMillis - it) / 1000).coerceAtLeast(0) } ?: 0
}

enum class AudioRoute { EARPIECE, SPEAKER, BLUETOOTH, WIRED_HEADSET, STREAMING }

data class CallAudio(
    val route: AudioRoute = AudioRoute.EARPIECE,
    val supportedRoutes: Set<AudioRoute> = setOf(AudioRoute.EARPIECE, AudioRoute.SPEAKER),
    val muted: Boolean = false,
    val bluetoothDeviceName: String? = null,
)

/** Bit values of android.telecom.CallAudioState.ROUTE_*; verified against the SDK in a unit test. */
object AudioRouteMask {
    const val ROUTE_EARPIECE = 0x1
    const val ROUTE_BLUETOOTH = 0x2
    const val ROUTE_WIRED_HEADSET = 0x4
    const val ROUTE_SPEAKER = 0x8
    const val ROUTE_STREAMING = 0x10

    fun toRoute(route: Int): AudioRoute = when (route) {
        ROUTE_BLUETOOTH -> AudioRoute.BLUETOOTH
        ROUTE_WIRED_HEADSET -> AudioRoute.WIRED_HEADSET
        ROUTE_SPEAKER -> AudioRoute.SPEAKER
        ROUTE_STREAMING -> AudioRoute.STREAMING
        else -> AudioRoute.EARPIECE
    }

    fun toMask(route: AudioRoute): Int = when (route) {
        AudioRoute.EARPIECE -> ROUTE_EARPIECE
        AudioRoute.BLUETOOTH -> ROUTE_BLUETOOTH
        AudioRoute.WIRED_HEADSET -> ROUTE_WIRED_HEADSET
        AudioRoute.SPEAKER -> ROUTE_SPEAKER
        AudioRoute.STREAMING -> ROUTE_STREAMING
    }

    fun supported(mask: Int): Set<AudioRoute> =
        AudioRoute.entries.filterTo(LinkedHashSet()) { mask and toMask(it) != 0 }

    /**
     * What the speaker toggle should switch to. Turning the speaker off returns to Bluetooth
     * or a wired headset when one is available, otherwise the earpiece.
     */
    fun speakerToggleTarget(current: AudioRoute, supported: Set<AudioRoute>): AudioRoute = when {
        current != AudioRoute.SPEAKER -> AudioRoute.SPEAKER
        AudioRoute.BLUETOOTH in supported -> AudioRoute.BLUETOOTH
        AudioRoute.WIRED_HEADSET in supported -> AudioRoute.WIRED_HEADSET
        else -> AudioRoute.EARPIECE
    }
}

sealed interface PlaceCallResult {
    data object Placed : PlaceCallResult
    data object MissingPermission : PlaceCallResult
    data object EmptyNumber : PlaceCallResult
    data class Failed(val message: String) : PlaceCallResult
}
