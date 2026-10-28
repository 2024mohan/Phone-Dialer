package app.minimaldialer.recording.engine

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import app.minimaldialer.recording.domain.DeviceFacts

object DeviceFactsProbe {
    const val CAPTURE_AUDIO_OUTPUT = "android.permission.CAPTURE_AUDIO_OUTPUT"

    fun collect(context: Context): DeviceFacts {
        val roles = context.getSystemService(RoleManager::class.java)
        return DeviceFacts(
            sdkInt = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER.orEmpty(),
            model = Build.MODEL.orEmpty(),
            hasCaptureAudioOutput = granted(context, CAPTURE_AUDIO_OUTPUT),
            hasRecordAudioPermission = granted(context, Manifest.permission.RECORD_AUDIO),
            isDefaultDialer = roles?.isRoleHeld(RoleManager.ROLE_DIALER) == true,
        )
    }

    private fun granted(context: Context, permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}
