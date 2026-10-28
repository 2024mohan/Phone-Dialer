package app.minimaldialer.recording.engine

import android.content.Context
import app.minimaldialer.recording.domain.StreamCipher
import com.google.crypto.tink.KeyTemplates
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.StreamingAead
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import com.google.crypto.tink.streamingaead.StreamingAeadConfig
import java.io.InputStream
import java.io.OutputStream
import java.nio.channels.SeekableByteChannel

/**
 * AES-256-GCM-HKDF streaming encryption (4 KB segments) from Tink. The keyset lives in
 * app-private SharedPreferences, itself encrypted by a non-exportable Android Keystore key.
 * Backups are disabled for the app, so the keyset and recordings never leave the device;
 * uninstalling the app destroys both.
 */
class TinkStreamCipher(context: Context) : StreamCipher {

    private val aead: StreamingAead

    init {
        StreamingAeadConfig.register()
        val handle = AndroidKeysetManager.Builder()
            .withSharedPref(context.applicationContext, KEYSET_NAME, PREF_FILE)
            .withKeyTemplate(KeyTemplates.get("AES256_GCM_HKDF_4KB"))
            .withMasterKeyUri(MASTER_KEY_URI)
            .build()
            .keysetHandle
        aead = handle.getPrimitive(RegistryConfiguration.get(), StreamingAead::class.java)
    }

    override fun encrypting(out: OutputStream, associatedData: ByteArray): OutputStream =
        aead.newEncryptingStream(out, associatedData)

    override fun decrypting(input: InputStream, associatedData: ByteArray): InputStream =
        aead.newDecryptingStream(input, associatedData)

    override fun decrypting(channel: SeekableByteChannel, associatedData: ByteArray): SeekableByteChannel =
        aead.newSeekableDecryptingChannel(channel, associatedData)

    private companion object {
        const val KEYSET_NAME = "recordings_keyset"
        const val PREF_FILE = "recordings_keyset_prefs"
        const val MASTER_KEY_URI = "android-keystore://minimaldialer_recordings_master_key"
    }
}
