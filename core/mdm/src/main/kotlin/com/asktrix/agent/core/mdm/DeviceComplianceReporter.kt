package com.asktrix.agent.core.mdm

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Debug
import android.provider.Settings
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Observed device state, gathered for the server to judge (§14–§20, §25–§27).
 *
 * Two things this class is **not**:
 *
 *  1. **It is not a Device Policy Controller.** Google restricts the Android Management API to
 *     commercial EMM vendors, and Play Protect has blocked non-approved custom DPCs at provisioning
 *     since 2026. Restrictions are enforced by the EMM's policy (`mdm/policy.json`), not here. This
 *     class only reads and reports (ADR-0004).
 *
 *  2. **It is not a security control.** Every check below is bypassable by anyone who cares — a
 *     rooted device can lie about all of it. The authoritative verdict is the server's verification
 *     of the Keystore attestation statement. These signals are defence in depth and, just as
 *     usefully, field diagnostics.
 */
@Singleton
class DeviceComplianceReporter @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    data class Signals(
        val rootIndicators: Boolean,
        val emulatorIndicators: Boolean,
        val debuggerAttached: Boolean,
        val developerOptionsEnabled: Boolean,
        val screenLockSet: Boolean,
        val isDeviceOwnerManaged: Boolean,
        val backgroundRestricted: Boolean,
        val appStandbyBucket: Int?,
    )

    fun collect(): Signals = Signals(
        rootIndicators = hasRootIndicators(),
        emulatorIndicators = hasEmulatorIndicators(),
        debuggerAttached = Debug.isDebuggerConnected(),
        developerOptionsEnabled = isDeveloperOptionsEnabled(),
        screenLockSet = isScreenLockSet(),
        isDeviceOwnerManaged = isDeviceOwnerManaged(),
        backgroundRestricted = isBackgroundRestricted(),
        appStandbyBucket = appStandbyBucket(),
    )

    /** Common su locations. Trivially defeated by Magisk's hiding; reported anyway. */
    private fun hasRootIndicators(): Boolean =
        SU_PATHS.any { runCatching { File(it).exists() }.getOrDefault(false) } ||
            (Build.TAGS?.contains("test-keys") == true)

    private fun hasEmulatorIndicators(): Boolean =
        Build.FINGERPRINT.startsWith("generic") ||
            Build.FINGERPRINT.contains("vbox") ||
            Build.FINGERPRINT.contains("emulator") ||
            Build.MODEL.contains("sdk_gphone") ||
            Build.MODEL.contains("Emulator") ||
            Build.HARDWARE in setOf("goldfish", "ranchu", "vbox86") ||
            Build.PRODUCT in setOf("sdk", "sdk_x86", "vbox86p")

    private fun isDeveloperOptionsEnabled(): Boolean = runCatching {
        Settings.Global.getInt(context.contentResolver, Settings.Global.ADB_ENABLED, 0) == 1
    }.getOrDefault(false)

    private fun isScreenLockSet(): Boolean = runCatching {
        context.getSystemService<android.app.KeyguardManager>()?.isDeviceSecure == true
    }.getOrDefault(false)

    /** Confirms the EMM enrolment actually took effect on this handset. */
    private fun isDeviceOwnerManaged(): Boolean = runCatching {
        val dpm = context.getSystemService<DevicePolicyManager>() ?: return@runCatching false
        // The Asktrix app is never the device owner (ADR-0004), so this asks whether *any* app is —
        // i.e. whether the EMM enrolment actually took on this handset.
        dpm.activeAdmins?.isNotEmpty() == true ||
            dpm.isDeviceOwnerApp(context.packageName) ||
            dpm.isProfileOwnerApp(context.packageName)
    }.getOrDefault(false)

    /**
     * The single most valuable field in this report.
     *
     * Android's compatibility definition requires a compliant OEM restriction to be visible here, so
     * a `true` value is direct evidence that a battery manager is throttling us — which is the usual
     * reason location data goes quiet in the field.
     */
    private fun isBackgroundRestricted(): Boolean = runCatching {
        context.getSystemService<ActivityManager>()?.isBackgroundRestricted == true
    }.getOrDefault(false)

    /**
     * Bucket 5 (`STANDBY_BUCKET_EXEMPTED`) confirms an EMM exemption is in force. Checked instead of
     * `isIgnoringBatteryOptimizations`, which reads false even when an EMM role exemption is applied
     * — the exemption is implemented through standby buckets rather than the power allowlist.
     */
    private fun appStandbyBucket(): Int? = runCatching {
        context.getSystemService<UsageStatsManager>()?.appStandbyBucket
    }.getOrNull()

    private companion object {
        val SU_PATHS = listOf(
            "/system/bin/su", "/system/xbin/su", "/sbin/su", "/system/su",
            "/vendor/bin/su", "/su/bin/su", "/data/local/xbin/su", "/data/local/bin/su",
        )
    }
}
