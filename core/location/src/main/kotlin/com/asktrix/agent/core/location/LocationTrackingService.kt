package com.asktrix.agent.core.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.asktrix.agent.core.common.log.AsktrixLog
import com.asktrix.agent.core.common.result.AsktrixResult
import com.asktrix.agent.core.database.entity.OutboxKind
import com.asktrix.agent.core.sync.Outbox
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Samples location every ten minutes while the employee is checked in (§10).
 *
 * **Why a foreground service and not WorkManager.** `PeriodicWorkRequest` has a documented minimum
 * interval of 15 minutes, so it cannot meet a 10-minute requirement. `AlarmManager` exact alarms are
 * restricted from Android 12 and are the wrong tool for routine sampling. A foreground service with
 * `foregroundServiceType="location"` is the supported mechanism for user-visible, ongoing location
 * work — and the persistent notification is a feature here, not a cost: DPDP notice obligations mean
 * the employee should be able to see, at any moment, that tracking is on.
 *
 * **Why it is tied to check-in rather than always running.** §10 says working hours only, and that is
 * a compliance boundary, not a preference. Starting at check-in and stopping at check-out means the
 * app is not tracking anyone on their own time — and it also sidesteps the Android 15 six-hour cap
 * that applies to `dataSync` services, since a `location` service has no such limit.
 *
 * Samples are enqueued in the outbox, never uploaded inline, so a dead network never loses a sample
 * and never blocks the loop.
 */
@AndroidEntryPoint
class LocationTrackingService : Service() {

    @Inject lateinit var sampler: LocationSampler

    @Inject lateinit var outbox: Outbox

    @Inject lateinit var json: Json

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Default + job)
    private var samplingJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }

        startForegroundSafely()
        if (samplingJob?.isActive != true) {
            samplingJob = scope.launch { sampleLoop() }
        }
        // START_STICKY: if the system reclaims the process mid-shift, tracking resumes.
        return START_STICKY
    }

    private fun startForegroundSafely() {
        createChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Location tracking active")
            .setContentText("Recorded during working hours while you are checked in.")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        try {
            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                } else {
                    0
                },
            )
        } catch (e: IllegalStateException) {
            // Android 12+ throws ForegroundServiceStartNotAllowedException (an IllegalStateException
            // subclass) when a foreground service is started from the background. Stopping cleanly
            // is correct; check-in will start it again from the foreground.
            AsktrixLog.w(TAG, "Foreground start refused by the platform", e)
            stopSelf()
        }
    }

    private suspend fun sampleLoop() {
        while (scope.isActive) {
            when (val result = sampler.sample()) {
                is AsktrixResult.Success -> enqueue(result.data)
                is AsktrixResult.Failure ->
                    // A missing fix indoors is routine. The next cycle will try again.
                    AsktrixLog.d(TAG, "Sample unavailable: ${result.error::class.simpleName}")
            }
            delay(SAMPLE_INTERVAL_MILLIS)
        }
    }

    private suspend fun enqueue(sample: LocationSample) {
        val payload = buildJsonObject {
            put(
                "pings",
                JsonArray(
                    listOf(
                        buildJsonObject {
                            val sampledAt = java.time.Instant
                                .ofEpochMilli(sample.sampledAtMillis)
                                .toString()
                            put("sampledAt", JsonPrimitive(sampledAt))
                            put(
                                "location",
                                JsonObject(
                                    mapOf(
                                        "latitude" to JsonPrimitive(sample.latitudeE7),
                                        "longitude" to JsonPrimitive(sample.longitudeE7),
                                        "accuracyMetres" to JsonPrimitive(sample.accuracyMetres),
                                    ),
                                ),
                            )
                            put("isMocked", JsonPrimitive(sample.isMocked))
                            sample.batteryPercent?.let { put("batteryPercent", JsonPrimitive(it)) }
                        },
                    ),
                ),
            )
        }
        outbox.enqueue(OutboxKind.LOCATION_BATCH, targetId = null, payload = payload.toString())
    }

    private fun createChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Location tracking",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shown while your location is being recorded during working hours."
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        samplingJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "LocationTracking"
        private const val CHANNEL_ID = "asktrix.location"
        private const val NOTIFICATION_ID = 4201
        private const val ACTION_STOP = "com.asktrix.agent.location.STOP"

        /** §10: every 10 minutes. */
        private const val SAMPLE_INTERVAL_MILLIS = 10 * 60 * 1000L

        /** Must be called from the foreground — at check-in, or from a visible screen. */
        fun start(context: Context) {
            context.startForegroundService(Intent(context, LocationTrackingService::class.java))
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, LocationTrackingService::class.java).setAction(ACTION_STOP),
            )
        }
    }
}
