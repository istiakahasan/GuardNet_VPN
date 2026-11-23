package com.vpnproject.wgclient.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Encrypts small blobs (the WireGuard config, which contains the private key) at rest.
 * The AES key is generated inside the Android Keystore and never leaves it, so nothing
 * secret is hard-coded in the app or stored in readable form on disk.
 */
class SecureStore(private val file: File) {

    fun exists() = file.exists()

    fun write(plain: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        // File layout: [1 byte IV length][IV][ciphertext]
        file.writeBytes(byteArrayOf(cipher.iv.size.toByte()) + cipher.iv + cipher.doFinal(plain.toByteArray()))
    }

    fun read(): String? = runCatching {
        val bytes = file.readBytes()
        val ivLen = bytes[0].toInt()
        val iv = bytes.copyOfRange(1, 1 + ivLen)
        val cipher = Cipher.getInstance(TRANSFORMATION)
            .apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv)) }
        String(cipher.doFinal(bytes, 1 + ivLen, bytes.size - 1 - ivLen))
    }.getOrNull()

    fun delete() {
        file.delete()
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private companion object {
        const val KEY_ALIAS = "wg_config_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
