package com.asktrix.agent.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.asktrix.agent.core.common.session.SessionTokenStore
import com.asktrix.agent.core.common.session.SessionTokens
import com.asktrix.agent.core.security.crypto.KeystoreCrypto
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore(name = "asktrix_session")

/**
 * Session token storage, encrypted with a hardware-backed Keystore key.
 *
 * DataStore itself is plain on disk, so every value is encrypted with [KeystoreCrypto] before it is
 * written. The stored bytes are useless without the Keystore key, which cannot be extracted from the
 * device.
 *
 * `androidx.security:security-crypto` (EncryptedSharedPreferences) is deliberately not used: that
 * library is deprecated. Encrypting values ourselves with an explicit AES-GCM Keystore key is the
 * supported path and makes the threat model visible in the code rather than hidden in a dependency.
 *
 * A decryption failure is treated as "no session" rather than an error. That is the correct
 * behaviour when the key was invalidated or the file was tampered with: sign in again, and purge
 * (§3).
 */
@Singleton
class EncryptedSessionStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val crypto: KeystoreCrypto,
) : SessionTokenStore {

    /** Emits whether a session currently exists, so the UI can route without polling. */
    val isSignedIn: Flow<Boolean> =
        context.sessionDataStore.data.map { it[ACCESS_TOKEN] != null }

    override suspend fun current(): SessionTokens? {
        val prefs = context.sessionDataStore.data.first()
        val access = prefs[ACCESS_TOKEN]?.let(crypto::decrypt) ?: return null
        val refresh = prefs[REFRESH_TOKEN]?.let(crypto::decrypt) ?: return null
        val device = prefs[DEVICE_ID]?.let(crypto::decrypt) ?: return null
        return SessionTokens(access, refresh, device)
    }

    override suspend fun save(tokens: SessionTokens) {
        context.sessionDataStore.edit { prefs ->
            prefs[ACCESS_TOKEN] = crypto.encrypt(tokens.accessToken)
            prefs[REFRESH_TOKEN] = crypto.encrypt(tokens.refreshToken)
            prefs[DEVICE_ID] = crypto.encrypt(tokens.deviceId)
        }
    }

    override suspend fun clear() {
        context.sessionDataStore.edit { it.clear() }
    }

    private companion object {
        val ACCESS_TOKEN: Preferences.Key<String> = stringPreferencesKey("access_token")
        val REFRESH_TOKEN: Preferences.Key<String> = stringPreferencesKey("refresh_token")
        val DEVICE_ID: Preferences.Key<String> = stringPreferencesKey("device_id")
    }
}
