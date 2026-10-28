package app.minimaldialer.recording.domain

import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.KeysetHandle
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.SeekableByteChannel

/** Same Tink primitive and template as the Android implementation, with an in-memory keyset. */
class TinkTestCipher : StreamCipher {
    private val aead: StreamingAead

    init {
        StreamingAeadConfig.register()
        aead = KeysetHandle.generateNew(KeyTemplates.get("AES256_GCM_HKDF_4KB"))
            .getPrimitive(RegistryConfiguration.get(), StreamingAead::class.java)
    }

    override fun encrypting(out: OutputStream, associatedData: ByteArray): OutputStream = aead.newEncryptingStream(out, associatedData)
    override fun decrypting(input: InputStream, associatedData: ByteArray): InputStream = aead.newDecryptingStream(input, associatedData)
    override fun decrypting(channel: SeekableByteChannel, associatedData: ByteArray): SeekableByteChannel =
        aead.newSeekableDecryptingChannel(channel, associatedData)
}
