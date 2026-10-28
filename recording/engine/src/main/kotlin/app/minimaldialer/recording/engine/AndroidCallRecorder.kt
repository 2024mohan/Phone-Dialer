package app.minimaldialer.recording.engine

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import app.minimaldialer.recording.domain.CallRecorder
import app.minimaldialer.recording.domain.CapabilityPolicy
import app.minimaldialer.recording.domain.CaptureSource
import app.minimaldialer.recording.domain.FailureReason
import app.minimaldialer.recording.domain.RecorderEvent
import app.minimaldialer.recording.domain.RecorderReducer
import app.minimaldialer.recording.domain.RecorderState
import app.minimaldialer.recording.domain.RecordingCapability
import app.minimaldialer.recording.domain.RecordingLibrary
import app.minimaldialer.recording.domain.RecordingMeta
import app.minimaldialer.recording.domain.RecordingResult
import app.minimaldialer.recording.domain.RecordingStore
import app.minimaldialer.recording.domain.RecordingTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.Executors

/**
 * Recording lifecycle. Capture runs inside [CallRecorderService] (a microphone-type foreground
 * service), whose notification stays up for exactly as long as audio is being captured.
 * All blocking work happens on a single worker thread, so start/stop are strictly ordered.
 */
class AndroidCallRecorder(
    context: Context,
    private val library: RecordingLibrary,
    private val clock: () -> Long = System::currentTimeMillis,
) : CallRecorder {

    private val appContext = context.applicationContext
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "recorder-control") }

    private val _state = MutableStateFlow<RecorderState>(RecorderState.Idle)
    override val state: StateFlow<RecorderState> = _state.asStateFlow()

    private val _capability = MutableStateFlow(CapabilityPolicy.evaluate(DeviceFactsProbe.collect(appContext)))
    override val capability: StateFlow<RecordingCapability> = _capability.asStateFlow()

    // Only touched on [worker].
    private var pendingTarget: RecordingTarget? = null
    private var session: Session? = null

    private class Session(val target: RecordingTarget, val writer: RecordingStore.Writer, val engine: AudioCaptureEngine)

    /** Text for the foreground notification; read by the service. */
    internal var notificationText: String = ""
        private set

    /** When the current recording was requested; drives the notification's chronometer. */
    internal var notificationSince: Long = 0L
        private set

    init {
        active = this
    }

    override fun refreshCapability() {
        _capability.value = CapabilityPolicy.evaluate(DeviceFactsProbe.collect(appContext))
    }

    override fun start(target: RecordingTarget) {
        refreshCapability()
        val source = _capability.value.source
        if (source == null) {
            dispatch(RecorderEvent.Error(FailureReason.NO_PERMISSION, "Microphone permission is required"))
            return
        }
        if (_state.value.isBusy) return
        dispatch(RecorderEvent.StartRequested(source))
        notificationText = notificationTextFor(source, silenced = false)
        notificationSince = clock()
        worker.execute { pendingTarget = target }
        try {
            ContextCompat.startForegroundService(appContext, Intent(appContext, CallRecorderService::class.java).setAction(CallRecorderService.ACTION_START))
        } catch (e: Exception) { // ForegroundServiceStartNotAllowedException / SecurityException
            worker.execute { pendingTarget = null }
            dispatch(RecorderEvent.Error(FailureReason.SERVICE_NOT_ALLOWED, e.message))
        }
    }

    override fun stop() {
        if (!_state.value.isBusy) return
        dispatch(RecorderEvent.StopRequested)
        worker.execute { finish() }
    }

    override fun acknowledge() = dispatch(RecorderEvent.Acknowledge)

    /** The service is in the foreground; it's now allowed to open the microphone. */
    internal fun onServiceForeground() = worker.execute {
        val target = pendingTarget ?: return@execute stopService()
        pendingTarget = null
        val starting = _state.value as? RecorderState.Starting ?: return@execute stopService()
        val requested = starting.source
        val writer = try {
            library.store.begin(
                RecordingMeta(
                    id = "", number = target.number, contactName = target.contactName,
                    startedAtMillis = clock(), durationMillis = 0, audibleMillis = 0,
                    source = requested, incoming = target.incoming,
                    sampleRate = AudioCaptureEngine.SAMPLE_RATE, channels = AudioCaptureEngine.CHANNELS,
                ),
            )
        } catch (e: Exception) {
            dispatch(RecorderEvent.Error(FailureReason.STORAGE_ERROR, e.message))
            return@execute stopService()
        }
        val engine = AudioCaptureEngine(requested, writer.stream) { p ->
            dispatch(RecorderEvent.Progress(p.elapsedMillis, p.level, p.audibleMillis, p.silencedBySystem))
            if (p.silencedBySystem && !notificationText.contains("silenc")) {
                notificationText = notificationTextFor(requested, silenced = true)
                CallRecorderService.refreshNotification(appContext)
            }
        }
        val openError = engine.open()
        if (openError != null) {
            writer.abort()
            dispatch(RecorderEvent.Error(openError))
            return@execute stopService()
        }
        try {
            engine.start()
        } catch (e: Exception) {
            engine.release()
            writer.abort()
            dispatch(RecorderEvent.Error(FailureReason.SOURCE_UNAVAILABLE, e.message))
            return@execute stopService()
        }
        session = Session(target, writer, engine)
        if (engine.sourceUsed != requested) notificationText = notificationTextFor(engine.sourceUsed, silenced = false)
        dispatch(RecorderEvent.Started(target.callId, clock()))
        // A stop requested while we were starting is honoured now.
        if (_state.value is RecorderState.Stopping) finish()
    }

    internal fun onServiceStartFailed(e: Throwable) = worker.execute {
        pendingTarget = null
        dispatch(RecorderEvent.Error(FailureReason.SERVICE_NOT_ALLOWED, e.message))
    }

    /** Runs on [worker]. */
    private fun finish() {
        val s = session ?: run {
            // Stop arrived before capture opened: nothing was recorded. The service, if it is
            // still starting, sees no pending target in onServiceForeground() and stops itself
            // (stopping it here, before startForeground(), can crash on some Android versions).
            pendingTarget = null
            _state.update { if (it is RecorderState.Stopping) RecorderState.Idle else it }
            return
        }
        session = null
        val result = s.engine.stop()
        if (result.durationMillis == 0L) {
            s.writer.abort()
            dispatch(RecorderEvent.Error(FailureReason.ENCODER_ERROR, result.error?.message ?: "No audio was captured"))
            return stopService()
        }
        try {
            val meta = s.writer.commit {
                it.copy(
                    durationMillis = result.durationMillis,
                    audibleMillis = result.audibleMillis,
                    silencedBySystem = result.silencedBySystem,
                    source = result.sourceUsed,
                )
            }
            library.published(meta)
            dispatch(
                RecorderEvent.Completed(
                    RecordingResult(meta.id, result.sourceUsed, result.durationMillis, result.audibleMillis, result.silencedBySystem),
                ),
            )
        } catch (e: Exception) {
            s.writer.abort()
            dispatch(RecorderEvent.Error(FailureReason.STORAGE_ERROR, e.message))
        }
        stopService()
    }

    private fun stopService() {
        appContext.stopService(Intent(appContext, CallRecorderService::class.java))
    }

    private fun dispatch(event: RecorderEvent) = _state.update { RecorderReducer.reduce(it, event) }

    private fun notificationTextFor(source: CaptureSource, silenced: Boolean): String = when {
        silenced -> "Recording, but Android is silencing the microphone during this call"
        source == CaptureSource.CALL_AUDIO -> "Recording call audio"
        else -> "Recording microphone only"
    }

    internal companion object {
        @Volatile var active: AndroidCallRecorder? = null
    }
}
