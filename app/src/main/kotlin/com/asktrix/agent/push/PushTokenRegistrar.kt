package com.asktrix.agent.push

import com.asktrix.agent.BuildConfig
import com.asktrix.agent.core.common.log.AsktrixLog
import com.asktrix.agent.core.network.AsktrixApi
import com.asktrix.agent.core.network.dto.PushTokenRequestDto
import com.asktrix.agent.feature.auth.PushRegistration
import com.google.firebase.messaging.FirebaseMessaging
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Registers this device's FCM token with the CRM (§24).
 *
 * `onNewToken` only fires when Firebase mints a *new* token — typically once per install. An
 * employee who signs out and back in, or a device restored from backup, would otherwise never
 * register, and pushes would silently go nowhere. So registration also runs after every successful
 * sign-in, which is idempotent server-side.
 *
 * Everything here is best-effort. Push is an optimisation over polling: if registration fails the
 * app still syncs, just less promptly. It must never block sign-in.
 */
@Singleton
class PushTokenRegistrar @Inject constructor(
    private val api: AsktrixApi,
) : PushRegistration {

    override suspend fun register(): Boolean {
        if (!BuildConfig.FCM_ENABLED) return false

        val token = currentToken() ?: return false
        return runCatching { api.pushToken(PushTokenRequestDto(token)).isSuccessful }
            .onFailure { AsktrixLog.w(TAG, "Push token registration failed; sync still works", it) }
            .getOrDefault(false)
    }

    private suspend fun currentToken(): String? = suspendCancellableCoroutine { continuation ->
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener { token -> if (continuation.isActive) continuation.resume(token) }
            .addOnFailureListener { if (continuation.isActive) continuation.resume(null) }
    }

    private companion object {
        const val TAG = "PushToken"
    }
}
