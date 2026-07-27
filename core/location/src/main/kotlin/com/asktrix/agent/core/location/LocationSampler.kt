package com.asktrix.agent.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.BatteryManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.asktrix.agent.core.common.result.AsktrixError
import com.asktrix.agent.core.common.result.AsktrixResult
import com.asktrix.agent.core.common.time.TimeSource
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/** One GPS sample, with the context needed to diagnose field failures. */
data class LocationSample(
    val latitudeE7: Double,
    val longitudeE7: Double,
    val accuracyMetres: Float,
    val sampledAtMillis: Long,
    val isMocked: Boolean,
    val batteryPercent: Int?,
)

/**
 * Takes a single location fix (§10).
 *
 * `getCurrentLocation` rather than `requestLocationUpdates`: the requirement is a sample every ten
 * minutes, not a continuous stream. A one-shot fix lets the GPS radio sleep between samples, which
 * is the difference between a phone that lasts a shift and one that does not.
 *
 * `PRIORITY_BALANCED_POWER_ACCURACY` is deliberate — the requirement is to know which area an agent
 * is in, not to survey a building. High accuracy would cost far more battery for precision nobody
 * uses.
 *
 * `isMock` is reported honestly rather than acted on here. Whether a mocked fix invalidates an
 * attendance record is a policy decision, and policy decisions belong on the server.
 */
@Singleton
class LocationSampler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val time: TimeSource,
) {

    private val client = LocationServices.getFusedLocationProviderClient(context)

    fun hasForegroundPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Background location is a separate grant on API 29+ and cannot be requested in the same dialog
     * as the foreground permission.
     */
    fun hasBackgroundPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    suspend fun sample(): AsktrixResult<LocationSample> {
        if (!hasForegroundPermission()) {
            return AsktrixResult.Failure(
                AsktrixError.PermissionDenied(
                    permission = Manifest.permission.ACCESS_FINE_LOCATION,
                    permanentlyDenied = false,
                ),
            )
        }

        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
            .setDurationMillis(FIX_TIMEOUT_MILLIS)
            .setMaxUpdateAgeMillis(MAX_FIX_AGE_MILLIS)
            .build()

        return try {
            suspendCancellableCoroutine { continuation ->
                @Suppress("MissingPermission") // Checked above.
                val task = client.getCurrentLocation(request, null)
                task.addOnSuccessListener { location ->
                    if (location == null) {
                        // Indoors with no fix is normal, not an error worth alarming anyone about.
                        continuation.resume(
                            AsktrixResult.Failure(AsktrixError.Unexpected("no fix available")),
                        )
                    } else {
                        continuation.resume(
                            AsktrixResult.Success(
                                LocationSample(
                                    latitudeE7 = location.latitude,
                                    longitudeE7 = location.longitude,
                                    accuracyMetres = location.accuracy,
                                    sampledAtMillis = time.now().toEpochMilli(),
                                    isMocked = location.isMockedCompat(),
                                    batteryPercent = batteryPercent(),
                                ),
                            ),
                        )
                    }
                }
                task.addOnFailureListener { error ->
                    continuation.resume(AsktrixResult.Failure(AsktrixError.Unexpected(error.message)))
                }
            }
        } catch (e: SecurityException) {
            AsktrixResult.Failure(
                AsktrixError.PermissionDenied(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    permanentlyDenied = true,
                    debugContext = e.message,
                ),
            )
        }
    }

    /**
     * Battery level travels with each sample. It is the only practical way to tell, from the server,
     * whether a gap in an agent's location history was a dead battery or an OEM battery manager
     * silently freezing the app.
     */
    private fun batteryPercent(): Int? = runCatching {
        context.getSystemService(BatteryManager::class.java)
            ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }.getOrNull()?.takeIf { it in 0..MAX_PERCENT }

    /**
     * `Location.isMock` was added in API 31; `isFromMockProvider` covers 29 and 30.
     *
     * The older call is deprecated but is the only option below 31, and minSdk is 29 — so the
     * deprecation is suppressed deliberately rather than the check being dropped. Silently skipping
     * mock detection on older handsets would be worse: those are exactly the cheaper devices most
     * likely to be running a location spoofer.
     */
    @Suppress("DEPRECATION")
    private fun Location.isMockedCompat(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) isMock else isFromMockProvider

    private companion object {
        const val FIX_TIMEOUT_MILLIS = 20_000L
        const val MAX_FIX_AGE_MILLIS = 60_000L
        const val MAX_PERCENT = 100
    }
}
