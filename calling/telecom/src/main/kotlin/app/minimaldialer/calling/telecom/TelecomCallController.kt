package app.minimaldialer.calling.telecom

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.DisconnectCause
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import androidx.core.content.ContextCompat
import app.minimaldialer.calling.domain.AudioRouteMask
import app.minimaldialer.calling.domain.AudioRoute
import app.minimaldialer.calling.domain.CallAudio
import app.minimaldialer.calling.domain.CallController
import app.minimaldialer.calling.domain.CallSnapshot
import app.minimaldialer.calling.domain.CallState
import app.minimaldialer.calling.domain.PlaceCallResult
import app.minimaldialer.calling.domain.TelecomStates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * [CallController] backed by android.telecom. Telecom delivers every callback on the main
 * thread, and all mutable state here is only touched from it.
 */
class TelecomCallController private constructor(private val appContext: Context) : CallController {

    /** Supplied by the app: opens the in-call screen, optionally answering [answerCallId]. */
    var inCallIntentFactory: ((Context, String?) -> Intent)? = null

    /** Supplied by the app: resolves a contact name for a number (address-book lookup). */
    var nameResolver: (suspend (String) -> String?)? = null

    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var service: InCallService? = null
    private val ids = LinkedHashMap<Call, String>()
    private val resolvedNames = HashMap<String, String>()
    /** Calls that just ended, kept briefly so the UI can show "Call ended" with the reason. */
    private val recentlyEnded = LinkedHashMap<String, CallSnapshot>()
    private var nextId = 1

    private val _calls = MutableStateFlow<List<CallSnapshot>>(emptyList())
    override val calls: StateFlow<List<CallSnapshot>> = _calls.asStateFlow()

    private val _audio = MutableStateFlow(CallAudio())
    override val audio: StateFlow<CallAudio> = _audio.asStateFlow()

