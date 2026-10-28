package app.minimaldialer.recording.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat

/**
 * Holds the foreground state while capturing. Its notification is the persistent recording
 * indicator outside the app: ongoing, not dismissible, with a Stop action. The system's
 * microphone privacy indicator (Android 12+) is shown by the OS and is not touched.
 */
class CallRecorderService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val recorder = AndroidCallRecorder.active
        if (recorder == null) {
            stopSelf(); return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_STOP -> {
                recorder.stop(); return START_NOT_STICKY
            }
            ACTION_START -> try {
                ServiceCompat.startForeground(
                    this, NOTIFICATION_ID, buildNotification(this, recorder.notificationText, recorder.notificationSince),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
                )
                recorder.onServiceForeground()
            } catch (e: Exception) {
                recorder.onServiceStartFailed(e)
                stopSelf()
            }
            else -> stopSelf()
        }
        // Never restart on its own: a restart would mean recording without a user action.
        return START_NOT_STICKY
    }

    companion object {
        const val ACTION_START = "app.minimaldialer.recording.START"
        const val ACTION_STOP = "app.minimaldialer.recording.STOP"
        private const val CHANNEL = "recording"
        private const val NOTIFICATION_ID = 2001

        /** Optional: lets the app make the notification open the in-call screen. */
        @Volatile var contentIntentFactory: ((Context) -> Intent)? = null

        internal fun refreshNotification(context: Context) {
            val recorder = AndroidCallRecorder.active ?: return
            val nm = NotificationManagerCompat.from(context)
            if (!nm.areNotificationsEnabled()) return
            try {
                nm.notify(NOTIFICATION_ID, buildNotification(context, recorder.notificationText, recorder.notificationSince))
            } catch (_: SecurityException) {
            }
        }

        private fun buildNotification(context: Context, text: String, since: Long): Notification {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Call recording", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    setSound(null, null)
                    description = "Shown for as long as a call is being recorded"
                },
            )
            val stop = PendingIntent.getService(
                context, 0,
                Intent(context, CallRecorderService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val content = contentIntentFactory?.invoke(context)?.let {
                PendingIntent.getActivity(context, 1, it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            }
            return NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.presence_audio_online)
                .setContentTitle("● Recording")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setUsesChronometer(true)
                .setWhen(since)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
                .setContentIntent(content)
                .addAction(0, "Stop recording", stop)
                .build()
        }
    }
}
