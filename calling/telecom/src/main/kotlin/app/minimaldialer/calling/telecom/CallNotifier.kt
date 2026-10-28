package app.minimaldialer.calling.telecom

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import app.minimaldialer.calling.domain.CallSelection
import app.minimaldialer.calling.domain.CallSnapshot
import app.minimaldialer.calling.domain.CallState

/**
 * Incoming-call (heads-up / full-screen) and ongoing-call notifications. Telecom plays the
 * ringtone, so the incoming channel is silent.
 */
internal class CallNotifier(private val context: Context) {

    private val nm = NotificationManagerCompat.from(context)

    init {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_INCOMING, "Incoming calls", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null)
                enableVibration(false) // Telecom's ringer handles vibration per user settings
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ONGOING, "Ongoing calls", NotificationManager.IMPORTANCE_LOW),
        )
    }

    fun canPostNotifications(): Boolean = nm.areNotificationsEnabled()

    fun update(calls: List<CallSnapshot>, inCallIntent: (String?) -> Intent?) {
        val ringing = calls.firstOrNull { it.state == CallState.RINGING }
        val primary = CallSelection.primary(calls.filter { it.state.isLive })
        when {
            ringing != null -> post(incoming(ringing, inCallIntent))
            primary != null -> post(ongoing(primary, inCallIntent))
            else -> nm.cancel(NOTIFICATION_ID)
        }
    }

    @Suppress("MissingPermission") // guarded by areNotificationsEnabled()
    private fun post(notification: android.app.Notification) {
        if (nm.areNotificationsEnabled()) {
            try {
                nm.notify(NOTIFICATION_ID, notification)
            } catch (_: SecurityException) {
                // POST_NOTIFICATIONS revoked between the check and the call.
            }
        }
    }

    private fun incoming(call: CallSnapshot, inCallIntent: (String?) -> Intent?): android.app.Notification {
        val content = activityIntent(inCallIntent(null), REQ_CONTENT)
        // Answer goes straight to the activity: Android 12+ blocks notification trampolines.
        val answer = activityIntent(inCallIntent(call.id), REQ_ANSWER)
        val decline = CallActionReceiver.pendingIntent(context, CallActionReceiver.ACTION_DECLINE, call.id)
        val person = Person.Builder().setName(call.title).setImportant(true).build()
        val builder = NotificationCompat.Builder(context, CHANNEL_INCOMING)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(call.title)
            .setContentText("Incoming call")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(content)
            .setFullScreenIntent(content, true)
        if (answer != null && decline != null) {
            builder.setStyle(NotificationCompat.CallStyle.forIncomingCall(person, decline, answer))
        }
        return builder.build()
    }

    private fun ongoing(call: CallSnapshot, inCallIntent: (String?) -> Intent?): android.app.Notification {
        val hangUp = CallActionReceiver.pendingIntent(context, CallActionReceiver.ACTION_HANG_UP, call.id)
        val builder = NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle(call.title)
            .setContentText(
                when (call.state) {
                    CallState.DIALING -> "Calling…"
                    CallState.ON_HOLD -> "On hold"
                    else -> "Ongoing call"
                },
            )
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(activityIntent(inCallIntent(null), REQ_CONTENT))
        call.connectTimeMillis?.let { builder.setWhen(it).setUsesChronometer(true).setShowWhen(true) }
        if (hangUp != null) builder.addAction(0, "Hang up", hangUp)
        return builder.build()
    }

    private fun activityIntent(intent: Intent?, requestCode: Int): PendingIntent? = intent?.let {
        it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        PendingIntent.getActivity(context, requestCode, it, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val CHANNEL_INCOMING = "calls_incoming"
        const val CHANNEL_ONGOING = "calls_ongoing"
        const val NOTIFICATION_ID = 1001
        private const val REQ_CONTENT = 1
        private const val REQ_ANSWER = 2
    }
}
