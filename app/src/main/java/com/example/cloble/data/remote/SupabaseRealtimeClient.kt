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
    private val ephemeralJoinRefs = ConcurrentHashMap<String, String>()
    private val ephemeralTimeoutJobs = ConcurrentHashMap<String, Job>()

    private val _incomingMessages = MutableSharedFlow<Pair<String, JSONObject>>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<Pair<String, JSONObject>> = _incomingMessages.asSharedFlow()

    private var heartbeatJob: Job? = null
    private var reconnectJob: Job? = null
    private var reconnectAttempt = 0

    internal fun setConnectedForTest(connected: Boolean) {
        synchronized(topicLock) {
            isConnected = connected
        }
    }

    internal fun activeTopicCount(topic: String): Int = synchronized(topicLock) {
        topicRefCounts[topic] ?: 0
    }

    internal fun hasPendingReconnectForTest(): Boolean = synchronized(topicLock) {
        reconnectJob?.isActive == true
    }

    internal fun pendingBroadcastCountForTest(): Int = pendingBroadcasts.size

    internal fun pendingEphemeralCountForTest(topic: String): Int =
        pendingEphemeralBroadcasts[topic]?.size ?: 0

    fun connect() {
        synchronized(topicLock) {
            if (!autoConnect) return
            if (isConnected || webSocket != null) return
            val wsUrl = "${config.realtimeWsUrl}/websocket?apikey=${config.apiKey}&vsn=1.0.0"
            val request = Request.Builder().url(wsUrl).build()

            webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    var referencedTopics: Set<String> = emptySet()
                    val ephemeralJoins = mutableListOf<Pair<String, String>>()
                    val drainedBroadcasts = mutableListOf<String>()

                    synchronized(topicLock) {
                        if (webSocket !== this@SupabaseRealtimeClient.webSocket) {
                            webSocket.close(1000, "stale")
                            return
                        }
                        isConnected = true
                        reconnectAttempt = 0
                        reconnectJob?.cancel()
                        reconnectJob = null
                        startHeartbeat()

                        referencedTopics = topicRefCounts.keys.toSet()
                        val staleTopics = pendingEphemeralBroadcasts.entries
                            .filter { it.value.isEmpty() }
                            .map { it.key }
                        staleTopics.forEach { pendingEphemeralBroadcasts.remove(it) }

                        pendingEphemeralBroadcasts.keys
                            .filter { !referencedTopics.contains(it) && !pendingEphemeralBroadcasts[it].isNullOrEmpty() }
                            .forEach { topic ->
                                val joinRef = refCounter.incrementAndGet().toString()
                                ephemeralJoinRefs[topic] = joinRef
                                scheduleEphemeralTimeoutLocked(topic, joinRef)
                                ephemeralJoins.add(topic to joinRef)
                            }

                        while (true) {
                            val queued = pendingBroadcasts.poll() ?: break
                            drainedBroadcasts.add(queued)
                        }
                    }

                    referencedTopics.forEach { topic ->
                        sendJoinTopicWithRef(topic, refCounter.incrementAndGet().toString())
                    }
                    ephemeralJoins.forEach { (topic, joinRef) ->
                        sendJoinTopicWithRef(topic, joinRef)
                    }
                    drainedBroadcasts.forEach { queued ->
                        if (!sendFrame(queued)) {
                            offerBounded(pendingBroadcasts, queued, MAX_PENDING_BROADCASTS)
                        }
                    }
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val isCurrentSocket = synchronized(topicLock) {
                        webSocket === this@SupabaseRealtimeClient.webSocket
                    }
                    if (isCurrentSocket) {
                        handleIncomingMessage(text)
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    handleDisconnect(webSocket)
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    handleDisconnect(webSocket)
                }
            })
        }
    }

    fun disconnect() {
        val socketToClose: WebSocket?
        synchronized(topicLock) {
            reconnectJob?.cancel()
            reconnectJob = null
            reconnectAttempt = 0
            stopHeartbeat()
            ephemeralTimeoutJobs.values.forEach { it.cancel() }
            ephemeralTimeoutJobs.clear()
            ephemeralJoinRefs.clear()
            pendingBroadcasts.clear()
            pendingEphemeralBroadcasts.clear()
            isConnected = false
            socketToClose = webSocket
            webSocket = null
        }
        socketToClose?.close(1000, "disconnect")
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
                    val replyRef = if (json.isNull("ref")) {
                        ""
                    } else {
                        json.optString("ref", "").trim()
                    }
                    val status = json.optJSONObject("payload")?.optString("status")

                    val queue: ConcurrentLinkedQueue<String>?
                    synchronized(topicLock) {
                        val expectedRef = ephemeralJoinRefs[topic] ?: return
                        if (replyRef.isNotBlank() && replyRef != expectedRef) {
                            return
                        }
                        ephemeralJoinRefs.remove(topic)
                        ephemeralTimeoutJobs.remove(topic)?.cancel()
                        queue = pendingEphemeralBroadcasts.remove(topic)
                    }

                    if (status == "ok") {
                        if (queue != null && queue.isNotEmpty()) {
                            while (true) {
                                val queuedMsg = queue.poll() ?: break
                                if (isConnected && sendFrame(queuedMsg)) {
                                    continue
                                }
                                offerBounded(pendingBroadcasts, queuedMsg, MAX_PENDING_BROADCASTS)
                            }
                        }
                        scope.launch {
                            delay(EPHEMERAL_LEAVE_DELAY_MS)
                            leaveIfUnreferenced(topic)
                        }
                    } else {
                        leaveIfUnreferenced(topic)
                    }
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun hasActiveDemandLocked(): Boolean =
        topicRefCounts.isNotEmpty() ||
            pendingBroadcasts.isNotEmpty() ||
            pendingEphemeralBroadcasts.values.any { it.isNotEmpty() }

    private fun handleDisconnect(socket: WebSocket) {
        synchronized(topicLock) {
            if (socket !== this@SupabaseRealtimeClient.webSocket) return
            isConnected = false
            webSocket = null
            ephemeralJoinRefs.clear()
            ephemeralTimeoutJobs.values.forEach { it.cancel() }
            ephemeralTimeoutJobs.clear()
            stopHeartbeat()
            if (hasActiveDemandLocked()) {
                scheduleReconnectLocked()
            }
        }
    }

    private fun scheduleReconnectLocked() {
        if (!autoConnect || reconnectJob?.isActive == true || !hasActiveDemandLocked()) return
        val delayMs = (BASE_RECONNECT_DELAY_MS * (1L shl reconnectAttempt.coerceAtMost(4)))
            .coerceAtMost(MAX_RECONNECT_DELAY_MS)
        reconnectAttempt += 1
        reconnectJob = scope.launch {
            delay(delayMs)
            val shouldConnect = synchronized(topicLock) {
                reconnectJob = null
                hasActiveDemandLocked() && !isConnected && webSocket == null
            }
            if (shouldConnect) {
                connect()
            }
        }
    }

    private fun closeIfIdle() {
        if (!autoConnect) return
        val socketToClose: WebSocket?
        synchronized(topicLock) {
            if (hasActiveDemandLocked() || ephemeralJoinRefs.isNotEmpty()) return
            reconnectJob?.cancel()
            reconnectJob = null
            reconnectAttempt = 0
            stopHeartbeat()
            isConnected = false
            socketToClose = webSocket
            webSocket = null
        }
        socketToClose?.close(1000, "idle")
    }

    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive && isConnected) {
                delay(HEARTBEAT_INTERVAL_MS)
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

    private fun offerBounded(queue: ConcurrentLinkedQueue<String>, item: String, maxSize: Int) {
        while (queue.size >= maxSize) {
            if (queue.poll() == null) break
        }
        queue.offer(item)
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
        val shouldJoinNow: Boolean
        val shouldConnect: Boolean
        synchronized(topicLock) {
            val prev = topicRefCounts[topic] ?: 0
            topicRefCounts[topic] = prev + 1
            if (prev == 0) {
                ephemeralTimeoutJobs.remove(topic)?.cancel()
                ephemeralJoinRefs.remove(topic)
            }
            shouldJoinNow = prev == 0 && isConnected
            shouldConnect = !isConnected
        }
        if (shouldJoinNow) {
            sendJoinTopicWithRef(topic, refCounter.incrementAndGet().toString())
        } else if (shouldConnect) {
            connect()
        }
    }

    fun unsubscribeTopic(topic: String) {
        val shouldLeave: Boolean
        synchronized(topicLock) {
            val prev = topicRefCounts[topic] ?: 0
            if (prev > 1) {
                topicRefCounts[topic] = prev - 1
                shouldLeave = false
            } else if (prev == 1) {
                topicRefCounts.remove(topic)
                shouldLeave = isConnected && pendingEphemeralBroadcasts[topic].isNullOrEmpty()
            } else {
                shouldLeave = false
            }
        }
        if (shouldLeave) {
            sendLeaveTopic(topic)
        }
        closeIfIdle()
    }

    private fun leaveIfUnreferenced(topic: String) {
        val shouldLeave = synchronized(topicLock) {
            isConnected &&
                (topicRefCounts[topic] ?: 0) == 0 &&
                pendingEphemeralBroadcasts[topic].isNullOrEmpty() &&
                !ephemeralJoinRefs.containsKey(topic)
        }
        if (shouldLeave) {
            sendLeaveTopic(topic)
        }
        closeIfIdle()
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

    private fun sendJoinTopicWithRef(topic: String, ref: String) {
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

    private fun scheduleEphemeralTimeoutLocked(topic: String, joinRef: String) {
        ephemeralTimeoutJobs.remove(topic)?.cancel()
        ephemeralTimeoutJobs[topic] = scope.launch {
            delay(EPHEMERAL_JOIN_TIMEOUT_MS)
            val timedOut = synchronized(topicLock) {
                if (ephemeralJoinRefs[topic] != joinRef) {
                    false
                } else {
                    ephemeralJoinRefs.remove(topic)
                    ephemeralTimeoutJobs.remove(topic)
                    pendingEphemeralBroadcasts.remove(topic)
                    true
                }
            }
            if (timedOut) {
                leaveIfUnreferenced(topic)
            }
        }
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

        var sendImmediatelySubscribed = false
        var ephemeralJoinRefToSend: String? = null
        var shouldConnect = false

        synchronized(topicLock) {
            if ((topicRefCounts[topic] ?: 0) > 0) {
                if (isConnected) {
                    sendImmediatelySubscribed = true
                } else {
                    offerBounded(pendingBroadcasts, msg, MAX_PENDING_BROADCASTS)
                    shouldConnect = true
                }
            } else {
                if (!pendingEphemeralBroadcasts.containsKey(topic) &&
                    pendingEphemeralBroadcasts.size >= MAX_EPHEMERAL_TOPICS
                ) {
                    val evictTopic = pendingEphemeralBroadcasts.keys.firstOrNull {
                        it != topic && (topicRefCounts[it] ?: 0) == 0
                    }
                    if (evictTopic != null) {
                        pendingEphemeralBroadcasts.remove(evictTopic)
                        ephemeralJoinRefs.remove(evictTopic)
                        ephemeralTimeoutJobs.remove(evictTopic)?.cancel()
                    }
                }
                val queue = pendingEphemeralBroadcasts.computeIfAbsent(topic) { ConcurrentLinkedQueue() }
                offerBounded(queue, msg, MAX_EPHEMERAL_PER_TOPIC)
                if (isConnected) {
                    val existingRef = ephemeralJoinRefs[topic]
                    if (existingRef == null) {
                        val newJoinRef = refCounter.incrementAndGet().toString()
                        ephemeralJoinRefs[topic] = newJoinRef
                        scheduleEphemeralTimeoutLocked(topic, newJoinRef)
                        ephemeralJoinRefToSend = newJoinRef
                    } else {
                        scheduleEphemeralTimeoutLocked(topic, existingRef)
                    }
                } else {
                    shouldConnect = true
                }
            }
        }

        if (sendImmediatelySubscribed) {
            if (!sendFrame(msg)) {
                offerBounded(pendingBroadcasts, msg, MAX_PENDING_BROADCASTS)
                connect()
            }
            return
        }

        if (ephemeralJoinRefToSend != null) {
            sendJoinTopicWithRef(topic, ephemeralJoinRefToSend)
        } else if (shouldConnect) {
            connect()
        }
    }

    private companion object {
        const val MAX_PENDING_BROADCASTS = 64
        const val MAX_EPHEMERAL_PER_TOPIC = 32
        const val MAX_EPHEMERAL_TOPICS = 32
        const val BASE_RECONNECT_DELAY_MS = 3_000L
        const val MAX_RECONNECT_DELAY_MS = 30_000L
        const val EPHEMERAL_JOIN_TIMEOUT_MS = 3_000L
        const val EPHEMERAL_LEAVE_DELAY_MS = 500L
        const val HEARTBEAT_INTERVAL_MS = 25_000L
    }
}
