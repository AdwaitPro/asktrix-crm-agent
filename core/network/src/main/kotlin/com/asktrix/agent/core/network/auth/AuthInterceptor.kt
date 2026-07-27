package com.asktrix.agent.core.network.auth

import com.asktrix.agent.core.common.session.SessionTokenStore
import com.asktrix.agent.core.common.session.SessionTokens

import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches the bearer token to every request except the auth endpoints that mint one.
 *
 * `runBlocking` is correct here: OkHttp interceptors are synchronous by contract and already run on
 * a background dispatcher, so the read is a short suspend on a thread that is expected to block.
 */
class AuthInterceptor(private val tokens: SessionTokenStore) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.encodedPath.substringAfterLast('/') in UNAUTHENTICATED_PATHS) {
            return chain.proceed(request)
        }
        val accessToken = runBlocking { tokens.current()?.accessToken }
            ?: return chain.proceed(request)

        return chain.proceed(
            request.newBuilder()
                .header("Authorization", "Bearer $accessToken")
                .build(),
        )
    }

    private companion object {
        val UNAUTHENTICATED_PATHS = setOf("login", "refresh")
    }
}
