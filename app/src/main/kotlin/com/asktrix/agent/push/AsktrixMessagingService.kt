package com.asktrix.agent.push

import com.asktrix.agent.core.common.log.AsktrixLog
import com.asktrix.agent.core.network.AsktrixApi
import com.asktrix.agent.core.network.dto.PushTokenRequestDto
import com.asktrix.agent.core.sync.OutboxWorker
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Firebase Cloud Messaging (§24).
 *
 * **Push payloads carry identifiers only - never customer data.** A push travels through Google's
 * infrastructure and lands on a lock screen, so a client name or a masked number would leak exactly
 * where §4 is trying to prevent leaks. The message says "something changed, and here is its id"; the
 * app then fetches the detail over the authenticated API.
 *
 * Everything here is a nudge to sync rather than a state change in itself, which also means a lost
 * or duplicated push is harmless.
 */
@Suppress("OVERRIDE_DEPRECATION")
@AndroidEntryPoint
class AsktrixMessagingService : FirebaseMessagingService() {

    @Inject lateinit var api: AsktrixApi

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch {
            runCatching { api.pushToken(PushTokenRequestDto(token)) }
                .onFailure { AsktrixLog.w(TAG, "Push token registration failed; will retry on next sign-in", it) }
        }
    }

    // FirebaseMessagingService has deprecated this signature, but it remains the callback the SDK
    // actually invokes on message delivery. Suppressed rather than annotated: marking our override
    // @Deprecated would propagate a warning to no one, since nothing in this app calls it.
    override fun onMessageReceived(message: RemoteMessage) {
        // Any push means the server has something for us. Draining the outbox also pulls fresh data
        // down, so one handler covers call outcomes, new assignments and follow-up reminders alike.
        when (message.data[KEY_TYPE]) {
            TYPE_CALL_OUTCOME, TYPE_CLIENT_ASSIGNED, TYPE_FOLLOW_UP, null ->
                OutboxWorker.enqueue(applicationContext)

            else -> AsktrixLog.d(TAG, "Ignoring unrecognised push type")
        }
    }

    private companion object {
        const val TAG = "Messaging"
        const val KEY_TYPE = "type"
        const val TYPE_CALL_OUTCOME = "call_outcome"
        const val TYPE_CLIENT_ASSIGNED = "client_assigned"
        const val TYPE_FOLLOW_UP = "follow_up"
    }
}
