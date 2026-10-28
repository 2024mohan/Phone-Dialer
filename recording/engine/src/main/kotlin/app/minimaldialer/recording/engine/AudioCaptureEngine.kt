package app.minimaldialer.recording.engine

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioRecordingConfiguration
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaRecorder
import android.os.Process
import app.minimaldialer.recording.domain.AdtsHeader
import app.minimaldialer.recording.domain.CaptureSource
import app.minimaldialer.recording.domain.FailureReason
import app.minimaldialer.recording.domain.SignalMonitor
import java.io.IOException
import java.io.OutputStream
import java.nio.ByteOrder
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

/**
 * AudioRecord -> MediaCodec AAC-LC -> ADTS -> [output] (an encrypting stream).
 *
 * Every PCM buffer also goes through a [SignalMonitor], and the framework's
 * AudioRecordingConfiguration.isClientSilenced() is watched, so the result says what was
 * really captured rather than what was requested.
 */
internal class AudioCaptureEngine(
    private val requested: CaptureSource,
    private val output: OutputStream,
    private val onProgress: (Progress) -> Unit,
) {
    data class Progress(val elapsedMillis: Long, val level: Float, val audibleMillis: Long, val silencedBySystem: Boolean)

    data class Result(
        val sourceUsed: CaptureSource,
        val durationMillis: Long,
        val audibleMillis: Long,
        val silencedBySystem: Boolean,
        val error: Throwable?,
    )

    private var record: AudioRecord? = null
    private var codec: MediaCodec? = null
    private var thread: Thread? = null
    private val callbackExecutor = Executors.newSingleThreadExecutor()
    private val monitor = SignalMonitor(SAMPLE_RATE)
    private var framesQueued = 0L
    private var frame = ByteArray(2048)
    @Volatile private var running = false
    @Volatile private var silenced = false
    @Volatile private var loopError: Throwable? = null
    var sourceUsed: CaptureSource = requested
        private set

    /** Opens capture and encoder. Returns null on success. RECORD_AUDIO must already be granted. */
    fun open(): FailureReason? {
        val rec = openRecord(requested) ?: if (requested == CaptureSource.CALL_AUDIO) {
            // CAPTURE_AUDIO_OUTPUT was granted but the HAL refused VOICE_CALL. Fall back, and
            // record the fact in the metadata so it is labelled "microphone only".
            openRecord(CaptureSource.MICROPHONE)?.also { sourceUsed = CaptureSource.MICROPHONE }
        } else {
            null
        }
        if (rec == null) return FailureReason.SOURCE_UNAVAILABLE
        record = rec
        rec.registerAudioRecordingCallback(callbackExecutor, object : AudioManager.AudioRecordingCallback() {
            override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>) {
                val mine = configs.firstOrNull { it.clientAudioSessionId == rec.audioSessionId } ?: return
                if (mine.isClientSilenced) silenced = true
            }
        })
        return try {
            codec = createEncoder()
            null
        } catch (e: Exception) {
            rec.release()
            record = null
            FailureReason.ENCODER_ERROR
        }
    }

    fun start() {
        val rec = checkNotNull(record) { "open() first" }
        rec.startRecording()
        if (rec.recordingState != AudioRecord.RECORDSTATE_RECORDING) throw IOException("AudioRecord did not start")
        rec.activeRecordingConfiguration?.let { if (it.isClientSilenced) silenced = true }
        running = true
        thread = Thread(::loop, "call-recorder").also { it.start() }
    }

    /** Stops capture, flushes the encoder and returns what was captured. Blocks briefly. */
    fun stop(): Result {
        running = false
        runCatching { record?.stop() } // unblocks a pending read
        thread?.join(JOIN_TIMEOUT_MS)
        val result = Result(sourceUsed, monitor.durationMillis, monitor.audibleMillis, silenced, loopError)
        release()
        return result
    }

    fun release() {
        running = false
        runCatching { codec?.stop() }
        runCatching { codec?.release() }
        runCatching { record?.release() }
        codec = null
        record = null
        callbackExecutor.shutdown()
    }

    private fun loop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val rec = record ?: return
        val enc = codec ?: return
        val pcm = ShortArray(FRAME_SAMPLES)
        var lastProgressAt = 0L
        try {
            while (running) {
                val n = rec.read(pcm, 0, pcm.size, AudioRecord.READ_BLOCKING)
                if (n < 0) {
                    if (!running) break
                    throw IOException("AudioRecord.read returned $n")
                }
                if (n == 0) continue
                monitor.accept(pcm, n)
                queue(enc, pcm, n, endOfStream = false)
                drain(enc, untilEndOfStream = false)
                val elapsed = monitor.durationMillis
                if (elapsed - lastProgressAt >= PROGRESS_INTERVAL_MS) {
                    lastProgressAt = elapsed
                    onProgress(Progress(elapsed, monitor.lastLevel, monitor.audibleMillis, silenced))
                }
            }
            queue(enc, pcm, 0, endOfStream = true)
            drain(enc, untilEndOfStream = true)
            output.flush()
        } catch (t: Throwable) {
            loopError = t
        }
    }

    private fun queue(enc: MediaCodec, pcm: ShortArray, count: Int, endOfStream: Boolean) {
        var offset = 0
        var stalls = 0
        while (true) {
            val index = enc.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
            if (index < 0) {
                if (++stalls > MAX_EOS_WAITS) throw IOException("AAC encoder stalled")
                drain(enc, untilEndOfStream = false)
                continue
            }
            val buffer = checkNotNull(enc.getInputBuffer(index))
            buffer.clear()
            val samples = min(count - offset, buffer.remaining() / 2)
            buffer.order(ByteOrder.nativeOrder()).asShortBuffer().put(pcm, offset, samples)
            val ptsUs = framesQueued * 1_000_000L / SAMPLE_RATE
            framesQueued += samples
            offset += samples
            val last = offset >= count
            val flags = if (endOfStream && last) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0
            enc.queueInputBuffer(index, 0, samples * 2, ptsUs, flags)
            if (last) return
        }
    }

    private fun drain(enc: MediaCodec, untilEndOfStream: Boolean) {
        val info = MediaCodec.BufferInfo()
        var idleWaits = 0
        while (true) {
            val index = enc.dequeueOutputBuffer(info, if (untilEndOfStream) DEQUEUE_TIMEOUT_US else 0)
            when {
                index == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                    if (!untilEndOfStream || ++idleWaits > MAX_EOS_WAITS) return
                }
                index < 0 -> Unit // format/buffers changed: nothing to do for ADTS output
                else -> {
                    val buffer = checkNotNull(enc.getOutputBuffer(index))
                    val isConfig = info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
                    if (!isConfig && info.size > 0) {
                        val total = info.size + AdtsHeader.SIZE
                        if (frame.size < total) frame = ByteArray(max(total, frame.size * 2))
                        AdtsHeader.write(frame, 0, info.size, SAMPLE_RATE, CHANNELS)
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        buffer.get(frame, AdtsHeader.SIZE, info.size)
                        output.write(frame, 0, total)
                    }
                    enc.releaseOutputBuffer(index, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) return
                }
            }
        }
    }

    @SuppressLint("MissingPermission") // checked by the caller (CapabilityPolicy requires RECORD_AUDIO)
    private fun openRecord(source: CaptureSource): AudioRecord? {
        val androidSource = when (source) {
            CaptureSource.CALL_AUDIO -> MediaRecorder.AudioSource.VOICE_CALL
            CaptureSource.MICROPHONE -> MediaRecorder.AudioSource.MIC
        }
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) return null
        return try {
            val rec = AudioRecord.Builder()
                .setAudioSource(androidSource)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .build(),
                )
                .setBufferSizeInBytes(max(minBuffer * 4, FRAME_SAMPLES * 2 * 4))
                .build()
            if (rec.state == AudioRecord.STATE_INITIALIZED) rec else {
                rec.release(); null
            }
        } catch (e: Exception) { // UnsupportedOperationException, SecurityException, IllegalArgumentException
            null
        }
    }

    private fun createEncoder(): MediaCodec {
        val format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, CHANNELS).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, FRAME_SAMPLES * 2 * 4)
        }
        return MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
            configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            start()
        }
    }

    companion object {
        /** 16 kHz mono covers the telephony band (AMR-WB is 16 kHz) at a third of 48 kHz's cost. */
        const val SAMPLE_RATE = 16_000
        const val CHANNELS = 1
        const val BIT_RATE = 32_000
        private const val FRAME_SAMPLES = 1024
        private const val DEQUEUE_TIMEOUT_US = 10_000L
        private const val MAX_EOS_WAITS = 200 // ~2 s
        private const val PROGRESS_INTERVAL_MS = 250L
        private const val JOIN_TIMEOUT_MS = 5_000L
    }
}
