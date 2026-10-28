package app.minimaldialer.recording.domain

/**
 * ADTS framing for raw AAC-LC frames from MediaCodec. ADTS is a streamable container:
 * no index has to be patched at the end, so frames can go straight into an encrypting
 * stream and a crash mid-call still leaves a playable file up to the last full frame.
 */
object AdtsHeader {
    const val SIZE = 7
    private const val PROFILE_AAC_LC = 2
    private val SAMPLE_RATES = intArrayOf(96000, 88200, 64000, 48000, 44100, 32000, 24000, 22050, 16000, 12000, 11025, 8000, 7350)

    fun frequencyIndex(sampleRate: Int): Int =
        SAMPLE_RATES.indexOf(sampleRate).also { require(it >= 0) { "Unsupported AAC sample rate $sampleRate" } }

    /** Writes a 7-byte header (no CRC) for a payload of [payloadLength] bytes into [out] at [offset]. */
    fun write(out: ByteArray, offset: Int, payloadLength: Int, sampleRate: Int, channels: Int) {
        require(channels in 1..7) { "channels must be 1..7" }
        val frameLength = payloadLength + SIZE
        require(frameLength < (1 shl 13)) { "AAC frame too large: $frameLength" }
        val freq = frequencyIndex(sampleRate)
        out[offset] = 0xFF.toByte()
        out[offset + 1] = 0xF1.toByte() // sync, MPEG-4, layer 0, no CRC
        out[offset + 2] = (((PROFILE_AAC_LC - 1) shl 6) or (freq shl 2) or (channels shr 2)).toByte()
        out[offset + 3] = (((channels and 3) shl 6) or (frameLength shr 11)).toByte()
        out[offset + 4] = ((frameLength shr 3) and 0xFF).toByte()
        out[offset + 5] = (((frameLength and 7) shl 5) or 0x1F).toByte()
        out[offset + 6] = 0xFC.toByte()
    }

    fun header(payloadLength: Int, sampleRate: Int, channels: Int): ByteArray =
        ByteArray(SIZE).also { write(it, 0, payloadLength, sampleRate, channels) }

    /** Parses the frame length from a header, or -1 if [b] at [offset] is not an ADTS sync word. */
    fun frameLength(b: ByteArray, offset: Int): Int {
        if (b.size < offset + SIZE) return -1
        if (b[offset].toInt() and 0xFF != 0xFF || b[offset + 1].toInt() and 0xF0 != 0xF0) return -1
        return ((b[offset + 3].toInt() and 0x03) shl 11) or
            ((b[offset + 4].toInt() and 0xFF) shl 3) or
            ((b[offset + 5].toInt() and 0xE0) shr 5)
    }
}
