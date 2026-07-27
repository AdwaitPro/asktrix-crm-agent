package com.asktrix.agent

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.asktrix.agent.core.sync.OutboxWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * WorkManager is configured here rather than by its default initializer (which is removed in the
 * manifest) so that workers can be constructor-injected by Hilt. Every background job in this app —
 * the outbox, location sampling, recording reconciliation — is a Hilt worker.
 */
@HiltAndroidApp
class AsktrixApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        super.onCreate()
        // Drain anything left over from a previous process, and install the periodic safety net.
        OutboxWorker.enqueue(this)
        OutboxWorker.enqueuePeriodic(this)
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(
                if (BuildConfig.DEBUG) android.util.Log.INFO else android.util.Log.ERROR,
            )
            .build()
}
