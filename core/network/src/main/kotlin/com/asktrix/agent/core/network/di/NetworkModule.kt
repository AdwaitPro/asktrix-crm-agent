package com.asktrix.agent.core.network.di

import com.asktrix.agent.core.network.AsktrixApi
import com.asktrix.agent.core.network.auth.AuthInterceptor
import com.asktrix.agent.core.common.session.SessionTokenStore
import com.asktrix.agent.core.network.auth.TokenAuthenticator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    /** The CRM base URL, supplied by the app module from its build variant. */
    const val BASE_URL = "asktrix.baseUrl"

    @Provides
    @Singleton
    fun json(): Json = Json {
        // The CRM may add fields ahead of an app release; an unknown key must not break the client.
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun okHttpClient(tokens: SessionTokenStore, @Named(BASE_URL) baseUrl: String, json: Json): OkHttpClient =
        OkHttpClient.Builder()
            // Field agents are on mobile data with poor coverage; these are deliberately generous
            // but bounded, so a stalled request surfaces as a Timeout the outbox can retry.
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor(AuthInterceptor(tokens))
            .authenticator(TokenAuthenticator(tokens, baseUrl, json))
            // NOTE: no logging interceptor. Request and response bodies carry client data, and
            // logging them would violate invariant 4 (CLAUDE.md) regardless of build type.
            .build()

    @Provides
    @Singleton
    fun retrofit(client: OkHttpClient, json: Json, @Named(BASE_URL) baseUrl: String): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun asktrixApi(retrofit: Retrofit): AsktrixApi = retrofit.create(AsktrixApi::class.java)

    private const val CONNECT_TIMEOUT_SECONDS = 15L
    private const val READ_TIMEOUT_SECONDS = 30L
    private const val WRITE_TIMEOUT_SECONDS = 30L
}
