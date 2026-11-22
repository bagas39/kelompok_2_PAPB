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
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class SupabaseRealtimeClient internal constructor(
    private val config: SupabaseConfig = SupabaseConfig,
    private val autoConnect: Boolean = true,
    private val frameSenderOverride: ((String) -> Boolean)? = null
) {
    constructor(config: SupabaseConfig = SupabaseConfig) : this(
        config = config,
        autoConnect = true,
        frameSenderOverride = null
    )

    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refCounter = AtomicLong(0)

    @Volatile
    private var webSocket: WebSocket? = null

    @Volatile
    private var isConnected = false

    private val topicLock = Any()
    private val topicRefCounts = ConcurrentHashMap<String, Int>()
    private val pendingBroadcasts = ConcurrentLinkedQueue<String>()
    private val pendingEphemeralBroadcasts = ConcurrentHashMap<String, ConcurrentLinkedQueue<String>>()
    private val ephemeralJoiningTopics = ConcurrentHashMap.newKeySet<String>()

    private val _incomingMessages = MutableSharedFlow<Pair<String, JSONObject>>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<Pair<String, JSONObject>> = _incomingMessages.asSharedFlow()

    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null

    internal fun setConnectedForTest(connected: Boolean) {
        isConnected = connected
    }

    internal fun activeTopicCount(topic: String): Int = synchronized(topicLock) {
        topicRefCounts[topic] ?: 0
    }

    fun connect() {
        if (!autoConnect) return
        if (isConnected || webSocket != null) return
        val wsUrl = "${config.realtimeWsUrl}/websocket?apikey=${config.apiKey}&vsn=1.0.0"
        val request = Request.Builder().url(wsUrl).build()

        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                startHeartbeat()
                val topicsToJoin = synchronized(topicLock) {
                    (topicRefCounts.keys + pendingEphemeralBroadcasts.keys).toSet()
                }
                topicsToJoin.forEach { topic ->
                    val isReferenced = synchronized(topicLock) { (topicRefCounts[topic] ?: 0) > 0 }
                    if (!isReferenced) {
                        ephemeralJoiningTopics.add(topic)
                    }
                    sendJoinTopic(topic)
                }
                while (true) {
                    val queued = pendingBroadcasts.poll() ?: break
                    sendFrame(queued)
                }
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                handleDisconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                handleDisconnect()
            }
        })
    }

    internal fun handleIncomingMessage(text: String) {
        try {
            val json = JSONObject(text)
            val topic = json.optString("topic")
            val event = json.optString("event")
            when (event) {
                "broadcast" -> {
                    val payloadWrapper = json.optJSONObject("payload")
                    val broadcastPayload = payloadWrapper?.optJSONObject("payload")
                    if (broadcastPayload != null) {
                        _incomingMessages.tryEmit(topic to broadcastPayload)
                    }
                }
                "phx_reply" -> {
                    ephemeralJoiningTopics.remove(topic)
                    val queue = pendingEphemeralBroadcasts.remove(topic)
                    if (queue != null && queue.isNotEmpty()) {
                        while (true) {
                            val queuedMsg = queue.poll() ?: break
                            if (isConnected) {
                                sendFrame(queuedMsg)
                            } else {
                                pendingBroadcasts.offer(queuedMsg)
                            }
                        }
                        scope.launch {
                            delay(500)
                            leaveIfUnreferenced(topic)
                        }
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun handleDisconnect() {
        isConnected = false
        webSocket = null
        ephemeralJoiningTopics.clear()
        stopHeartbeat()
        scheduleReconnect()
    }

    private fun scheduleReconnect() {
        if (!autoConnect || reconnectJob?.isActive == true) return
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

    private fun sendFrame(payload: String): Boolean {
        frameSenderOverride?.let { return it.invoke(payload) }
        return webSocket?.send(payload) == true
    }

    private fun sendHeartbeat() {
        val ref = refCounter.incrementAndGet().toString()
        val payload = JSONObject().apply {
            put("topic", "phoenix")
            put("event", "heartbeat")
            put("payload", JSONObject())
            put("ref", ref)
        }
        sendFrame(payload.toString())
    }

    fun subscribeTopic(topic: String) {
        var shouldJoin = false
        synchronized(topicLock) {
            val prev = topicRefCounts[topic] ?: 0
            topicRefCounts[topic] = prev + 1
            if (prev == 0) {
                shouldJoin = true
            }
        }
        if (isConnected) {
            if (shouldJoin) {
                sendJoinTopic(topic)
            }
        } else {
            connect()
        }
    }

    fun unsubscribeTopic(topic: String) {
        var shouldLeave = false
        synchronized(topicLock) {
            val prev = topicRefCounts[topic] ?: 0
            if (prev > 1) {
                topicRefCounts[topic] = prev - 1
            } else if (prev == 1) {
                topicRefCounts.remove(topic)
                if (pendingEphemeralBroadcasts[topic].isNullOrEmpty()) {
                    shouldLeave = true
                }
            }
        }
        if (shouldLeave) {
            sendLeaveTopic(topic)
        }
    }

    private fun leaveIfUnreferenced(topic: String) {
        val shouldLeave = synchronized(topicLock) {
            (topicRefCounts[topic] ?: 0) == 0 && pendingEphemeralBroadcasts[topic].isNullOrEmpty()
        }
        if (shouldLeave && isConnected) {
            sendLeaveTopic(topic)
        }
    }

    private fun sendLeaveTopic(topic: String) {
        val ref = refCounter.incrementAndGet().toString()
        val payload = JSONObject().apply {
            put("topic", topic)
            put("event", "phx_leave")
            put("payload", JSONObject())
            put("ref", ref)
        }
        sendFrame(payload.toString())
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
        sendFrame(msg.toString())
    }

    fun broadcast(topic: String, eventName: String, data: JSONObject) {
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
        }.toString()

        val isSubscribed = synchronized(topicLock) { (topicRefCounts[topic] ?: 0) > 0 }
        if (isSubscribed) {
            if (isConnected && sendFrame(msg)) {
                return
            }
            pendingBroadcasts.offer(msg)
            connect()
            return
        }

        val queue = pendingEphemeralBroadcasts.computeIfAbsent(topic) { ConcurrentLinkedQueue() }
        queue.offer(msg)
        if (isConnected) {
            if (ephemeralJoiningTopics.add(topic)) {
                sendJoinTopic(topic)
                scope.launch {
                    delay(3000)
                    ephemeralJoiningTopics.remove(topic)
                    pendingEphemeralBroadcasts.remove(topic)
                    leaveIfUnreferenced(topic)
                }
            }
        } else {
            connect()
        }
    }
}
