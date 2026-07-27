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
