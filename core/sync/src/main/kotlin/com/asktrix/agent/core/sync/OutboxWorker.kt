package com.asktrix.agent.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

/**
 * Drains the outbox whenever the network is available.
 *
 * WorkManager is the right tool here rather than a foreground service: the work is deferrable, must
 * survive process death and reboot, and must not hold a wakelock. Android 15 caps `dataSync`
 * foreground services at 6 hours per 24, and several Indian OEM battery managers freeze foreground
 * services outright - so a persistent service would be the *less* reliable choice.
 *
 * WorkManager's own retry is the outer loop; the outbox's per-item backoff is the inner one. The
 * worker returns `retry()` only when items remain, so a fully-drained queue does not reschedule.
 */
@HiltWorker
class OutboxWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val processor: OutboxProcessor,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = runCatching {
        if (processor.drain()) Result.success() else Result.retry()
    }.getOrElse { Result.retry() }

    companion object {
        const val WORK_NAME = "asktrix.outbox.drain"

        /**
         * Enqueues a drain. [ExistingWorkPolicy.APPEND_OR_REPLACE] keeps ordering: a drain requested
         * while one is running is queued behind it rather than replacing it, so no action is skipped.
         */
        fun enqueue(context: Context) {
            val request = OneTimeWorkRequestBuilder<OutboxWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }

        /**
         * A periodic safety net.
         *
         * The one-off request above is the primary path. This exists because several Indian OEM
         * battery managers silently drop pending WorkManager requests, and an outbox that stops
         * draining is invisible to the employee - their work simply never arrives. Fifteen minutes
         * is WorkManager's documented minimum period.
         */
        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<OutboxWorker>(PERIODIC_MINUTES, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        const val PERIODIC_WORK_NAME = "asktrix.outbox.periodic"
        private const val PERIODIC_MINUTES = 15L
        private const val BACKOFF_SECONDS = 30L
    }
}
