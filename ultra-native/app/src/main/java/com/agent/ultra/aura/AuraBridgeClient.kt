package com.agent.ultra.aura

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.min

/**
 * Secure outbound AURA bridge transport.
 *
 * The phone never opens a listening remote-control socket. AURA connects out to
 * a trusted relay using WSS and a per-device bearer token. Durable device events
 * are queued locally until the relay acknowledges them.
 */
class AuraBridgeClient(
    private val context: Context,
    private val core: AuraCore,
) {
    companion object {
        private const val PREFS = "aura_bridge"
        private const val ENDPOINT = "endpoint"
        private const val TOKEN = "token"
        private const val DEVICE_ID = "device_id"
        private const val TAG = "AURABridge"
        private const val MIN_RECONNECT_MS = 1_000L
        private const val MAX_RECONNECT_MS = 60_000L
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val queue = AuraEventQueue(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(30, TimeUnit.SECONDS)
        .build()

    private var socket: WebSocket? = null
    private var reconnectAttempt = 0
    @Volatile private var reconnectEnabled = false

    private val deviceId: String = prefs.getString(DEVICE_ID, null)
        ?: UUID.randomUUID().toString().also { id ->
            prefs.edit().putString(DEVICE_ID, id).apply()
        }

    fun configure(endpoint: String, token: String) {
        require(endpoint.startsWith("wss://")) { "AURA Bridge endpoint must use wss://" }
        require(token.length >= 24) { "AURA Bridge token is too short" }
        prefs.edit()
            .putString(ENDPOINT, endpoint.trim())
            .putString(TOKEN, token)
            .apply()
        reconnectAttempt = 0
        reconnectEnabled = true
        connect()
    }

    fun connect() {
        reconnectEnabled = true
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
            .header("X-AURA-Device", deviceId)
            .header("X-AURA-Protocol", AuraBridgeProtocol.VERSION.toString())
            .build()
        socket = client.newWebSocket(request, listener)
    }

    fun disconnect() {
        reconnectEnabled = false
        reconnectAttempt = 0
        socket?.close(1000, "AURA stopping")
        socket = null
    }

    fun isConfigured(): Boolean =
        !prefs.getString(ENDPOINT, null).isNullOrBlank() &&
            !prefs.getString(TOKEN, null).isNullOrBlank()

    fun isConnected(): Boolean = socket != null

    /** Sends immediately when connected; otherwise persists the event for the next session. */
    fun sendEvent(event: AuraEvent): Boolean {
        val sent = socket?.send(event.toWireJson()) == true
        if (!sent) queue.enqueue(event)
        return sent
    }

    fun sendStatus(): Boolean {
        val status = JSONObject()
            .put("type", AuraBridgeProtocol.TYPE_STATUS)
            .put("protocol", AuraBridgeProtocol.VERSION)
            .put("deviceId", deviceId)
            .put("configured", isConfigured())
            .put("connected", isConnected())
            .put("capabilities", JSONObject()
                .put(AuraBridgeProtocol.CAPABILITY_TASK, true)
                .put(AuraBridgeProtocol.CAPABILITY_EVENT, true)
                .put(AuraBridgeProtocol.CAPABILITY_STATUS, true))
        return socket?.send(status.toString()) == true
    }

    private fun flushQueue(webSocket: WebSocket) {
        for (event in queue.pending()) {
            if (!webSocket.send(event.toString())) break
        }
    }

    private fun scheduleReconnect() {
        if (!reconnectEnabled || !isConfigured()) return
        val delayMs = min(MAX_RECONNECT_MS, MIN_RECONNECT_MS shl min(reconnectAttempt, 6))
        reconnectAttempt++
        scope.launch {
            delay(delayMs)
            if (reconnectEnabled) connect()
        }
    }

    private fun sendPong(id: String?) {
        val pong = JSONObject()
            .put("type", AuraBridgeProtocol.TYPE_PONG)
            .put("protocol", AuraBridgeProtocol.VERSION)
        if (!id.isNullOrBlank()) pong.put("id", id)
        socket?.send(pong.toString())
    }

    private fun sendHello(webSocket: WebSocket) {
        val hello = JSONObject()
            .put("type", AuraBridgeProtocol.TYPE_HELLO)
            .put("protocol", AuraBridgeProtocol.VERSION)
            .put("deviceId", deviceId)
            .put("capabilities", JSONObject()
                .put(AuraBridgeProtocol.CAPABILITY_TASK, true)
                .put(AuraBridgeProtocol.CAPABILITY_EVENT, true)
                .put(AuraBridgeProtocol.CAPABILITY_STATUS, true))
        webSocket.send(hello.toString())
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            reconnectAttempt = 0
            sendHello(webSocket)
            sendStatus()
            flushQueue(webSocket)
            Log.i(TAG, "Bridge connected")
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            runCatching {
                val message = JSONObject(text)
                when (message.optString("type")) {
                    AuraBridgeProtocol.TYPE_TASK,
                    AuraBridgeProtocol.TYPE_EVENT -> message.toAuraEvent()?.let(core::publish)
                    AuraBridgeProtocol.TYPE_ACK -> queue.ack(message.optString("id").trim())
                    AuraBridgeProtocol.TYPE_PING -> sendPong(message.optString("id", null))
                    AuraBridgeProtocol.TYPE_STATUS_REQUEST -> sendStatus()
                    else -> Unit
                }
            }.onFailure { Log.w(TAG, "Ignored malformed bridge message") }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, reason) }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            socket = null
            Log.i(TAG, "Bridge closed: $code")
            scheduleReconnect()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            socket = null
            Log.w(TAG, "Bridge connection failed")
            scheduleReconnect()
        }
    }

    private fun JSONObject.toAuraEvent(): AuraEvent? {
        val type = optString("type")
        val id = optString("id").trim()
        if (id.isEmpty()) return null
        if (type != AuraBridgeProtocol.TYPE_TASK && type != AuraBridgeProtocol.TYPE_EVENT) return null
        val task = optString("task").trim()
        if (type == AuraBridgeProtocol.TYPE_TASK && task.isEmpty()) return null
        return AuraEvent(
            id = id,
            type = type,
            source = optString("source", "bridge"),
            priority = optInt("priority", 0),
            payload = JSONObject().apply {
                if (task.isNotEmpty()) put("task", task)
                if (has("payload")) put("payload", opt("payload"))
            },
        )
    }

    private fun AuraEvent.toWireJson(): String = JSONObject()
        .put("type", type)
        .put("id", id)
        .put("source", source)
        .put("timestampMs", timestampMs)
        .put("priority", priority)
        .put("payload", JSONObject(payload.toString()))
        .apply { if (type == AuraBridgeProtocol.TYPE_TASK) put("task", payload.optString("task").trim()) }
        .toString()
}
