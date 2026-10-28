package app.minimaldialer.recording.domain

import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.SeekableByteChannel

/**
 * Authenticated streaming encryption. The Android implementation wraps Tink's
 * AES256_GCM_HKDF_4KB StreamingAead with its keyset protected by an Android Keystore key.
 * [associatedData] binds a ciphertext to its recording id, so files can't be swapped.
 */
interface StreamCipher {
    fun encrypting(out: OutputStream, associatedData: ByteArray): OutputStream
    fun decrypting(input: InputStream, associatedData: ByteArray): InputStream
    /** Random-access plaintext view, used for seeking during playback. */
    fun decrypting(channel: SeekableByteChannel, associatedData: ByteArray): SeekableByteChannel
}
