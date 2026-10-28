package app.minimaldialer.recordings

import android.media.AudioAttributes
import android.media.MediaDataSource
import android.media.MediaPlayer
import app.minimaldialer.recording.domain.RecordingStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel

data class PlayerState(
    val recordingId: String? = null,
    val playing: Boolean = false,
    val positionMillis: Long = 0,
    val durationMillis: Long = 0,
    val error: String? = null,
)

/**
 * Plays encrypted recordings without writing plaintext to disk: MediaPlayer reads through a
 * [MediaDataSource] backed by Tink's seekable decrypting channel.
 */
class RecordingPlayer(private val store: RecordingStore) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: MediaPlayer? = null
    private var ticker: Job? = null
    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    fun toggle(id: String, knownDurationMillis: Long) {
        val current = _state.value
        val p = player
        when {
            current.recordingId == id && p != null && p.isPlaying -> {
                p.pause(); _state.value = current.copy(playing = false); ticker?.cancel()
            }
            current.recordingId == id && p != null -> {
                p.start(); _state.value = current.copy(playing = true); startTicker()
            }
            else -> play(id, knownDurationMillis)
        }
    }

    fun seekTo(fraction: Float) {
        val p = player ?: return
        val target = (fraction.coerceIn(0f, 1f) * _state.value.durationMillis).toLong()
        p.seekTo(target, MediaPlayer.SEEK_CLOSEST_SYNC)
        _state.value = _state.value.copy(positionMillis = target)
    }

    fun stop() {
        ticker?.cancel()
        player?.release()
        player = null
        _state.value = PlayerState()
    }

    private fun play(id: String, knownDurationMillis: Long) {
        stop()
        scope.launch {
            try {
                val channel = kotlinx.coroutines.withContext(Dispatchers.IO) { store.openAudio(id) }
                val mp = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    )
                    setDataSource(ChannelDataSource(channel))
                    setOnCompletionListener {
                        ticker?.cancel()
                        _state.value = _state.value.copy(playing = false, positionMillis = _state.value.durationMillis)
                    }
                    setOnErrorListener { _, what, extra ->
                        _state.value = _state.value.copy(playing = false, error = "Playback error ($what/$extra)")
                        true
                    }
                }
                player = mp
                kotlinx.coroutines.withContext(Dispatchers.IO) { mp.prepare() }
                // ADTS has no index, so MediaPlayer's duration can be an estimate; prefer ours.
                val duration = knownDurationMillis.takeIf { it > 0 } ?: mp.duration.toLong()
                mp.start()
                _state.value = PlayerState(id, playing = true, positionMillis = 0, durationMillis = duration)
                startTicker()
            } catch (e: Exception) {
                stop()
                _state.value = PlayerState(id, error = "Can't play this recording: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            while (isActive) {
                player?.let { _state.value = _state.value.copy(positionMillis = it.currentPosition.toLong()) }
                delay(250)
            }
        }
    }
}

private class ChannelDataSource(private val channel: SeekableByteChannel) : MediaDataSource() {
    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int = synchronized(this) {
        if (size == 0) return 0
        val total = channel.size()
        if (position >= total) return -1
        channel.position(position)
        val bb = ByteBuffer.wrap(buffer, offset, size)
        var read = 0
        while (bb.hasRemaining()) {
            val n = channel.read(bb)
            if (n <= 0) break
            read += n
        }
        return if (read == 0) -1 else read
    }

    override fun getSize(): Long = synchronized(this) { channel.size() }

    override fun close() = synchronized(this) { channel.close() }
}
