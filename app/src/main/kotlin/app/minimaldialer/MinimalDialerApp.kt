package app.minimaldialer

import android.app.Application
import android.content.Context
import android.content.Intent
import app.minimaldialer.calling.domain.CallSelection
import app.minimaldialer.calling.domain.CallState
import app.minimaldialer.calling.telecom.TelecomCallController
import app.minimaldialer.contacts.data.ProviderCallLogRepository
import app.minimaldialer.contacts.data.ProviderContactsRepository
import app.minimaldialer.recording.domain.RecorderState
import app.minimaldialer.recording.domain.RecordingLibrary
import app.minimaldialer.recording.domain.RecordingStore
import app.minimaldialer.recording.engine.AndroidCallRecorder
import app.minimaldialer.recording.engine.CallRecorderService
import app.minimaldialer.recording.engine.TinkStreamCipher
import app.minimaldialer.recordings.RecordingPlayer
import app.minimaldialer.recordings.RecordingSharer
import app.minimaldialer.settings.AppPreferences
import app.minimaldialer.settings.SharedCopies
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.io.File

/** Manual dependency wiring. Small enough that a DI framework would add more than it saves. */
class AppContainer(context: Context) {
    private val app = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val contacts = ProviderContactsRepository(app)
    val callLog = ProviderCallLogRepository(app)
    val calls: TelecomCallController = TelecomCallController.get(app)

    /** noBackupFilesDir: app-private and excluded from backups by definition. */
    val recordingsDir = File(app.noBackupFilesDir, "recordings")
    val library by lazy { RecordingLibrary(RecordingStore(recordingsDir, TinkStreamCipher(app)), Dispatchers.IO) }
    val recorder by lazy { AndroidCallRecorder(app, library) }
    val player by lazy { RecordingPlayer(library.store) }
    val sharer by lazy { RecordingSharer(app, library) }
    val preferences = AppPreferences(app)
    val sharedCopies = object : SharedCopies {
        override fun bytes() = sharer.sharedCopiesBytes()
        override fun clear() = sharer.clearSharedCopies()
    }

    fun start() {
        calls.inCallIntentFactory = { ctx, answerId -> InCallActivity.intent(ctx, answerId) }
        calls.nameResolver = { number -> contacts.findByNumber(number)?.displayName }
        CallRecorderService.contentIntentFactory = { ctx -> InCallActivity.intent(ctx, null) }

        appScope.launch(Dispatchers.IO) {
            // Decrypted share copies are only meant to live while the share sheet is open.
            sharer.clearSharedCopies()
            // Nothing can be recording at process start, so this can't race a live session.
            runCatching { library.recoverInterrupted() }
        }

        // A recording never outlives the call it belongs to. Runs off the main thread because
        // touching `recorder` first creates the Tink keyset (an Android Keystore operation).
        appScope.launch(Dispatchers.Default) {
            combine(calls.calls, recorder.state) { list, rec -> list to rec }.collect { (list, rec) ->
                val callId = (rec as? RecorderState.Recording)?.callId ?: return@collect
                val call = list.firstOrNull { it.id == callId }
                if (call == null || call.state == CallState.DISCONNECTED || call.state == CallState.DISCONNECTING ||
                    !CallSelection.hasLiveCall(list)
                ) {
                    recorder.stop()
                }
            }
        }
    }
}

class MinimalDialerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this).also { it.start() }
    }
}

val Context.container: AppContainer get() = (applicationContext as MinimalDialerApp).container

internal fun Intent.dialNumber(): String? =
    data?.takeIf { it.scheme == "tel" }?.schemeSpecificPart
