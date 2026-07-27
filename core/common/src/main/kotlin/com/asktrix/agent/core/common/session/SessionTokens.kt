package com.asktrix.agent.core.common.session

/**
 * The token pair plus the device it is bound to.
 *
 * Declared in `:core:common` because two modules need it and neither should depend on the other:
 * `:core:network` uses tokens, `:core:datastore` stores them. The network layer knows how to *use*
 * tokens; it does not know where they live.
 */
data class SessionTokens(
    val accessToken: String,
    val refreshToken: String,
    val deviceId: String,
)

interface SessionTokenStore {

    /** Current tokens, or null when signed out. */
    suspend fun current(): SessionTokens?

    suspend fun save(tokens: SessionTokens)

    /**
     * Clears tokens and triggers a purge of the encrypted cache (§3).
     * Called on logout, on a refused refresh, and on an integrity failure.
     */
    suspend fun clear()
}


/**
 * Who is signed in, and what their role permits (§2).
 *
 * Held so every screen can render for the actual role rather than showing the same UI to a sales
 * agent and an accounts clerk. The UI hides what is absent; the server still enforces it, because
 * hiding a button is not authorisation.
 */
data class SignedInEmployee(
    val employeeId: String,
    val employeeCode: String,
    val displayName: String,
    val role: String,
    val permissions: List<String>,
    val allowedStatuses: List<String>,
) {
    fun can(permission: String): Boolean = permission in permissions

    /** Human-readable group name, e.g. RELATIONSHIP_MANAGER becomes "Relationship manager". */
    val roleLabel: String
        get() = role.split('_').joinToString(" ") { word ->
            word.lowercase().replaceFirstChar { it.uppercase() }
        }.replaceFirstChar { it.uppercase() }
}

interface EmployeeStore {
    suspend fun currentEmployee(): SignedInEmployee?
    suspend fun saveEmployee(employee: SignedInEmployee)
}
