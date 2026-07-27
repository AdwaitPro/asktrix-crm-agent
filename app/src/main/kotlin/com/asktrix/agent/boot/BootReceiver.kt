package com.asktrix.agent.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.asktrix.agent.core.sync.OutboxWorker

/**
 * Resumes background sync after a reboot (§22).
 *
 * Deliberately modest in what it does. It enqueues WorkManager work and nothing else — it does not
 * start a foreground service, because Android 15 restricts which foreground service types may be
 * started from BOOT_COMPLETED, and location tracking should resume from check-in rather than
 * silently at boot. That is also the correct compliance behaviour: a device that reboots overnight
 * must not start tracking someone before their shift.
 *
 * `RECEIVE_BOOT_COMPLETED` is also not a guarantee. Several Indian OEM skins suppress boot receivers
 * for apps the user has not whitelisted, which is why the enrollment runbook has a per-OEM autostart
 * step. WorkManager's own persistence is the real safety net here.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED ||
            intent.action == Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            OutboxWorker.enqueue(context.applicationContext)
        }
    }
}
