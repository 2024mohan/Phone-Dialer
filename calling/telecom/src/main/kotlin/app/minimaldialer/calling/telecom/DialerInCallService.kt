package app.minimaldialer.calling.telecom

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService

/** Telecom binds this while there are calls and this app holds the default dialer role. */
class DialerInCallService : InCallService() {

    private val controller get() = TelecomCallController.get(this)

    override fun onCreate() {
        super.onCreate()
        controller.attach(this)
    }

    override fun onCallAdded(call: Call) {
        controller.onCallAdded(this, call)
    }

    override fun onCallRemoved(call: Call) {
        controller.onCallRemoved(call)
    }

    @Deprecated("Deprecated in API 34; still delivered on all supported versions")
    override fun onCallAudioStateChanged(audioState: CallAudioState?) {
        controller.onAudioStateChanged(audioState)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        controller.detach(this)
        return super.onUnbind(intent)
    }
}
