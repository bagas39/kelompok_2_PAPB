package com.example.cloble.domain.util

import com.example.cloble.domain.model.ChatThreadSummary
import com.example.cloble.domain.model.MatchStatus

object ChatThreadDeduplicator {

    private fun statusRank(status: MatchStatus): Int {
        return when (status) {
            MatchStatus.ACTIVE -> 0
            MatchStatus.COMPLETED -> 1
            MatchStatus.CANCELLED -> 2
        }
    }

    private fun activityTimestamp(thread: ChatThreadSummary): Long {
        return maxOf(thread.lastMessageSentAt ?: 0L, thread.updatedAt)
    }

    fun deduplicateByPartner(threads: List<ChatThreadSummary>): List<ChatThreadSummary> {
        if (threads.isEmpty()) return emptyList()

        return threads
            .groupBy { it.partnerUserId }
            .values
            .map { group ->
                val sortedGroup = group.sortedWith(
                    compareBy<ChatThreadSummary> { statusRank(it.status) }
                        .thenByDescending { activityTimestamp(it) }
                        .thenByDescending { it.matchId }
                )
                val primary = sortedGroup.first()
                primary.copy(
                    unreadCount = group.sumOf { it.unreadCount },
                    totalMatchesWithPartner = group.size
                )
            }
            .sortedWith(
                compareBy<ChatThreadSummary> { statusRank(it.status) }
                    .thenByDescending { activityTimestamp(it) }
                    .thenByDescending { it.matchId }
            )
    }
}
