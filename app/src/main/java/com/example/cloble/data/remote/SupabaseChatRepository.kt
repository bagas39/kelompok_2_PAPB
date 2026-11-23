package com.example.cloble.data.remote

import com.example.cloble.data.local.dao.OfflineChatCacheDao
import com.example.cloble.data.local.entity.CachedChatThreadEntity
import com.example.cloble.data.repository.OfflineChatCacheMappers
import com.example.cloble.domain.model.ChatMessage
import com.example.cloble.domain.model.ChatThreadSummary
import com.example.cloble.domain.model.MatchStatus
import com.example.cloble.domain.repository.ChatRepository
import com.example.cloble.domain.result.DbFailure
import com.example.cloble.domain.result.SendMessageResult
import com.example.cloble.domain.util.ChatThreadDeduplicator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import org.json.JSONObject

class SupabaseChatRepository(
    private val client: SupabaseClient,
    private val realtimeClient: SupabaseRealtimeClient,
    private val invalidator: SupabaseDataInvalidator,
    private val cacheDao: OfflineChatCacheDao? = null,
    private val activeSessionTokenProvider: () -> String? = { client.getSessionToken() }
) : ChatRepository {

    private fun isSessionTokenStillValid(requestToken: String?): Boolean {
        if (requestToken.isNullOrBlank()) return false
        return activeSessionTokenProvider() == requestToken
    }

    private suspend fun fetchUserThreadsOrNull(
        partnerUserIdFilter: Long? = null
    ): List<ChatThreadSummary>? {
        return try {
            val params = JSONObject().apply {
                if (partnerUserIdFilter != null) {
                    put("p_partner_user_id", partnerUserIdFilter)
                }
            }
            val resp = client.rpc("rpc_get_chat_threads", params)
            if (resp.optString("code") != "SUCCESS") return null
            val arr = resp.optJSONArray("threads") ?: return emptyList()
            val list = mutableListOf<ChatThreadSummary>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val matchStatus = try {
                    MatchStatus.valueOf(obj.optString("status", "ACTIVE"))
                } catch (_: Exception) {
                    MatchStatus.ACTIVE
                }
                val lastMessageText = obj.optString("lastMessageText")
                    .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                    ?: "Match baru terhubung"
                val hasLastSentAt = !obj.isNull("lastMessageSentAt") && obj.optLong("lastMessageSentAt", 0L) > 0L
                val updatedAt = obj.optLong("updatedAt", 0L)
                val lastSentAt = if (hasLastSentAt) obj.optLong("lastMessageSentAt") else updatedAt
                val avatarUri = obj.optString("partnerAvatarUri")
                    .takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }

                list.add(
                    ChatThreadSummary(
                        matchId = obj.optLong("matchId"),
                        partnerUserId = obj.optLong("partnerUserId"),
                        partnerName = obj.optString("partnerName", "Pengguna").ifBlank { "Pengguna" },
                        partnerCity = obj.optString("partnerCity", ""),
                        partnerAvatarUri = avatarUri,
                        partnerAverageRating = obj.optDouble("partnerAverageRating", 0.0),
                        myItemName = obj.optString("myItemName", "Pakaian").ifBlank { "Pakaian" },
                        partnerItemName = obj.optString("partnerItemName", "Pakaian").ifBlank { "Pakaian" },
                        status = matchStatus,
                        lastMessageText = lastMessageText,
                        lastMessageSentAt = lastSentAt,
                        unreadCount = obj.optInt("unreadCount", 0),
                        updatedAt = updatedAt,
                        totalMatchesWithPartner = obj.optInt("totalMatchesWithPartner", 1).coerceAtLeast(1)
                    )
                )
            }
            list
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            null
        }
    }

    private fun sortPartnerMatches(threads: List<ChatThreadSummary>): List<ChatThreadSummary> {
        return threads.sortedWith(
            compareBy<ChatThreadSummary> {
                when (it.status) {
                    MatchStatus.ACTIVE -> 0
                    MatchStatus.COMPLETED -> 1
                    MatchStatus.CANCELLED -> 2
                }
            }.thenByDescending { it.updatedAt }.thenByDescending { it.matchId }
        )
    }

    override fun observeChatThreads(userId: Long): Flow<List<ChatThreadSummary>> =
        observeThreadsFlow(
            userId = userId,
            partnerUserIdFilter = null,
            cacheFlow = cacheDao?.observeThreads(userId),
            transform = { ChatThreadDeduplicator.deduplicateByPartner(it) }
        )

    override fun observePartnerMatches(
        userId: Long,
        partnerUserId: Long
    ): Flow<List<ChatThreadSummary>> =
        observeThreadsFlow(
            userId = userId,
            partnerUserIdFilter = partnerUserId,
            cacheFlow = cacheDao?.observePartnerThreads(userId, partnerUserId),
            transform = ::sortPartnerMatches
        )

    private fun observeThreadsFlow(
        userId: Long,
        partnerUserIdFilter: Long?,
        cacheFlow: Flow<List<CachedChatThreadEntity>>?,
        transform: (List<ChatThreadSummary>) -> List<ChatThreadSummary>
    ): Flow<List<ChatThreadSummary>> = channelFlow {
        val userTopic = "realtime:user_threads_$userId"
        realtimeClient.subscribeTopic(userTopic)

        var hasEmitted = false
        val refreshTrigger = Channel<Unit>(Channel.CONFLATED)

        val cacheJob = if (cacheDao != null && cacheFlow != null) {
            launch {
                cacheFlow.collect { entities ->
                    hasEmitted = true
                    send(transform(entities.map { OfflineChatCacheMappers.toDomain(it) }))
                }
            }
        } else {
            null
        }

        val syncJob = launch {
            for (unit in refreshTrigger) {
                val requestToken = activeSessionTokenProvider()
                val fetched = fetchUserThreadsOrNull(partnerUserIdFilter)
                if (fetched != null) {
                    if (cacheDao != null) {
                        if (!isSessionTokenStillValid(requestToken)) {
                            continue
                        }
                        val entities = fetched.map { OfflineChatCacheMappers.toEntity(userId, it) }
                        if (partnerUserIdFilter == null) {
                            cacheDao.replaceThreadsForUser(userId, entities)
                        } else {
                            cacheDao.replacePartnerThreadsForUser(userId, partnerUserIdFilter, entities)
                        }
                        fetched.forEach { thread ->
                            cacheDao.updateMatchDetailStatus(
                                viewerUserId = userId,
                                matchId = thread.matchId,
                                status = thread.status,
                                meetingLocation = null,
                                updatedAt = thread.updatedAt
                            )
                        }
                    } else {
                        hasEmitted = true
                        send(transform(fetched))
                    }
                } else if (cacheDao == null && !hasEmitted) {
                    hasEmitted = true
                    send(emptyList())
                }
            }
        }

        refreshTrigger.trySend(Unit)

        val realtimeJob = launch {
            realtimeClient.incomingMessages.collect { (incomingTopic, _) ->
                if (incomingTopic == userTopic || incomingTopic.startsWith("realtime:match_")) {
                    refreshTrigger.trySend(Unit)
                }
            }
        }

        val invalidatorJob = launch {
            invalidator.discoveryTrigger.collect {
                refreshTrigger.trySend(Unit)
            }
        }

        val chatInvalidatorJob = launch {
            invalidator.chatTrigger.collect {
                refreshTrigger.trySend(Unit)
            }
        }

        val userInvalidatorJob = launch {
            invalidator.userTrigger.collect {
                refreshTrigger.trySend(Unit)
            }
        }

        awaitClose {
            refreshTrigger.close()
            cacheJob?.cancel()
            syncJob.cancel()
            realtimeJob.cancel()
            invalidatorJob.cancel()
            chatInvalidatorJob.cancel()
            userInvalidatorJob.cancel()
            realtimeClient.unsubscribeTopic(userTopic)
        }
    }.flowOn(Dispatchers.IO)

    override fun observeMessages(
        matchId: Long,
        viewerUserId: Long
    ): Flow<List<ChatMessage>> = channelFlow {
        val topic = "realtime:match_$matchId"
        realtimeClient.subscribeTopic(topic)

        val currentMessages = mutableListOf<ChatMessage>()
        var hasEmitted = false
        val refreshTrigger = Channel<Unit>(Channel.CONFLATED)

        val cacheJob = if (cacheDao != null) {
            launch {
                cacheDao.observeMessages(viewerUserId, matchId).collect { entities ->
                    hasEmitted = true
                    send(entities.map { OfflineChatCacheMappers.toDomain(it, viewerUserId) })
                }
            }
        } else {
            null
        }

        suspend fun reloadMessagesFromServer() {
            try {
                val requestToken = activeSessionTokenProvider()
                val resp = client.rpc(
                    "rpc_get_chat_messages",
                    JSONObject().put("p_match_id", matchId)
                )
                if (resp.optString("code") != "SUCCESS") return
                val arr = resp.optJSONArray("messages") ?: return
                val fetched = mutableListOf<ChatMessage>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val senderObj = obj.optJSONObject("users")
                    val senderName = senderObj?.optString("name", "Pengguna") ?: "Pengguna"
                    fetched.add(SupabaseMappers.parseChatMessage(obj, viewerUserId, senderName))
                }
                if (cacheDao != null) {
                    if (!isSessionTokenStillValid(requestToken)) return
                    cacheDao.replaceMessagesForMatch(
                        viewerUserId = viewerUserId,
                        matchId = matchId,
                        messages = fetched.map { OfflineChatCacheMappers.toEntity(viewerUserId, it) }
                    )
                } else {
                    val snapshot = synchronized(currentMessages) {
                        currentMessages.clear()
                        currentMessages.addAll(fetched)
                        currentMessages.toList()
                    }
                    hasEmitted = true
                    send(snapshot)
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (cacheDao == null && !hasEmitted) {
                    hasEmitted = true
                    send(emptyList())
                }
            }
        }

        val syncJob = launch {
            for (unit in refreshTrigger) {
                reloadMessagesFromServer()
            }
        }

        refreshTrigger.trySend(Unit)

        val job = launch {
            realtimeClient.incomingMessages.collect { (incomingTopic, _) ->
                if (incomingTopic == topic) {
                    refreshTrigger.trySend(Unit)
                }
            }
        }

        val chatRefreshJob = launch {
            invalidator.chatTrigger.collect {
                refreshTrigger.trySend(Unit)
            }
        }

        awaitClose {
            refreshTrigger.close()
            cacheJob?.cancel()
            syncJob.cancel()
            job.cancel()
            chatRefreshJob.cancel()
            realtimeClient.unsubscribeTopic(topic)
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun sendMessage(
        matchId: Long,
        senderId: Long,
        text: String
    ): SendMessageResult {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return SendMessageResult.EmptyMessage

        return try {
            val requestToken = activeSessionTokenProvider()
            val params = JSONObject().apply {
                put("p_match_id", matchId)
                put("p_message_text", trimmed)
            }
            val resp = client.rpc("rpc_send_chat_message", params)
            when (resp.optString("code")) {
                "SUCCESS" -> {
                    val newId = resp.optLong("messageId")
                    val senderName = resp.optString("senderName", "Pengguna").ifBlank { "Pengguna" }
                    val sentAt = resp.optLong("sentAt", System.currentTimeMillis())
                    val recipientId = resp.optLong("recipientId", 0L)

                    val cachedMessage = ChatMessage(
                        id = newId,
                        matchId = matchId,
                        senderId = senderId,
                        senderName = senderName,
                        messageText = trimmed,
                        isRead = false,
                        sentAt = sentAt,
                        isFromMe = true
                    )
                    if (isSessionTokenStillValid(requestToken)) {
                        runCatching {
                            cacheDao?.upsertMessage(OfflineChatCacheMappers.toEntity(senderId, cachedMessage))
                            cacheDao?.updateThreadLastMessage(senderId, matchId, trimmed, sentAt)
                        }
                    }

                    val syncPing = JSONObject().put("type", "sync")
                    realtimeClient.broadcast("realtime:match_$matchId", "chat_sync", syncPing)
                    if (recipientId > 0L) {
                        realtimeClient.broadcast("realtime:user_threads_$recipientId", "thread_sync", syncPing)
                    }
                    invalidator.invalidateChat()

                    SendMessageResult.Success(newId)
                }
                "MATCH_NOT_FOUND" -> SendMessageResult.MatchNotFound
                "NOT_MATCH_PARTICIPANT", "UNAUTHORIZED" -> SendMessageResult.NotMatchParticipant
                "CHAT_READ_ONLY" -> {
                    invalidator.invalidateChat()
                    SendMessageResult.ChatReadOnly
                }
                "EMPTY_MESSAGE" -> SendMessageResult.EmptyMessage
                "VALIDATION_FAILED" -> SendMessageResult.DatabaseError(
                    DbFailure.UnexpectedDatabaseError(resp.optString("message", "Pesan tidak valid"))
                )
                else -> SendMessageResult.DatabaseError(
                    DbFailure.UnexpectedDatabaseError("Gagal mengirim pesan. Silakan coba lagi.")
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            SendMessageResult.DatabaseError(DbFailure.UnexpectedDatabaseError(CONNECTION_ERROR_MESSAGE))
        }
    }

    override suspend fun markMessagesRead(matchId: Long, viewerUserId: Long) {
        try {
            val requestToken = activeSessionTokenProvider()
            val resp = client.rpc(
                "rpc_mark_chat_messages_read",
                JSONObject().put("p_match_id", matchId)
            )
            if (resp.optString("code") == "SUCCESS") {
                if (isSessionTokenStillValid(requestToken)) {
                    runCatching {
                        cacheDao?.markMessagesReadLocal(viewerUserId, matchId)
                        cacheDao?.clearThreadUnreadCount(viewerUserId, matchId)
                    }
                }
                invalidator.invalidateChat()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
        }
    }

    private companion object {
        const val CONNECTION_ERROR_MESSAGE = "Terjadi gangguan koneksi ke server. Silakan coba lagi."
    }
}
