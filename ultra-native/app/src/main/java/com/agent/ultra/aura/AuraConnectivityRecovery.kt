package com.agent.ultra.aura

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import org.json.JSONObject

/** Observes connectivity loss and asks the bridge to reconnect when a network returns. */
class AuraConnectivityRecovery(
    context: Context,
    private val publish: (AuraEvent) -> Unit,
    private val reconnect: () -> Unit,
    private val store: AuraPresenceStore
) {
    private val manager = context.getSystemService(ConnectivityManager::class.java)
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val caps = manager.getNetworkCapabilities(network)
            val transport = when {
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "wifi"
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "cellular"
                else -> "other"
            }
            store.update(mapOf("online" to true, "transport" to transport, "lastOnlineAt" to System.currentTimeMillis()))
            publish(
                AuraEvent(
                    id = "connectivity:online:${System.currentTimeMillis()}",
                    type = "connectivity",
                    source = "android.connectivity",
                    priority = 1,
                    payload = JSONObject().put("online", true).put("transport", transport)
                )
            )
            reconnect()
        }

        override fun onLost(network: Network) {
            store.update(mapOf("online" to false, "transport" to "none", "lastOfflineAt" to System.currentTimeMillis()))
            publish(
                AuraEvent(
                    id = "connectivity:offline:${System.currentTimeMillis()}",
                    type = "connectivity",
                    source = "android.connectivity",
                    priority = 2,
                    payload = JSONObject().put("online", false).put("recovery", "bridge_reconnect_pending")
                )
            )
            publish(
                AuraEvent(
                    id = "recovery:offline:${System.currentTimeMillis()}",
                    type = "recovery",
                    source = "aura.recovery",
                    priority = 2,
                    payload = JSONObject()
                        .put("action", "reconnect")
                        .put("wifiMayNeedUserAction", true)
                        .put("reason", "android_os_restrictions")
                )
            )
        }
    }

    fun start() = runCatching { manager.registerDefaultNetworkCallback(callback) }.getOrNull()
    fun stop() = runCatching { manager.unregisterNetworkCallback(callback) }.getOrNull()
}
