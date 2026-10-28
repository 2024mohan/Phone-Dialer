package app.minimaldialer

import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.minimaldialer.calling.domain.AudioRoute
import app.minimaldialer.calling.domain.CallSelection
import app.minimaldialer.calling.domain.CallState
import app.minimaldialer.calling.ui.InCallScreen
import app.minimaldialer.calling.ui.InCallViewModel
import app.minimaldialer.calling.ui.RecordingMessages
import app.minimaldialer.core.designsystem.MinimalDialerTheme
import app.minimaldialer.core.model.ThemeMode
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Shown over the lock screen (manifest: showWhenLocked / turnScreenOn). Holds a proximity
 * wake lock while the earpiece is in use so the screen turns off against the ear.
 */
class InCallActivity : ComponentActivity() {

    private var proximityLock: PowerManager.WakeLock? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        handleAnswer(intent)

        val appContainer = container
        setContent {
            val theme by appContainer.preferences.themeMode.collectAsStateWithLifecycle(ThemeMode.SYSTEM)
            MinimalDialerTheme(theme) {
                val vm: InCallViewModel = viewModel { InCallViewModel(appContainer.calls, appContainer.recorder) }
                InCallScreen(vm, onFinished = {
                    // The call ended before the user saw how the recording turned out.
                    RecordingMessages.outcome(appContainer.recorder.state.value)?.let {
                        Toast.makeText(applicationContext, it, Toast.LENGTH_LONG).show()
                        appContainer.recorder.acknowledge()
                    }
                    finishAndRemoveTask()
                })
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(appContainer.calls.calls, appContainer.calls.audio) { calls, audio -> calls to audio }.collect { (calls, audio) ->
                    val primary = CallSelection.primary(calls)
                    val earpiece = audio.route == AudioRoute.EARPIECE
                    val inCall = primary != null && primary.state != CallState.RINGING && primary.state.isLive
                    setProximity(inCall && earpiece)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAnswer(intent)
    }

    override fun onStop() {
        super.onStop()
        setProximity(false)
    }

    private fun handleAnswer(intent: Intent?) {
        val id = intent?.getStringExtra(EXTRA_ANSWER_CALL_ID) ?: return
        intent.removeExtra(EXTRA_ANSWER_CALL_ID)
        container.calls.answer(id)
    }

    private fun setProximity(on: Boolean) {
        val pm = getSystemService(PowerManager::class.java) ?: return
        if (on) {
            val hasSensor = getSystemService(SensorManager::class.java)?.getDefaultSensor(Sensor.TYPE_PROXIMITY) != null
            if (!hasSensor || !pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) return
            if (proximityLock?.isHeld == true) return
            proximityLock = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "minimaldialer:proximity").also {
                it.setReferenceCounted(false)
                it.acquire(MAX_CALL_MS)
            }
        } else {
            proximityLock?.let { if (it.isHeld) it.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY) }
            proximityLock = null
        }
    }

    companion object {
        private const val EXTRA_ANSWER_CALL_ID = "answer_call_id"
        private const val MAX_CALL_MS = 4 * 60 * 60 * 1000L

        fun intent(context: Context, answerCallId: String?): Intent =
            Intent(context, InCallActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                if (answerCallId != null) putExtra(EXTRA_ANSWER_CALL_ID, answerCallId)
            }
    }
}
