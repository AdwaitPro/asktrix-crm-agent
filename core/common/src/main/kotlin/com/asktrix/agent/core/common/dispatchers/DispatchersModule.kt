package com.asktrix.agent.core.common.dispatchers

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Module
@InstallIn(SingletonComponent::class)
object DispatchersModule {

    @Provides
    @Dispatcher(AsktrixDispatcher.IO)
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @Dispatcher(AsktrixDispatcher.DEFAULT)
    fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Dispatcher(AsktrixDispatcher.MAIN)
    fun mainDispatcher(): CoroutineDispatcher = Dispatchers.Main.immediate

    @Provides
    @Singleton
    fun dispatcherProvider(
        @Dispatcher(AsktrixDispatcher.IO) io: CoroutineDispatcher,
        @Dispatcher(AsktrixDispatcher.DEFAULT) default: CoroutineDispatcher,
        @Dispatcher(AsktrixDispatcher.MAIN) main: CoroutineDispatcher,
    ): DispatcherProvider = object : DispatcherProvider {
        override val io: CoroutineDispatcher = io
        override val default: CoroutineDispatcher = default
        override val main: CoroutineDispatcher = main
    }

    /**
     * A SupervisorJob so one failed background task cannot cancel the whole application scope -
     * a failed recording upload must not stop location sampling.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun applicationScope(
        @Dispatcher(AsktrixDispatcher.DEFAULT) dispatcher: CoroutineDispatcher,
    ): CoroutineScope = CoroutineScope(SupervisorJob() + dispatcher)
}
