package com.example.cloble.data.remote

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class SupabaseRealtimeClient(
    private val config: SupabaseConfig = SupabaseConfig
) {
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refCounter = AtomicLong(0)

    @Volatile
    private var webSocket: WebSocket? = null

    @Volatile
    private var isConnected = false

    private val activeTopics = ConcurrentHashMap.newKeySet<String>()

    private val _incomingMessages = MutableSharedFlow<Pair<String, JSONObject>>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<Pair<String, JSONObject>> = _incomingMessages.asSharedFlow()

    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null

    fun connect() {
        if (isConnected || webSocket != null) return
        val wsUrl = "${config.realtimeWsUrl}/websocket?apikey=${config.apiKey}&vsn=1.0.0"
        val request = Request.Builder().url(wsUrl).build()

        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                startHeartbeat()
                activeTopics.forEach { topic ->
                    sendJoinTopic(topic)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                try {
                    val json = JSONObject(text)
                    val topic = json.optString("topic")
                    val event = json.optString("event")
                    if (event == "broadcast") {
                        val payloadWrapper = json.optJSONObject("payload")
                        val broadcastPayload = payloadWrapper?.optJSONObject("payload")
                        if (broadcastPayload != null) {
                            _incomingMessages.tryEmit(topic to broadcastPayload)
                        }
                    }
                } catch (_: Exception) {
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                handleDisconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                handleDisconnect()
            }
        })
    }

    private fun handleDisconnect() {
        isConnected = false
        webSocket = null
        stopHeartbeat()
        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        if (reconnectJob?.isActive == true) return
        reconnectJob = scope.launch {
            delay(3000)
            connect()
        }
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && isConnected) {
                delay(25000)
                sendHeartbeat()
            }
        }
    }

    private fun stopHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = null
    }

    private fun sendHeartbeat() {
        val ref = refCounter.incrementAndGet().toString()
        val payload = JSONObject().apply {
            put("topic", "phoenix")
            put("event", "heartbeat")
            put("payload", JSONObject())
            put("ref", ref)
        }
        webSocket?.send(payload.toString())
    }

    fun subscribeTopic(topic: String) {
        activeTopics.add(topic)
        if (isConnected) {
            sendJoinTopic(topic)
        } else {
            connect()
        }
    }

    fun unsubscribeTopic(topic: String) {
        activeTopics.remove(topic)
        val ref = refCounter.incrementAndGet().toString()
        val payload = JSONObject().apply {
            put("topic", topic)
            put("event", "phx_leave")
            put("payload", JSONObject())
            put("ref", ref)
        }
        webSocket?.send(payload.toString())
    }

    private fun sendJoinTopic(topic: String) {
        val ref = refCounter.incrementAndGet().toString()
        val configObj = JSONObject().apply {
            put("broadcast", JSONObject().apply {
                put("ack", true)
                put("self", true)
            })
        }
        val payloadObj = JSONObject().apply {
            put("config", configObj)
        }
        val msg = JSONObject().apply {
            put("topic", topic)
            put("event", "phx_join")
            put("payload", payloadObj)
            put("ref", ref)
        }
        webSocket?.send(msg.toString())
    }

    fun broadcast(topic: String, eventName: String, data: JSONObject) {
        if (!isConnected) {
            connect()
        }
        val ref = refCounter.incrementAndGet().toString()
        val innerPayload = JSONObject().apply {
            put("type", "broadcast")
            put("event", eventName)
            put("payload", data)
        }
        val msg = JSONObject().apply {
            put("topic", topic)
            put("event", "broadcast")
            put("payload", innerPayload)
            put("ref", ref)
        }
        webSocket?.send(msg.toString())
    }
}
