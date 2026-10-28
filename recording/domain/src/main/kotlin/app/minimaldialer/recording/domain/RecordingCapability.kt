package app.minimaldialer.recording.domain

/** Which audio the app can actually capture during a cellular call on this device. */
enum class CaptureSource {
    /** Both sides of the call via AudioSource.VOICE_CALL. Needs CAPTURE_AUDIO_OUTPUT (privileged). */
    CALL_AUDIO,
    /** The device microphone only. During a cellular call the audio policy normally silences it. */
    MICROPHONE,
}

/** Facts about the device, collected on the Android side and evaluated here. */
data class DeviceFacts(
    val sdkInt: Int,
    val manufacturer: String,
    val model: String,
    /** True only for privileged (pre-installed) apps granted android.permission.CAPTURE_AUDIO_OUTPUT. */
    val hasCaptureAudioOutput: Boolean,
    val hasRecordAudioPermission: Boolean,
    val isDefaultDialer: Boolean,
)

enum class Limitation {
    NO_RECORD_AUDIO_PERMISSION,
    NOT_DEFAULT_DIALER,
    /** Since Android 10, VOICE_CALL / VOICE_UPLINK / VOICE_DOWNLINK need CAPTURE_AUDIO_OUTPUT. */
    CALL_AUDIO_REQUIRES_PRIVILEGED_APP,
    /** Android 10+ concurrent-capture policy: during a voice call ordinary apps receive silence. */
    MIC_SILENCED_DURING_VOICE_CALLS,
    /** Mic capture records the far end only acoustically, i.e. with speakerphone on. */
    FAR_END_ONLY_VIA_SPEAKER,
}

data class RecordingCapability(
    /** The source the engine will try, or null when recording can't be attempted at all. */
    val source: CaptureSource?,
    val limitations: List<Limitation>,
    val oemHint: String?,
) {
    val canAttempt: Boolean get() = source != null
}

/**
 * Decides what to attempt. This is a static policy; the engine still verifies at runtime
 * (AudioRecord initialisation, AudioRecordingConfiguration.isClientSilenced and signal level)
 * and reports a silent capture as such.
 */
object CapabilityPolicy {
    const val ANDROID_10 = 29

    fun evaluate(facts: DeviceFacts): RecordingCapability {
        val limits = mutableListOf<Limitation>()
        if (!facts.hasRecordAudioPermission) limits += Limitation.NO_RECORD_AUDIO_PERMISSION
        if (!facts.isDefaultDialer) limits += Limitation.NOT_DEFAULT_DIALER

        val source = when {
            !facts.hasRecordAudioPermission -> null
            facts.hasCaptureAudioOutput -> CaptureSource.CALL_AUDIO
            else -> {
                if (facts.sdkInt >= ANDROID_10) {
                    limits += Limitation.CALL_AUDIO_REQUIRES_PRIVILEGED_APP
                    limits += Limitation.MIC_SILENCED_DURING_VOICE_CALLS
                }
                limits += Limitation.FAR_END_ONLY_VIA_SPEAKER
                CaptureSource.MICROPHONE
            }
        }
        return RecordingCapability(source, limits, oemHint(facts.manufacturer).takeIf { source != CaptureSource.CALL_AUDIO })
    }

    /**
     * Some manufacturers ship their own (privileged) phone app with a recorder. That is the
     * compliant route to two-way recording on those devices; this app can't use it.
     * Availability depends on region and firmware, so the wording stays conditional.
     */
    fun oemHint(manufacturer: String): String? = when (manufacturer.trim().lowercase()) {
        "samsung" -> "Samsung's own Phone app may offer call recording, depending on region and One UI version."
        "xiaomi", "redmi", "poco" -> "Xiaomi's own Phone app may offer call recording, depending on region and HyperOS/MIUI version."
        "oneplus", "oppo", "realme" -> "The manufacturer's own Phone app may offer call recording, depending on region and firmware."
        "google" -> "The Google Phone app offers call recording only in some regions."
        "motorola", "nokia", "hmd global", "vivo", "honor", "huawei", "asus", "sony" ->
            "The manufacturer's or Google's Phone app may offer call recording, depending on region and firmware."
        else -> null
    }
}
