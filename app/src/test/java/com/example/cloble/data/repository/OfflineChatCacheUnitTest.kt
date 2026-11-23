package com.example.cloble.data.repository

import com.example.cloble.data.local.dao.OfflineChatCacheDao
import com.example.cloble.data.local.db.CloBleDatabase
import com.example.cloble.data.local.entity.CachedChatMessageEntity
import com.example.cloble.data.local.entity.CachedChatThreadEntity
import com.example.cloble.data.local.entity.CachedMatchDetailEntity
import com.example.cloble.domain.model.ChatMessage
import com.example.cloble.domain.model.ChatThreadSummary
import com.example.cloble.domain.model.ClothingItem
import com.example.cloble.domain.model.ItemStatus
import com.example.cloble.domain.model.MatchStatus
import com.example.cloble.domain.model.VerifiedMatchDetail
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineChatCacheUnitTest {

    private fun sampleItem(
        id: Long,
        ownerId: Long,
        name: String,
        photoUri: String? = "https://example.com/$id.jpg"
    ) = ClothingItem(
        id = id,
        ownerId = ownerId,
        name = name,
        description = "Deskripsi $name",
        category = "Atasan",
        size = "M",
        gender = "Uniseks",
        color = "Hitam",
        condition = "Sangat baik",
        mainPhotoUri = photoUri,
        detailPhotoUri = null,
        extraPhotoUri = null,
        isBarterAllowed = true,
        status = ItemStatus.AVAILABLE,
        createdAt = 1000L,
        updatedAt = 2000L
    )

    @Test
    fun threadMapper_roundTripPreservesAllFieldsIncludingMatchCount() {
        val summary = ChatThreadSummary(
            matchId = 15L,
            partnerUserId = 2L,
            partnerName = "Naya",
            partnerCity = "Bandung",
            partnerAvatarUri = "https://example.com/naya.jpg",
            partnerAverageRating = 4.75,
            myItemName = "Kemeja Linen",
            partnerItemName = "Hoodie Hitam",
            status = MatchStatus.ACTIVE,
            lastMessageText = "Ketemu di kafe jam 4 ya",
            lastMessageSentAt = 9999L,
            unreadCount = 3,
            updatedAt = 10000L,
            totalMatchesWithPartner = 2
        )

        val entity = OfflineChatCacheMappers.toEntity(viewerUserId = 1L, summary = summary)
        val restored = OfflineChatCacheMappers.toDomain(entity)

        assertEquals(summary.matchId, restored.matchId)
        assertEquals(summary.partnerUserId, restored.partnerUserId)
        assertEquals(summary.partnerName, restored.partnerName)
        assertEquals(summary.partnerCity, restored.partnerCity)
        assertEquals(summary.partnerAvatarUri, restored.partnerAvatarUri)
        assertEquals(summary.partnerAverageRating, restored.partnerAverageRating, 0.0001)
        assertEquals(summary.myItemName, restored.myItemName)
        assertEquals(summary.partnerItemName, restored.partnerItemName)
        assertEquals(summary.status, restored.status)
        assertEquals(summary.lastMessageText, restored.lastMessageText)
        assertEquals(summary.lastMessageSentAt, restored.lastMessageSentAt)
        assertEquals(summary.unreadCount, restored.unreadCount)
        assertEquals(summary.updatedAt, restored.updatedAt)
        assertEquals(summary.totalMatchesWithPartner, restored.totalMatchesWithPartner)
    }

    @Test
    fun messageMapper_computesIsFromMeRelativeToViewer() {
        val message = ChatMessage(
            id = 42L,
            matchId = 15L,
            senderId = 1L,
            senderName = "Raka",
            messageText = "Halo!",
            isRead = false,
            sentAt = 5000L,
            isFromMe = true
        )

        val entity = OfflineChatCacheMappers.toEntity(viewerUserId = 1L, message = message)
        val forSender = OfflineChatCacheMappers.toDomain(entity, viewerUserId = 1L)
        val forPartner = OfflineChatCacheMappers.toDomain(entity, viewerUserId = 2L)

        assertEquals(1L, entity.viewerUserId)
        assertTrue(forSender.isFromMe)
        assertFalse(forPartner.isFromMe)
        assertEquals(message.messageText, forPartner.messageText)
        assertEquals(message.senderName, forPartner.senderName)
        assertEquals(message.sentAt, forPartner.sentAt)
    }

    @Test
    fun matchDetailMapper_roundTripPreservesNullableAndEmbeddedFields() {
        val myItem = sampleItem(id = 10L, ownerId = 1L, name = "Jaket Denim", photoUri = null)
        val partnerItem = sampleItem(id = 20L, ownerId = 2L, name = "Cardigan Cream")
        val detail = VerifiedMatchDetail(
            matchId = 77L,
            status = MatchStatus.COMPLETED,
            meetingLocation = "Taman Lansia Bandung",
            createdAt = 3000L,
            updatedAt = 4000L,
            myUserId = 1L,
            partnerUserId = 2L,
            partnerName = "Naya",
            partnerCity = "Bandung",
            partnerAvatarUri = null,
            partnerAverageRating = 4.9,
            partnerReviewCount = 8,
            myItem = myItem,
            partnerItem = partnerItem
        )

        val entity = OfflineChatCacheMappers.toEntity(detail)
        val restored = OfflineChatCacheMappers.toDomain(entity)

        assertEquals(detail, restored)
        assertNull(restored.partnerAvatarUri)
        assertNull(restored.myItem.mainPhotoUri)
        assertEquals("Taman Lansia Bandung", restored.meetingLocation)
    }

    @Test
    fun fakeCacheDao_replacesSlicesUpdatesUnreadAndClearsCleanly() = runBlocking {
        val dao = FakeOfflineChatCacheDao()

        val user1ThreadA = OfflineChatCacheMappers.toEntity(
            viewerUserId = 1L,
            summary = ChatThreadSummary(
                matchId = 100L,
                partnerUserId = 2L,
                partnerName = "Naya",
                partnerCity = "Bandung",
                partnerAverageRating = 4.8,
                myItemName = "Kemeja",
                partnerItemName = "Hoodie",
                status = MatchStatus.ACTIVE,
                lastMessageText = "Pesan lama",
                lastMessageSentAt = 1000L,
                unreadCount = 2,
                updatedAt = 1000L
            )
        )
        val user1ThreadB = OfflineChatCacheMappers.toEntity(
            viewerUserId = 1L,
            summary = ChatThreadSummary(
                matchId = 101L,
                partnerUserId = 3L,
                partnerName = "Bagas",
                partnerCity = "Cimahi",
                partnerAverageRating = 3.5,
                myItemName = "Celana",
                partnerItemName = "Kaos",
                status = MatchStatus.COMPLETED,
                lastMessageText = "Done",
                lastMessageSentAt = 2000L,
                unreadCount = 0,
                updatedAt = 2000L
            )
        )
        val user2Thread = user1ThreadA.copy(viewerUserId = 2L, partnerUserId = 1L, partnerName = "Raka")

        dao.replaceThreadsForUser(1L, listOf(user1ThreadA, user1ThreadB))
        dao.replaceThreadsForUser(2L, listOf(user2Thread))

        assertEquals(2, dao.observeThreads(1L).first().size)
        assertEquals(1, dao.observeThreads(2L).first().size)

        val updatedThreadA = user1ThreadA.copy(unreadCount = 5)
        dao.replacePartnerThreadsForUser(1L, 2L, listOf(updatedThreadA))
        assertEquals(2, dao.observeThreads(1L).first().size)
        assertEquals(5, dao.observePartnerThreads(1L, 2L).first().single().unreadCount)

        dao.clearThreadUnreadCount(1L, 100L)
        dao.updateThreadLastMessage(1L, 100L, "Sampai lokasi", 5500L)
        val threadAfterRead = dao.observePartnerThreads(1L, 2L).first().single()
        assertEquals(0, threadAfterRead.unreadCount)
        assertEquals("Sampai lokasi", threadAfterRead.lastMessageText)
        assertEquals(5500L, threadAfterRead.updatedAt)
        assertEquals("Pesan lama", dao.observeThreads(2L).first().single().lastMessageText)

        dao.upsertMessages(
            listOf(
                CachedChatMessageEntity(1L, 1L, 100L, 2L, "Naya", "Halo", isRead = false, sentAt = 1000L),
                CachedChatMessageEntity(1L, 2L, 100L, 1L, "Raka", "Hai", isRead = false, sentAt = 2000L)
            )
        )
        dao.markMessagesReadLocal(viewerUserId = 1L, matchId = 100L)
        val messages = dao.observeMessages(viewerUserId = 1L, matchId = 100L).first()
        assertTrue(messages.first { it.id == 1L }.isRead)
        assertFalse(messages.first { it.id == 2L }.isRead)

        val detail = OfflineChatCacheMappers.toEntity(
            VerifiedMatchDetail(
                matchId = 100L,
                status = MatchStatus.ACTIVE,
                meetingLocation = "Dago",
                createdAt = 100L,
                updatedAt = 200L,
                myUserId = 1L,
                partnerUserId = 2L,
                partnerName = "Naya",
                partnerCity = "Bandung",
                partnerAverageRating = 4.8,
                partnerReviewCount = 4,
                myItem = sampleItem(10L, 1L, "Kemeja"),
                partnerItem = sampleItem(20L, 2L, "Hoodie")
            )
        )
        dao.upsertMatchDetail(detail)
        assertEquals("Dago", dao.observeMatchDetail(1L, 100L).first()?.meetingLocation)

        dao.clearAll()
        assertTrue(dao.observeThreads(1L).first().isEmpty())
        assertTrue(dao.observeMessages(1L, 100L).first().isEmpty())
        assertNull(dao.observeMatchDetail(1L, 100L).first())
    }

    @Test
    fun replaceMessagesForMatch_removesServerDeletedMessagesAndIsolatesByViewer() = runBlocking {
        val dao = FakeOfflineChatCacheDao()
        val msg1Viewer1 = CachedChatMessageEntity(1L, 10L, 100L, 2L, "Naya", "Pesan 1", isRead = false, sentAt = 1000L)
        val msg2Viewer1 = CachedChatMessageEntity(1L, 11L, 100L, 1L, "Raka", "Pesan 2", isRead = false, sentAt = 2000L)
        val msg1Viewer2 = CachedChatMessageEntity(2L, 10L, 100L, 2L, "Naya", "Pesan 1", isRead = false, sentAt = 1000L)

        dao.replaceMessagesForMatch(1L, 100L, listOf(msg1Viewer1, msg2Viewer1))
        dao.replaceMessagesForMatch(2L, 100L, listOf(msg1Viewer2))

        dao.replaceMessagesForMatch(1L, 100L, listOf(msg2Viewer1))
        assertEquals(listOf(11L), dao.observeMessages(1L, 100L).first().map { it.id })
        assertEquals(listOf(10L), dao.observeMessages(2L, 100L).first().map { it.id })

        dao.replaceMessagesForMatch(1L, 100L, emptyList())
        assertTrue(dao.observeMessages(1L, 100L).first().isEmpty())
        assertEquals(1, dao.observeMessages(2L, 100L).first().size)
    }

    @Test
    fun databaseName_usesProductionCacheFileName() {
        assertEquals("cloble_cache.db", CloBleDatabase.DATABASE_NAME)
    }

    @Test
    fun parallelMatchStatusUpdates_onlyAffectTargetMatchAndSyncEmbeddedItems() = runBlocking {
        val dao = FakeOfflineChatCacheDao()
        val match1 = OfflineChatCacheMappers.toEntity(
            viewerUserId = 1L,
            summary = ChatThreadSummary(
                matchId = 201L,
                partnerUserId = 2L,
                partnerName = "Naya",
                partnerCity = "Bandung",
                partnerAverageRating = 4.8,
                myItemName = "Kemeja A",
                partnerItemName = "Hoodie A",
                status = MatchStatus.ACTIVE,
                lastMessageText = "Match 1",
                lastMessageSentAt = 1000L,
                unreadCount = 0,
                updatedAt = 1000L,
                totalMatchesWithPartner = 2
            )
        )
        val match2 = match1.copy(
            matchId = 202L,
            myItemName = "Jaket B",
            partnerItemName = "Cardigan B",
            lastMessageText = "Match 2",
            updatedAt = 2000L
        )
        dao.replaceThreadsForUser(1L, listOf(match1, match2))
        dao.upsertMatchDetail(
            OfflineChatCacheMappers.toEntity(
                VerifiedMatchDetail(
                    matchId = 201L,
                    status = MatchStatus.ACTIVE,
                    meetingLocation = null,
                    createdAt = 100L,
                    updatedAt = 1000L,
                    myUserId = 1L,
                    partnerUserId = 2L,
                    partnerName = "Naya",
                    partnerCity = "Bandung",
                    partnerAverageRating = 4.8,
                    partnerReviewCount = 3,
                    myItem = sampleItem(10L, 1L, "Kemeja A"),
                    partnerItem = sampleItem(20L, 2L, "Hoodie A")
                )
            )
        )
        dao.upsertMatchDetail(
            OfflineChatCacheMappers.toEntity(
                VerifiedMatchDetail(
                    matchId = 202L,
                    status = MatchStatus.ACTIVE,
                    meetingLocation = null,
                    createdAt = 100L,
                    updatedAt = 2000L,
                    myUserId = 1L,
                    partnerUserId = 2L,
                    partnerName = "Naya",
                    partnerCity = "Bandung",
                    partnerAverageRating = 4.8,
                    partnerReviewCount = 3,
                    myItem = sampleItem(11L, 1L, "Jaket B"),
                    partnerItem = sampleItem(21L, 2L, "Cardigan B")
                )
            )
        )

        dao.updateThreadStatus(1L, 201L, MatchStatus.COMPLETED, 5000L)
        dao.updateMatchDetailStatus(1L, 201L, MatchStatus.COMPLETED, "Dago", 5000L)
        dao.updateThreadStatus(1L, 202L, MatchStatus.CANCELLED, 6000L)
        dao.updateMatchDetailStatus(1L, 202L, MatchStatus.CANCELLED, null, 6000L)

        val detail201 = dao.observeMatchDetail(1L, 201L).first()
        val detail202 = dao.observeMatchDetail(1L, 202L).first()
        val threads = dao.observePartnerThreads(1L, 2L).first()

        assertEquals(MatchStatus.COMPLETED, detail201?.status)
        assertEquals("Dago", detail201?.meetingLocation)
        assertEquals(ItemStatus.EXCHANGED, detail201?.myItem?.status)
        assertEquals(ItemStatus.EXCHANGED, detail201?.partnerItem?.status)
        assertFalse(detail201?.myItem?.isBarterAllowed ?: true)
        assertFalse(detail201?.partnerItem?.isBarterAllowed ?: true)
        assertEquals(MatchStatus.CANCELLED, detail202?.status)
        assertNull(detail202?.meetingLocation)
        assertEquals(ItemStatus.AVAILABLE, detail202?.myItem?.status)
        assertEquals(MatchStatus.COMPLETED, threads.first { it.matchId == 201L }.status)
        assertEquals(MatchStatus.CANCELLED, threads.first { it.matchId == 202L }.status)
        assertEquals(2, threads.first { it.matchId == 201L }.totalMatchesWithPartner)
    }

    private class FakeOfflineChatCacheDao : OfflineChatCacheDao() {
        private val threadsState = MutableStateFlow<List<CachedChatThreadEntity>>(emptyList())
        private val messagesState = MutableStateFlow<List<CachedChatMessageEntity>>(emptyList())
        private val matchDetailsState = MutableStateFlow<List<CachedMatchDetailEntity>>(emptyList())

        override fun observeThreads(viewerUserId: Long): Flow<List<CachedChatThreadEntity>> =
            threadsState.map { list ->
                list.filter { it.viewerUserId == viewerUserId }
                    .sortedWith(compareByDescending<CachedChatThreadEntity> { it.updatedAt }.thenByDescending { it.matchId })
            }

        override fun observePartnerThreads(
            viewerUserId: Long,
            partnerUserId: Long
        ): Flow<List<CachedChatThreadEntity>> =
            threadsState.map { list ->
                list.filter { it.viewerUserId == viewerUserId && it.partnerUserId == partnerUserId }
                    .sortedWith(compareByDescending<CachedChatThreadEntity> { it.updatedAt }.thenByDescending { it.matchId })
            }

        override suspend fun upsertThreads(threads: List<CachedChatThreadEntity>) {
            val map = threadsState.value.associateBy { it.viewerUserId to it.matchId }.toMutableMap()
            threads.forEach { map[it.viewerUserId to it.matchId] = it }
            threadsState.value = map.values.toList()
        }

        override suspend fun deleteThreadsForUser(viewerUserId: Long) {
            threadsState.value = threadsState.value.filterNot { it.viewerUserId == viewerUserId }
        }

        override suspend fun deletePartnerThreadsForUser(viewerUserId: Long, partnerUserId: Long) {
            threadsState.value = threadsState.value.filterNot {
                it.viewerUserId == viewerUserId && it.partnerUserId == partnerUserId
            }
        }

        override suspend fun clearThreadUnreadCount(viewerUserId: Long, matchId: Long) {
            threadsState.value = threadsState.value.map {
                if (it.viewerUserId == viewerUserId && it.matchId == matchId) it.copy(unreadCount = 0) else it
            }
        }

        override suspend fun updateThreadLastMessage(
            viewerUserId: Long,
            matchId: Long,
            messageText: String,
            sentAt: Long
        ) {
            threadsState.value = threadsState.value.map {
                if (it.viewerUserId == viewerUserId && it.matchId == matchId) {
                    it.copy(lastMessageText = messageText, lastMessageSentAt = sentAt, updatedAt = sentAt)
                } else {
                    it
                }
            }
        }

        override suspend fun updateThreadStatus(
            viewerUserId: Long,
            matchId: Long,
            status: MatchStatus,
            updatedAt: Long
        ) {
            threadsState.value = threadsState.value.map {
                if (it.viewerUserId == viewerUserId && it.matchId == matchId && it.status != status) {
                    it.copy(status = status, updatedAt = updatedAt)
                } else {
                    it
                }
            }
        }

        override fun observeMessages(viewerUserId: Long, matchId: Long): Flow<List<CachedChatMessageEntity>> =
            messagesState.map { list ->
                list.filter { it.viewerUserId == viewerUserId && it.matchId == matchId }
                    .sortedWith(compareBy<CachedChatMessageEntity> { it.sentAt }.thenBy { it.id })
            }

        override suspend fun upsertMessage(message: CachedChatMessageEntity) {
            upsertMessages(listOf(message))
        }

        override suspend fun upsertMessages(messages: List<CachedChatMessageEntity>) {
            val map = messagesState.value.associateBy { it.viewerUserId to it.id }.toMutableMap()
            messages.forEach { map[it.viewerUserId to it.id] = it }
            messagesState.value = map.values.toList()
        }

        override suspend fun deleteMessagesForMatch(viewerUserId: Long, matchId: Long) {
            messagesState.value = messagesState.value.filterNot {
                it.viewerUserId == viewerUserId && it.matchId == matchId
            }
        }

        override suspend fun markMessagesReadLocal(viewerUserId: Long, matchId: Long) {
            messagesState.value = messagesState.value.map {
                if (it.viewerUserId == viewerUserId &&
                    it.matchId == matchId &&
                    it.senderId != viewerUserId &&
                    !it.isRead
                ) {
                    it.copy(isRead = true)
                } else {
                    it
                }
            }
        }

        override fun observeMatchDetail(viewerUserId: Long, matchId: Long): Flow<CachedMatchDetailEntity?> =
            matchDetailsState.map { list ->
                list.firstOrNull { it.viewerUserId == viewerUserId && it.matchId == matchId }
            }

        override suspend fun upsertMatchDetail(detail: CachedMatchDetailEntity) {
            val map = matchDetailsState.value.associateBy { it.viewerUserId to it.matchId }.toMutableMap()
            map[detail.viewerUserId to detail.matchId] = detail
            matchDetailsState.value = map.values.toList()
        }

        override suspend fun updateMatchDetailStatus(
            viewerUserId: Long,
            matchId: Long,
            status: MatchStatus,
            meetingLocation: String?,
            updatedAt: Long
        ) {
            matchDetailsState.value = matchDetailsState.value.map {
                val embeddedNeedsCompletedSync = status == MatchStatus.COMPLETED && (
                    it.myItem.status != ItemStatus.EXCHANGED ||
                        it.partnerItem.status != ItemStatus.EXCHANGED ||
                        it.myItem.isBarterAllowed ||
                        it.partnerItem.isBarterAllowed
                    )
                if (it.viewerUserId == viewerUserId &&
                    it.matchId == matchId &&
                    (it.status != status || (meetingLocation != null && it.meetingLocation == null) || embeddedNeedsCompletedSync)
                ) {
                    it.copy(
                        status = status,
                        meetingLocation = it.meetingLocation ?: meetingLocation,
                        updatedAt = updatedAt,
                        myItem = if (status == MatchStatus.COMPLETED) {
                            it.myItem.copy(status = ItemStatus.EXCHANGED, isBarterAllowed = false)
                        } else {
                            it.myItem
                        },
                        partnerItem = if (status == MatchStatus.COMPLETED) {
                            it.partnerItem.copy(status = ItemStatus.EXCHANGED, isBarterAllowed = false)
                        } else {
                            it.partnerItem
                        }
                    )
                } else {
                    it
                }
            }
        }

        override suspend fun deleteMatchDetail(viewerUserId: Long, matchId: Long) {
            matchDetailsState.value = matchDetailsState.value.filterNot {
                it.viewerUserId == viewerUserId && it.matchId == matchId
            }
        }

        override suspend fun clearAllThreads() {
            threadsState.value = emptyList()
        }

        override suspend fun clearAllMessages() {
            messagesState.value = emptyList()
        }

        override suspend fun clearAllMatchDetails() {
            matchDetailsState.value = emptyList()
        }
    }
}
