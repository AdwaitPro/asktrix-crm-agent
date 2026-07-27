package com.asktrix.agent.core.security.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AES-GCM encryption with a key held in the Android Keystore.
 *
 * The key material never leaves the Keystore and is never serialised - this class can encrypt and
 * decrypt, but cannot export the key, so a rooted attacker who copies the app's files still cannot
 * read the ciphertext without the hardware-backed key.
 *
 * StrongBox (a discrete secure element) is used when the device has one, with a documented fallback
 * to the TEE-backed Keystore. StrongBox is not universal - requiring it would exclude much of the
 * likely fleet, so availability is detected rather than assumed.
 *
 * GCM is authenticated encryption: tampering with stored bytes causes decryption to fail rather than
 * silently returning corrupt data, which is what lets the cache detect integrity failure (§3).
 */
@Singleton
class KeystoreCrypto @Inject constructor() {

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    /** Encrypts [plaintext], returning Base64 of `iv || ciphertext`. */
    fun encrypt(plaintext: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + ciphertext, Base64.NO_WRAP)
    }

    /**
     * Decrypts a value produced by [encrypt].
     *
     * Returns null rather than throwing when the data is corrupt, the key was invalidated (for
     * example the user removed their screen lock), or the value was tampered with. The caller treats
     * null as "session gone, sign in again and purge the cache" - a recoverable state, not a crash.
     */
    fun decrypt(encoded: String): String? = runCatching {
        val bytes = Base64.decode(encoded, Base64.NO_WRAP)
        if (bytes.size <= GCM_IV_LENGTH) return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(
            Cipher.DECRYPT_MODE,
            secretKey(),
            GCMParameterSpec(GCM_TAG_LENGTH_BITS, bytes, 0, GCM_IV_LENGTH),
        )
        String(
            cipher.doFinal(bytes, GCM_IV_LENGTH, bytes.size - GCM_IV_LENGTH),
            Charsets.UTF_8,
        )
    }.getOrNull()

    /** Destroys the key, rendering every value it encrypted permanently unreadable (§3 purge). */
    fun destroyKey() {
        runCatching { keyStore.deleteEntry(KEY_ALIAS) }
    }

    private fun secretKey(): SecretKey {
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        return generateKey(strongBox = true) ?: generateKey(strongBox = false)
            ?: error("Unable to create a Keystore key on this device")
    }

    private fun generateKey(strongBox: Boolean): SecretKey? = runCatching {
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            // Deliberately NOT setUserAuthenticationRequired: background sync and location upload
            // must work while the device is locked (§9, §10). Confidentiality comes from the
            // hardware-backed key plus the device's own lock screen, not from per-use auth.
            .setIsStrongBoxBacked(strongBox)
            .build()

        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
            .apply { init(spec) }
            .generateKey()
    }.getOrNull()

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "asktrix.session.v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_SIZE_BITS = 256
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_LENGTH_BITS = 128
    }
}
