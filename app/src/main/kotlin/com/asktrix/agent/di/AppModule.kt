package com.asktrix.agent.di

import com.asktrix.agent.BuildConfig
import com.asktrix.agent.core.common.session.SessionTokenStore
import com.asktrix.agent.core.datastore.EncryptedSessionStore
import com.asktrix.agent.core.network.di.NetworkModule
import com.asktrix.agent.feature.auth.AppVersion
import com.asktrix.agent.feature.settings.AppVersionName
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

/**
 * The composition root. Only the app module reads `BuildConfig`, so feature and core modules stay
 * independent of build variants and remain testable in isolation.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    @Named(NetworkModule.BASE_URL)
    fun baseUrl(): String = BuildConfig.CRM_BASE_URL

    @Provides
    @Singleton
    fun appVersion(): AppVersion = AppVersion(BuildConfig.VERSION_NAME)

    @Provides
    @Singleton
    fun appVersionName(): AppVersionName = AppVersionName(BuildConfig.VERSION_NAME)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindings {

    @Binds
    @Singleton
    abstract fun sessionTokenStore(impl: EncryptedSessionStore): SessionTokenStore
}
