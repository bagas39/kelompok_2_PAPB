package com.example.cloble.data.remote

import com.example.cloble.domain.model.ChatMessage
import com.example.cloble.domain.model.ChatThreadSummary
import com.example.cloble.domain.model.MatchStatus
import com.example.cloble.domain.repository.ChatRepository
import com.example.cloble.domain.result.DbFailure
import com.example.cloble.domain.result.SendMessageResult
import com.example.cloble.domain.util.ChatThreadDeduplicator
import kotlinx.coroutines.Dispatchers
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
    private val invalidator: SupabaseDataInvalidator
) : ChatRepository {

    override fun observeChatThreads(userId: Long): Flow<List<ChatThreadSummary>> = channelFlow {
        val userTopic = "realtime:user_threads_$userId"
        realtimeClient.subscribeTopic(userTopic)

        suspend fun fetchThreads(): List<ChatThreadSummary> {
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
                ChatThreadDeduplicator.deduplicateByPartner(list)
            } catch (_: Exception) {
                emptyList()
            }
        }

        send(fetchThreads())

        val realtimeJob = launch {
            realtimeClient.incomingMessages.collect { (incomingTopic, _) ->
                if (incomingTopic == userTopic || incomingTopic.startsWith("realtime:match_")) {
                    send(fetchThreads())
                }
            }
        }

        val invalidatorJob = launch {
            invalidator.discoveryTrigger.collect {
                send(fetchThreads())
            }
        }

        val chatInvalidatorJob = launch {
            invalidator.chatTrigger.collect {
                send(fetchThreads())
            }
        }

        val userInvalidatorJob = launch {
            invalidator.userTrigger.collect {
                send(fetchThreads())
            }
        }

        awaitClose {
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
                currentMessages.clear()
                currentMessages.addAll(fetched)
                send(currentMessages.toList())
            } catch (_: Exception) {
                if (currentMessages.isEmpty()) {
                    send(emptyList())
                }
            }
        }

        reloadMessagesFromServer()

        val job = launch {
            realtimeClient.incomingMessages.collect { (incomingTopic, data) ->
                if (incomingTopic == topic) {
                    val id = data.optLong("id")
                    val mId = data.optLong("matchId")
                    if (mId == matchId && currentMessages.none { it.id == id }) {
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
                        currentMessages.add(newMsg)
                        send(currentMessages.toList())
                    }
                }
            }
        }

        val chatRefreshJob = launch {
            invalidator.chatTrigger.collect {
                reloadMessagesFromServer()
            }
        }

        awaitClose {
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
            invalidator.invalidateChat()
        } catch (_: Exception) {
        }
    }
}
