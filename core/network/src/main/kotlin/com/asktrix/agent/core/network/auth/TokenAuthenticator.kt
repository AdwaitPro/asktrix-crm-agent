package com.asktrix.agent.core.network.auth

import com.asktrix.agent.core.common.session.SessionTokenStore
import com.asktrix.agent.core.common.session.SessionTokens

import com.asktrix.agent.core.network.dto.AuthSessionDto
import com.asktrix.agent.core.network.dto.RefreshRequestDto
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.Authenticator
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.Route

/**
 * Refreshes the access token on a 401 and replays the original request.
 *
 * Two details that matter:
 *  - **Bounded retries.** OkHttp calls an authenticator repeatedly on repeated 401s; without a depth
 *    limit a permanently-rejected token becomes an infinite loop.
 *  - **A separate, interceptor-free client for the refresh call.** Using the main client would send
 *    the dead access token through [AuthInterceptor] and recurse straight back into here.
 *
 * Refresh tokens are single-use and rotate. If the server refuses, the session is cleared and the
 * local cache is purged (§3) — the user must sign in again.
 */
class TokenAuthenticator(
    private val tokens: SessionTokenStore,
    private val baseUrl: String,
    private val json: Json,
) : Authenticator {

    private val refreshClient = OkHttpClient.Builder().build()

    override fun authenticate(route: Route?, response: Response): Request? {
        if (responseDepth(response) >= MAX_REFRESH_ATTEMPTS) return null

        val current = runBlocking { tokens.current() } ?: return null

        // Another request may already have refreshed while this one was queued.
        val staleToken = response.request.header("Authorization")?.removePrefix("Bearer ")
        if (staleToken != null && staleToken != current.accessToken) {
            return response.request.newBuilder()
                .header("Authorization", "Bearer ${current.accessToken}")
                .build()
        }

        val refreshed = runBlocking { refresh(current) }
        if (refreshed == null) {
            runBlocking { tokens.clear() }
            return null
        }

        return response.request.newBuilder()
            .header("Authorization", "Bearer ${refreshed.accessToken}")
            .build()
    }

    private suspend fun refresh(current: SessionTokens): SessionTokens? {
        val payload = json.encodeToString(
            RefreshRequestDto.serializer(),
            RefreshRequestDto(current.refreshToken, current.deviceId),
        )
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/auth/refresh")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return runCatching {
            refreshClient.newCall(request).execute().use { httpResponse ->
                if (!httpResponse.isSuccessful) return@use null
                val body = httpResponse.body.string()
                val session = json.decodeFromString(AuthSessionDto.serializer(), body)
                SessionTokens(
                    accessToken = session.accessToken,
                    refreshToken = session.refreshToken,
                    deviceId = current.deviceId,
                ).also { tokens.save(it) }
            }
        }.getOrNull()
    }

    private fun responseDepth(response: Response): Int {
        var depth = 1
        var prior = response.priorResponse
        while (prior != null) {
            depth++
            prior = prior.priorResponse
        }
        return depth
    }

    private companion object {
        const val MAX_REFRESH_ATTEMPTS = 2
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
