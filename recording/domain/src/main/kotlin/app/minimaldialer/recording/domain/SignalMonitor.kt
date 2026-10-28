package app.minimaldialer.recording.domain

import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.max

/**
 * Watches 16-bit PCM as it is captured. Used to tell the user, truthfully, whether a
 * recording contains any signal: on many devices the capture opens fine but the audio
 * policy delivers zeros during a call.
 */
class SignalMonitor(
    private val sampleRate: Int,
    /** Peak amplitude at or below which a buffer counts as silent (about -60 dBFS). */
    private val silenceThreshold: Int = 33,
) {
    var totalFrames: Long = 0; private set
    var audibleFrames: Long = 0; private set
    var peak: Int = 0; private set
    /** Peak of the most recent buffer, for a live level meter. */
    var lastPeak: Int = 0; private set

    fun accept(samples: ShortArray, count: Int = samples.size) {
        var p = 0
        for (i in 0 until count) {
            val v = abs(samples[i].toInt())
            if (v > p) p = v
        }
        lastPeak = p
        peak = max(peak, p)
        totalFrames += count
        if (p > silenceThreshold) audibleFrames += count
    }

    val durationMillis: Long get() = if (sampleRate == 0) 0 else totalFrames * 1000 / sampleRate
    val audibleMillis: Long get() = if (sampleRate == 0) 0 else audibleFrames * 1000 / sampleRate

    /** True when nothing above the silence threshold was ever captured. */
    val isEntirelySilent: Boolean get() = totalFrames > 0 && audibleFrames == 0L

    /** Level of the last buffer in 0..1 on a dB scale from -60 dBFS to 0 dBFS. */
    val lastLevel: Float get() = levelOf(lastPeak)

    companion object {
        fun levelOf(peak: Int): Float {
            if (peak <= 0) return 0f
            val db = 20 * log10(peak / 32767.0)
            return ((db + 60) / 60).coerceIn(0.0, 1.0).toFloat()
        }
    }
}
