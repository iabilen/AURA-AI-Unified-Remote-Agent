package com.agent.ultra.aura

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * Secure outbound bridge transport.
 *
 * AURA initiates a WSS connection to a relay/bridge endpoint. Nothing is exposed as
 * a listening socket on the phone. The relay can later be backed by an MCP/custom
 * connector without changing AURA Core or Brain.
 */
class AuraBridgeClient(
    private val context: Context,
    private val core: AuraCore,
    private val onAnswer: (String) -> Unit,
) {
    companion object {
        private const val PREFS = "aura_bridge"
        private const val ENDPOINT = "endpoint"
        private const val TOKEN = "token"
        private const val DEVICE_ID = "device_id"
        private const val TAG = "AURABridge"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private var socket: WebSocket? = null
    private val deviceId: String = prefs.getString(DEVICE_ID, null)
        ?: UUID.randomUUID().toString().also { prefs.edit().putString(DEVICE_ID, it).apply() }

    fun configure(endpoint: String, token: String) {
        require(endpoint.startsWith("wss://")) { "AURA Bridge endpoint must use wss://" }
        require(token.length >= 24) { "AURA Bridge token is too short" }
        prefs.edit().putString(ENDPOINT, endpoint.trim()).putString(TOKEN, token).apply()
        connect()
    }

    fun connect() {
        val endpoint = prefs.getString(ENDPOINT, null)?.trim().orEmpty()
        val token = prefs.getString(TOKEN, null).orEmpty()
        if (endpoint.isEmpty() || token.isEmpty()) return
        if (!endpoint.startsWith("wss://")) {
            Log.w(TAG, "Bridge disabled: endpoint is not wss")
            return
        }
        socket?.cancel()
        val request = Request.Builder()
            .url(endpoint)
            .header("Authorization", "Bearer $token")
            .build()
        socket = client.newWebSocket(request, listener)
    }

    fun disconnect() {
        socket?.close(1000, "AURA stopping")
        socket = null
    }

    fun isConfigured(): Boolean =
        !prefs.getString(ENDPOINT, null).isNullOrBlank() && !prefs.getString(TOKEN, null).isNullOrBlank()

    fun sendEvent(event: AuraEvent) {
        socket?.send(event.toJson().toString())
    }

    private fun JSONObject.toAuraEvent(): AuraEvent? {
        if (optString("type") != AuraEventTypes.TASK) return null
        val id = optString("id").trim()
        val task = optString("task").trim()
        if (id.isEmpty() || task.isEmpty()) return null
        return AuraEvent(
            id = id,
            type = AuraEventTypes.TASK,
            source = optString("source", "bridge"),
            priority = optInt("priority", 0),
            payload = JSONObject().put("task", task),
        )
    }

    private fun AuraEvent.toJson(): JSONObject = JSONObject()
        .put("type", type)
        .put("id", id)
        .put("source", source)
        .put("timestampMs", timestampMs)
        .put("priority", priority)
        .put("payload", payload)

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            val hello = JSONObject()
                .put("type", AuraEventTypes.HELLO)
                .put("protocol", 1)
                .put("deviceId", deviceId)
                .put("capabilities", JSONObject().put("task", true).put("event", true))
            webSocket.send(hello.toString())
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            runCatching {
                val message = JSONObject(text)
                when (message.optString("type")) {
                    AuraEventTypes.TASK -> core.publish(message.toAuraEvent() ?: return)
                    else -> Unit
                }
            }.onFailure { Log.w(TAG, "Ignored malformed bridge message") }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "Bridge connection failed: ${t.javaClass.simpleName}")
        }
    }
}
