package com.asktrix.agent.core.common.time

import android.os.SystemClock
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The production [TimeSource].
 *
 * [elapsedRealtimeMillis] uses `SystemClock.elapsedRealtime()` rather than `System.currentTimeMillis()`
 * because it is monotonic and includes deep sleep — it cannot jump backwards when the user changes
 * the clock or when NTP corrects it. Every duration and backoff calculation depends on that.
 *
 * [serverSkewMillis] is updated whenever the CRM reports its clock, giving the app a measure of how
 * far the device clock has drifted. That is diagnostic, not corrective: anything compliance-relevant
 * is decided server-side precisely because this value can be large and deliberate.
 */
@Singleton
class SystemTimeSource @Inject constructor() : TimeSource {

    private val skew = AtomicLong(0)

    override fun now(): Instant = Instant.now()

    override fun elapsedRealtimeMillis(): Long = SystemClock.elapsedRealtime()

    override val serverSkewMillis: Long get() = skew.get()

    /** Records the drift between the server's clock and this device's. */
    fun observeServerTime(serverTime: Instant) {
        skew.set(serverTime.toEpochMilli() - System.currentTimeMillis())
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class TimeModule {

    @Binds
    @Singleton
    abstract fun timeSource(impl: SystemTimeSource): TimeSource
}
