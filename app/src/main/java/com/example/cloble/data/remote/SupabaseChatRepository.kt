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
import org.json.JSONArray
import org.json.JSONObject

class SupabaseChatRepository(
    private val client: SupabaseClient,
    private val realtimeClient: SupabaseRealtimeClient,
    private val invalidator: SupabaseDataInvalidator,
    private val cacheDao: OfflineChatCacheDao? = null
) : ChatRepository {

    private suspend fun fetchUserThreadsOrNull(
        userId: Long,
        partnerUserIdFilter: Long? = null
    ): List<ChatThreadSummary>? {
        return try {
            val matchesArr = client.getJsonArray(
                "matches?or=(user1Id.eq.$userId,user2Id.eq.$userId)&select=*,u1:user1Id(name,city,avatarUri),u2:user2Id(name,city,avatarUri),i1:user1ItemId(name,mainPhotoUri),i2:user2ItemId(name,mainPhotoUri)&order=updatedAt.desc"
            )

            val ratingsByUserId = mutableMapOf<Long, Pair<Double, Int>>()
            try {
                val reviewsArr = client.getJsonArray("reviews?select=revieweeId,rating")
                for (r in 0 until reviewsArr.length()) {
                    val rev = reviewsArr.getJSONObject(r)
                    val rid = rev.optLong("revieweeId")
                    val rating = rev.optDouble("rating", 0.0)
                    val prev = ratingsByUserId[rid]
                    if (prev == null) {
                        ratingsByUserId[rid] = rating to 1
                    } else {
                        ratingsByUserId[rid] = (prev.first + rating) to (prev.second + 1)
                    }
                }
            } catch (_: Exception) {
            }

            val list = mutableListOf<ChatThreadSummary>()
            for (i in 0 until matchesArr.length()) {
                val m = matchesArr.getJSONObject(i)
                val matchId = m.optLong("id")
                val isViewerUser1 = userId == m.optLong("user1Id")
                val partnerUserId = if (isViewerUser1) m.optLong("user2Id") else m.optLong("user1Id")
                if (partnerUserIdFilter != null && partnerUserId != partnerUserIdFilter) continue

                val partnerObj = if (isViewerUser1) m.optJSONObject("u2") else m.optJSONObject("u1")
                val partnerName = partnerObj?.optString("name", "Pengguna") ?: "Pengguna"
                val partnerCity = partnerObj?.optString("city", "Bandung") ?: "Bandung"
                val partnerAvatarUri = partnerObj?.optString("avatarUri")
                    ?.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }

                val ratingStats = ratingsByUserId[partnerUserId]
                val partnerAvgRating = if (ratingStats != null && ratingStats.second > 0) {
                    ratingStats.first / ratingStats.second
                } else {
                    0.0
                }

                val myItemObj = if (isViewerUser1) m.optJSONObject("i1") else m.optJSONObject("i2")
                val partnerItemObj = if (isViewerUser1) m.optJSONObject("i2") else m.optJSONObject("i1")
                val myItemName = myItemObj?.optString("name", "Pakaian") ?: "Pakaian"
                val partnerItemName = partnerItemObj?.optString("name", "Pakaian") ?: "Pakaian"

                val matchStatus = try {
                    MatchStatus.valueOf(m.optString("status", "ACTIVE"))
                } catch (_: Exception) {
                    MatchStatus.ACTIVE
                }

                val latestMsgArr = client.getJsonArray("chat_messages?matchId=eq.$matchId&order=sentAt.desc&limit=1")
                val lastMessage = if (latestMsgArr.length() > 0) latestMsgArr.getJSONObject(0).optString("messageText") else "Match baru terhubung"
                val lastSentAt = if (latestMsgArr.length() > 0) latestMsgArr.getJSONObject(0).optLong("sentAt") else m.optLong("createdAt")

                val unreadArr = client.getJsonArray("chat_messages?matchId=eq.$matchId&senderId=neq.$userId&isRead=eq.false&select=id")
                val unreadCount = unreadArr.length()

                list.add(
                    ChatThreadSummary(
                        matchId = matchId,
                        partnerUserId = partnerUserId,
                        partnerName = partnerName,
                        partnerCity = partnerCity,
                        partnerAvatarUri = partnerAvatarUri,
                        partnerAverageRating = partnerAvgRating,
                        myItemName = myItemName,
                        partnerItemName = partnerItemName,
                        status = matchStatus,
                        lastMessageText = lastMessage,
                        lastMessageSentAt = lastSentAt,
                        unreadCount = unreadCount,
                        updatedAt = m.optLong("updatedAt", System.currentTimeMillis())
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
                val fetched = fetchUserThreadsOrNull(userId, partnerUserIdFilter)
                if (fetched != null) {
                    if (cacheDao != null) {
                        val entities = fetched.map { OfflineChatCacheMappers.toEntity(userId, it) }
                        if (partnerUserIdFilter == null) {
                            cacheDao.replaceThreadsForUser(userId, entities)
                        } else {
                            cacheDao.replacePartnerThreadsForUser(userId, partnerUserIdFilter, entities)
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
                cacheDao.observeMessages(matchId).collect { entities ->
                    hasEmitted = true
                    send(entities.map { OfflineChatCacheMappers.toDomain(it, viewerUserId) })
                }
            }
        } else {
            null
        }

        suspend fun reloadMessagesFromServer() {
            try {
                val arr = client.getJsonArray("chat_messages?matchId=eq.$matchId&select=*,users:senderId(name)&order=sentAt.asc")
                val fetched = mutableListOf<ChatMessage>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val senderObj = obj.optJSONObject("users")
                    val senderName = senderObj?.optString("name", "Pengguna") ?: "Pengguna"
                    fetched.add(SupabaseMappers.parseChatMessage(obj, viewerUserId, senderName))
                }
                if (cacheDao != null) {
                    if (fetched.isNotEmpty()) {
                        cacheDao.upsertMessages(fetched.map { OfflineChatCacheMappers.toEntity(it) })
                    }
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
            realtimeClient.incomingMessages.collect { (incomingTopic, data) ->
                if (incomingTopic == topic) {
                    val id = data.optLong("id")
                    val mId = data.optLong("matchId")
                    if (mId == matchId && id > 0L) {
                        val senderId = data.optLong("senderId")
                        val senderName = data.optString("senderName", "Pengguna")
                        val messageText = data.optString("messageText")
                        val sentAt = data.optLong("sentAt")
                        val isFromMe = senderId == viewerUserId

                        val newMsg = ChatMessage(
                            id = id,
                            matchId = mId,
                            senderId = senderId,
                            senderName = senderName,
                            messageText = messageText,
                            sentAt = sentAt,
                            isFromMe = isFromMe,
                            isRead = false
                        )
                        if (cacheDao != null) {
                            cacheDao.upsertMessage(OfflineChatCacheMappers.toEntity(newMsg))
                            cacheDao.updateThreadLastMessage(mId, messageText, sentAt)
                        } else {
                            val updatedSnapshot = synchronized(currentMessages) {
                                if (currentMessages.none { it.id == id }) {
                                    currentMessages.add(newMsg)
                                    currentMessages.toList()
                                } else {
                                    null
                                }
                            }
                            if (updatedSnapshot != null) {
                                hasEmitted = true
                                send(updatedSnapshot)
                            }
                        }
                    }
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
            val matchJson = client.getJsonObjectOrNull("matches?id=eq.$matchId")
                ?: return SendMessageResult.MatchNotFound

            val user1Id = matchJson.optLong("user1Id")
            val user2Id = matchJson.optLong("user2Id")
            if (senderId != user1Id && senderId != user2Id) {
                return SendMessageResult.NotMatchParticipant
            }

            val status = matchJson.optString("status")
            if (status != "ACTIVE") {
                return SendMessageResult.ChatReadOnly
            }

            val now = System.currentTimeMillis()
            val body = JSONObject().apply {
                put("matchId", matchId)
                put("senderId", senderId)
                put("messageText", trimmed)
                put("isRead", false)
                put("sentAt", now)
            }

            val resp = client.post("chat_messages", body.toString(), returnRepresentation = true)
            val created = JSONArray(resp)
            if (created.length() > 0) {
                val newId = created.getJSONObject(0).optLong("id")
                client.patch("matches?id=eq.$matchId", JSONObject().put("updatedAt", now).toString())

                val senderJson = client.getJsonObjectOrNull("users?id=eq.$senderId&select=name")
                val senderName = senderJson?.optString("name", "Pengguna") ?: "Pengguna"

                val cachedMessage = ChatMessage(
                    id = newId,
                    matchId = matchId,
                    senderId = senderId,
                    senderName = senderName,
                    messageText = trimmed,
                    isRead = false,
                    sentAt = now,
                    isFromMe = true
                )
                runCatching {
                    cacheDao?.upsertMessage(OfflineChatCacheMappers.toEntity(cachedMessage))
                    cacheDao?.updateThreadLastMessage(matchId, trimmed, now)
                }

                val broadcastData = JSONObject().apply {
                    put("id", newId)
                    put("matchId", matchId)
                    put("senderId", senderId)
                    put("senderName", senderName)
                    put("messageText", trimmed)
                    put("sentAt", now)
                }
                realtimeClient.broadcast("realtime:match_$matchId", "new_chat_message", broadcastData)

                val recipientId = if (senderId == user1Id) user2Id else user1Id
                realtimeClient.broadcast("realtime:user_threads_$recipientId", "thread_updated", broadcastData)
                invalidator.invalidateChat()

                SendMessageResult.Success(newId)
            } else {
                SendMessageResult.DatabaseError(DbFailure.UnexpectedDatabaseError("Gagal mengirim pesan"))
            }
        } catch (e: Exception) {
            SendMessageResult.DatabaseError(DbFailure.UnexpectedDatabaseError(e.message))
        }
    }

    override suspend fun markMessagesRead(matchId: Long, viewerUserId: Long) {
        try {
            val body = JSONObject().put("isRead", true)
            client.patch("chat_messages?matchId=eq.$matchId&senderId=neq.$viewerUserId&isRead=eq.false", body.toString())
            runCatching {
                cacheDao?.markMessagesReadLocal(matchId, viewerUserId)
                cacheDao?.clearThreadUnreadCount(viewerUserId, matchId)
            }
            invalidator.invalidateChat()
        } catch (_: Exception) {
        }
    }
}
