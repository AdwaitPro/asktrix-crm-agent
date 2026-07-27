package com.asktrix.agent.core.security

import android.content.Context
import android.os.Build
import com.asktrix.agent.core.security.crypto.KeystoreCrypto
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A stable per-install device identifier for device binding and for the call-record device identity
 * the OSP conditions require.
 *
 * Deliberately **not** a hardware identifier. `ANDROID_ID`, IMEI and the serial number are either
 * restricted, privacy-sensitive, or both - Google Play policy treats hardware IDs as personal data.
 * A random UUID generated once per install, stored encrypted, identifies the installation for
 * binding purposes without collecting anything about the user or the handset.
 *
 * It resets on reinstall or on a cache purge, which is correct: a reinstalled app is a new binding
 * and should require sign-in.
 */
@Singleton
class DeviceIdentity @Inject constructor(
    @ApplicationContext private val context: Context,
    private val crypto: KeystoreCrypto,
) {

    private val file: File get() = File(context.filesDir, FILE_NAME)

    @Synchronized
    fun deviceId(): String {
        if (file.exists()) {
            crypto.decrypt(file.readText())?.let { return it }
            // Undecryptable means the key was rotated or the file was tampered with. Start over
            // rather than trusting it.
            file.delete()
        }
        val id = UUID.randomUUID().toString()
        file.writeText(crypto.encrypt(id))
        return id
    }

    fun clear() {
        runCatching { file.delete() }
    }

    val manufacturer: String get() = Build.MANUFACTURER ?: "unknown"
    val model: String get() = Build.MODEL ?: "unknown"
    val osVersion: String get() = "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"

    private companion object {
        const val FILE_NAME = "device_identity.bin"
    }
}
