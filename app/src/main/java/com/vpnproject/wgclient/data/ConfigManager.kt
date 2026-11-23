package com.vpnproject.wgclient.data

import android.content.Context
import com.wireguard.config.Config
import java.io.ByteArrayInputStream
import java.io.File

/** Saves/loads the user's WireGuard config, encrypted, in app-private storage. */
class ConfigManager(context: Context) {

    private val store = SecureStore(File(context.filesDir, "wg0.enc"))

    init {
        // Remove any plaintext config left by an earlier version.
        File(context.filesDir, "wg0.conf").delete()
    }

    fun hasConfig(): Boolean = store.exists()

    fun load(): Config? = store.read()?.let { runCatching { parse(it) }.getOrNull() }

    /** Validates then saves. Returns the parsed config, or null if invalid. */
    fun save(text: String): Config? {
        val config = runCatching { parse(text) }.getOrNull() ?: return null
        return runCatching { store.write(text); config }.getOrNull()
    }

    fun delete() = store.delete()

    private fun parse(text: String): Config =
        Config.parse(ByteArrayInputStream(text.toByteArray()))
}
