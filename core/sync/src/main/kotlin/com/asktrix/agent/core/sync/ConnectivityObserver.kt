package com.asktrix.agent.core.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Observes real connectivity (§9).
 *
 * Uses `NET_CAPABILITY_VALIDATED`, not merely "a network exists". That distinction is the whole
 * point on Indian mobile networks: a captive portal at a café, or a data pack that has run out, both
 * present a connected network that cannot reach the internet. Treating those as online would send
 * the outbox into a pointless retry storm.
 */
@Singleton
class ConnectivityObserver @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    val isOnline: Flow<Boolean> = callbackFlow {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val online = mutableSetOf<Network>()

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                val usable = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                if (usable) online.add(network) else online.remove(network)
                trySend(online.isNotEmpty())
            }

            override fun onLost(network: Network) {
                online.remove(network)
                trySend(online.isNotEmpty())
            }

            override fun onUnavailable() {
                trySend(false)
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        manager.registerNetworkCallback(request, callback)

        val current = manager.activeNetwork?.let(manager::getNetworkCapabilities)
        trySend(
            current?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true,
        )

        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