    private val notifier by lazy { CallNotifier(appContext) }

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) = publish()
        override fun onDetailsChanged(call: Call, details: Call.Details) = publish()
    }

    // region InCallService hooks

    internal fun attach(service: InCallService) {
        this.service = service
    }

    internal fun detach(service: InCallService) {
        if (this.service === service) this.service = null
        ids.keys.toList().forEach { it.unregisterCallback(callback) }
        ids.clear()
        publish()
    }

    internal fun onCallAdded(service: InCallService, call: Call) {
        val id = "call-${nextId++}"
        ids[call] = id
        call.registerCallback(callback, main)
        resolveName(id, call)
        publish()
        val snapshot = snapshotOf(call, id)
        // Outgoing calls open the in-call screen immediately. Incoming calls go through the
        // notification's full-screen intent, which the system shows as a heads-up instead when
        // the user is actively using the phone. Without notification permission there is no
        // other way to surface the call, so the activity is started directly (InCallService
        // bindings are exempt from background activity-start limits).
        if (snapshot.state != CallState.RINGING || !notifier.canPostNotifications()) {
            openInCallScreen(service)
        }
    }

    internal fun onCallRemoved(call: Call) {
        val id = ids.remove(call) ?: return
        call.unregisterCallback(callback)
        val ended = snapshotOf(call, id).copy(state = CallState.DISCONNECTED)
        recentlyEnded[id] = ended
        publish()
        scope.launch {
            delay(ENDED_LINGER_MS)
            recentlyEnded.remove(id)
            resolvedNames.remove(id)
            publish()
        }
    }

    @Suppress("DEPRECATION") // CallAudioState is deprecated on API 34 but still delivered.
    internal fun onAudioStateChanged(state: CallAudioState?) {
        state ?: return
        val btName = runCatching { state.activeBluetoothDevice?.name }.getOrNull() // needs BLUETOOTH_CONNECT on 31+
        _audio.value = CallAudio(
            route = AudioRouteMask.toRoute(state.route),
            supportedRoutes = AudioRouteMask.supported(state.supportedRouteMask),
            muted = state.isMuted,
            bluetoothDeviceName = btName,
        )
    }

    // endregion

    // region CallController

    override fun placeCall(number: String): PlaceCallResult {
        if (number.isBlank()) return PlaceCallResult.EmptyNumber
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            return PlaceCallResult.MissingPermission
        }
        val telecom = appContext.getSystemService(TelecomManager::class.java)
        return try {
            telecom.placeCall(Uri.fromParts("tel", number, null), Bundle())
            PlaceCallResult.Placed
        } catch (e: SecurityException) {
            PlaceCallResult.MissingPermission
        } catch (e: Exception) {
            PlaceCallResult.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    override fun answer(callId: String) {
        find(callId)?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    override fun reject(callId: String) {
        find(callId)?.reject(false, null)
    }

    override fun hangUp(callId: String) {
        val call = find(callId) ?: return
        if (stateOf(call) == TelecomStates.STATE_RINGING) call.reject(false, null) else call.disconnect()
    }

    override fun setOnHold(callId: String, hold: Boolean) {
        val call = find(callId) ?: return
        if (hold) call.hold() else call.unhold()
    }

    override fun setMuted(muted: Boolean) {
        service?.setMuted(muted)
    }

    @Suppress("DEPRECATION") // setAudioRoute is deprecated on API 34 in favour of CallEndpoint, but still honoured.
    override fun setRoute(route: AudioRoute) {
        service?.setAudioRoute(AudioRouteMask.toMask(route))
    }

    override fun playDtmf(callId: String, digit: Char) {
        find(callId)?.playDtmfTone(digit)
    }

    override fun stopDtmf(callId: String) {
        find(callId)?.stopDtmfTone()
    }

    override fun swap() {
        val held = ids.keys.firstOrNull { stateOf(it) == TelecomStates.STATE_HOLDING } ?: return
        // Telecom holds the active call automatically when another is unheld.
        held.unhold()
    }

    // endregion

    fun openInCallScreen(context: Context = appContext, answerCallId: String? = null) {
        val intent = inCallIntentFactory?.invoke(context, answerCallId) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    internal fun inCallIntent(answerCallId: String?): Intent? = inCallIntentFactory?.invoke(appContext, answerCallId)

    private fun find(callId: String): Call? = ids.entries.firstOrNull { it.value == callId }?.key

    private fun resolveName(id: String, call: Call) {
        val number = call.details.handle?.schemeSpecificPart ?: return
        val resolver = nameResolver ?: return
        scope.launch {
            val name = runCatching { resolver(number) }.getOrNull()
            if (!name.isNullOrBlank()) {
                resolvedNames[id] = name
                publish()
            }
        }
    }

    private fun publish() {
        val live = ids.map { (call, id) -> snapshotOf(call, id) }
        val list = live + recentlyEnded.values.filter { e -> live.none { it.id == e.id } }
        _calls.value = list
        notifier.update(list, ::inCallIntent)
        if (list.isEmpty()) _audio.value = CallAudio()
    }

    @Suppress("DEPRECATION")
    private fun stateOf(call: Call): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) call.details.state else call.state

    private fun snapshotOf(call: Call, id: String): CallSnapshot {
        val d = call.details
        val restricted = d.handlePresentation == TelecomManager.PRESENTATION_RESTRICTED
        val number = if (restricted) "" else d.handle?.schemeSpecificPart.orEmpty()
        val systemName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) d.contactDisplayName else null
        val name = systemName?.takeIf { it.isNotBlank() }
            ?: resolvedNames[id]
            ?: d.callerDisplayName?.takeIf { it.isNotBlank() }
            ?: if (restricted) "Private number" else null
        return CallSnapshot(
            id = id,
            number = number,
            displayName = name,
            state = TelecomStates.map(stateOf(call)),
            isIncoming = d.callDirection == Call.Details.DIRECTION_INCOMING,
            connectTimeMillis = d.connectTimeMillis.takeIf { it > 0 },
            canHold = d.can(Call.Details.CAPABILITY_HOLD) || d.can(Call.Details.CAPABILITY_SUPPORT_HOLD),
            canMute = d.can(Call.Details.CAPABILITY_MUTE),
            disconnectReason = d.disconnectCause?.let(::describe),
            isConference = d.hasProperty(Call.Details.PROPERTY_CONFERENCE),
        )
    }

    private fun describe(cause: DisconnectCause): String? {
        cause.label?.toString()?.takeIf { it.isNotBlank() }?.let { return it }
        return when (cause.code) {
            DisconnectCause.BUSY -> "Busy"
            DisconnectCause.REJECTED -> "Declined"
            DisconnectCause.MISSED -> "Missed"
            DisconnectCause.ERROR -> "Call failed"
            DisconnectCause.RESTRICTED -> "Call restricted"
            else -> null
        }
    }

    companion object {
        private const val ENDED_LINGER_MS = 1_500L

        @Volatile private var instance: TelecomCallController? = null

        fun get(context: Context): TelecomCallController =
            instance ?: synchronized(this) {
                instance ?: TelecomCallController(context.applicationContext).also { instance = it }
            }
    }
}
