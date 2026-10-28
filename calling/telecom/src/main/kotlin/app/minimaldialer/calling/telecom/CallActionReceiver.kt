package app.minimaldialer.calling.telecom

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Decline / hang-up from the notification. These don't open UI, so a receiver is fine. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_CALL_ID) ?: return
        val controller = TelecomCallController.get(context)
        when (intent.action) {
            ACTION_DECLINE -> controller.reject(id)
            ACTION_HANG_UP -> controller.hangUp(id)
        }
    }

    companion object {
        const val ACTION_DECLINE = "app.minimaldialer.action.DECLINE"
        const val ACTION_HANG_UP = "app.minimaldialer.action.HANG_UP"
        const val EXTRA_CALL_ID = "call_id"

        fun pendingIntent(context: Context, action: String, callId: String): PendingIntent? {
            val intent = Intent(context, CallActionReceiver::class.java).setAction(action).putExtra(EXTRA_CALL_ID, callId)
            return PendingIntent.getBroadcast(
                context, action.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
