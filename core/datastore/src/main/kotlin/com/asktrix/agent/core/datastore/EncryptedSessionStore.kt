package com.asktrix.agent.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.asktrix.agent.core.common.session.EmployeeStore
import com.asktrix.agent.core.common.session.SessionTokenStore
import com.asktrix.agent.core.common.session.SignedInEmployee
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
) : SessionTokenStore, EmployeeStore {

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

    /**
     * The signed-in employee, including what their role permits.
     *
     * Encrypted like the tokens: the role and permission list say what this person is allowed to do,
     * and leaving that in plaintext on disk would be an obvious thing to tamper with.
     */
    override suspend fun currentEmployee(): SignedInEmployee? {
        val prefs = context.sessionDataStore.data.first()
        val raw = prefs[EMPLOYEE]?.let(crypto::decrypt) ?: return null
        val parts = raw.split(FIELD_SEPARATOR)
        if (parts.size < EXPECTED_FIELDS) return null
        return SignedInEmployee(
            employeeId = parts[0],
            employeeCode = parts[1],
            displayName = parts[2],
            role = parts[3],
            permissions = parts[4].split(LIST_SEPARATOR).filter { it.isNotBlank() },
            allowedStatuses = parts[5].split(LIST_SEPARATOR).filter { it.isNotBlank() },
        )
    }

    override suspend fun saveEmployee(employee: SignedInEmployee) {
        val raw = listOf(
            employee.employeeId,
            employee.employeeCode,
            employee.displayName,
            employee.role,
            employee.permissions.joinToString(LIST_SEPARATOR),
            employee.allowedStatuses.joinToString(LIST_SEPARATOR),
        ).joinToString(FIELD_SEPARATOR)
        context.sessionDataStore.edit { it[EMPLOYEE] = crypto.encrypt(raw) }
    }

    private companion object {
        val ACCESS_TOKEN: Preferences.Key<String> = stringPreferencesKey("access_token")
        val REFRESH_TOKEN: Preferences.Key<String> = stringPreferencesKey("refresh_token")
        val DEVICE_ID: Preferences.Key<String> = stringPreferencesKey("device_id")
        val EMPLOYEE: Preferences.Key<String> = stringPreferencesKey("employee")

        // Field separators chosen to be absent from ids, names and permission strings.
        const val FIELD_SEPARATOR = "\u001F"
        const val LIST_SEPARATOR = ","
        const val EXPECTED_FIELDS = 6
    }
}
