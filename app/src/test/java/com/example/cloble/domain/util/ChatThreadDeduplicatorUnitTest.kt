package com.example.cloble.domain.util

import com.example.cloble.domain.model.ChatThreadSummary
import com.example.cloble.domain.model.MatchStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ChatThreadDeduplicatorUnitTest {

    private fun thread(
        matchId: Long,
        partnerUserId: Long,
        partnerName: String = "Naya",
        status: MatchStatus = MatchStatus.ACTIVE,
        lastSentAt: Long? = null,
        updatedAt: Long = 1000L,
        unreadCount: Int = 0
    ): ChatThreadSummary {
        return ChatThreadSummary(
            matchId = matchId,
            partnerUserId = partnerUserId,
            partnerName = partnerName,
            partnerCity = "Bandung",
            partnerAvatarUri = null,
            partnerAverageRating = 4.5,
            myItemName = "Kemeja",
            partnerItemName = "Hoodie",
            status = status,
            lastMessageText = "Halo",
            lastMessageSentAt = lastSentAt,
            unreadCount = unreadCount,
            updatedAt = updatedAt
        )
    }

    @Test
    fun deduplicateByPartner_collapsesDuplicateThreadsAndSumsUnread() {
        val input = listOf(
            thread(matchId = 10L, partnerUserId = 2L, status = MatchStatus.ACTIVE, lastSentAt = 2000L, unreadCount = 2),
            thread(matchId = 11L, partnerUserId = 2L, status = MatchStatus.ACTIVE, lastSentAt = 3000L, unreadCount = 3),
            thread(matchId = 12L, partnerUserId = 3L, partnerName = "Bagas", status = MatchStatus.ACTIVE, lastSentAt = 2500L, unreadCount = 1)
        )

        val result = ChatThreadDeduplicator.deduplicateByPartner(input)

        assertEquals(2, result.size)
        assertEquals(2L, result[0].partnerUserId)
        assertEquals(11L, result[0].matchId)
        assertEquals(5, result[0].unreadCount)
        assertEquals(2, result[0].totalMatchesWithPartner)

        assertEquals(3L, result[1].partnerUserId)
        assertEquals(12L, result[1].matchId)
        assertEquals(1, result[1].unreadCount)
        assertEquals(1, result[1].totalMatchesWithPartner)
    }

    @Test
    fun deduplicateByPartner_activeMatchBeatsNewerCancelledOrCompletedMatch() {
        val input = listOf(
            thread(matchId = 1L, partnerUserId = 5L, status = MatchStatus.CANCELLED, lastSentAt = 9999L, updatedAt = 9999L, unreadCount = 1),
            thread(matchId = 2L, partnerUserId = 5L, status = MatchStatus.COMPLETED, lastSentAt = 8000L, updatedAt = 8000L, unreadCount = 0),
            thread(matchId = 3L, partnerUserId = 5L, status = MatchStatus.ACTIVE, lastSentAt = 1500L, updatedAt = 1500L, unreadCount = 4)
        )

        val result = ChatThreadDeduplicator.deduplicateByPartner(input)

        assertEquals(1, result.size)
        assertEquals(3L, result[0].matchId)
        assertEquals(MatchStatus.ACTIVE, result[0].status)
        assertEquals(5, result[0].unreadCount)
        assertEquals(3, result[0].totalMatchesWithPartner)
    }

    @Test
    fun deduplicateByPartner_emptyInputReturnsEmptyList() {
        assertEquals(emptyList<ChatThreadSummary>(), ChatThreadDeduplicator.deduplicateByPartner(emptyList()))
    }
}
