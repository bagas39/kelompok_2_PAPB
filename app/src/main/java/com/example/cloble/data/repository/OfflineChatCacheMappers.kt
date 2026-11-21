package com.example.cloble.data.repository

import com.example.cloble.data.local.entity.CachedChatMessageEntity
import com.example.cloble.data.local.entity.CachedChatThreadEntity
import com.example.cloble.data.local.entity.CachedClothingSnapshot
import com.example.cloble.data.local.entity.CachedMatchDetailEntity
import com.example.cloble.domain.model.ChatMessage
import com.example.cloble.domain.model.ChatThreadSummary
import com.example.cloble.domain.model.ClothingItem
import com.example.cloble.domain.model.VerifiedMatchDetail

/**
 * Maps between domain models and Room offline-cache entities.
 */
object OfflineChatCacheMappers {

    fun toEntity(viewerUserId: Long, summary: ChatThreadSummary): CachedChatThreadEntity {
        return CachedChatThreadEntity(
            viewerUserId = viewerUserId,
            matchId = summary.matchId,
            partnerUserId = summary.partnerUserId,
            partnerName = summary.partnerName,
            partnerCity = summary.partnerCity,
            partnerAvatarUri = summary.partnerAvatarUri,
            partnerAverageRating = summary.partnerAverageRating,
            myItemName = summary.myItemName,
            partnerItemName = summary.partnerItemName,
            status = summary.status,
            lastMessageText = summary.lastMessageText,
            lastMessageSentAt = summary.lastMessageSentAt,
            unreadCount = summary.unreadCount,
            updatedAt = summary.updatedAt
        )
    }

    fun toDomain(entity: CachedChatThreadEntity): ChatThreadSummary {
        return ChatThreadSummary(
            matchId = entity.matchId,
            partnerUserId = entity.partnerUserId,
            partnerName = entity.partnerName,
            partnerCity = entity.partnerCity,
            partnerAvatarUri = entity.partnerAvatarUri,
            partnerAverageRating = entity.partnerAverageRating,
            myItemName = entity.myItemName,
            partnerItemName = entity.partnerItemName,
            status = entity.status,
            lastMessageText = entity.lastMessageText,
            lastMessageSentAt = entity.lastMessageSentAt,
            unreadCount = entity.unreadCount,
            updatedAt = entity.updatedAt
        )
    }

    fun toEntity(message: ChatMessage): CachedChatMessageEntity {
        return CachedChatMessageEntity(
            id = message.id,
            matchId = message.matchId,
            senderId = message.senderId,
            senderName = message.senderName,
            messageText = message.messageText,
            isRead = message.isRead,
            sentAt = message.sentAt
        )
    }

    fun toDomain(entity: CachedChatMessageEntity, viewerUserId: Long): ChatMessage {
        return ChatMessage(
            id = entity.id,
            matchId = entity.matchId,
            senderId = entity.senderId,
            senderName = entity.senderName,
            messageText = entity.messageText,
            isRead = entity.isRead,
            sentAt = entity.sentAt,
            isFromMe = entity.senderId == viewerUserId
        )
    }

    fun toEntity(detail: VerifiedMatchDetail): CachedMatchDetailEntity {
        return CachedMatchDetailEntity(
            viewerUserId = detail.myUserId,
            matchId = detail.matchId,
            status = detail.status,
            meetingLocation = detail.meetingLocation,
            createdAt = detail.createdAt,
            updatedAt = detail.updatedAt,
            partnerUserId = detail.partnerUserId,
            partnerName = detail.partnerName,
            partnerCity = detail.partnerCity,
            partnerAvatarUri = detail.partnerAvatarUri,
            partnerAverageRating = detail.partnerAverageRating,
            partnerReviewCount = detail.partnerReviewCount,
            myItem = toSnapshot(detail.myItem),
            partnerItem = toSnapshot(detail.partnerItem)
        )
    }

    fun toDomain(entity: CachedMatchDetailEntity): VerifiedMatchDetail {
        return VerifiedMatchDetail(
            matchId = entity.matchId,
            status = entity.status,
            meetingLocation = entity.meetingLocation,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            myUserId = entity.viewerUserId,
            partnerUserId = entity.partnerUserId,
            partnerName = entity.partnerName,
            partnerCity = entity.partnerCity,
            partnerAvatarUri = entity.partnerAvatarUri,
            partnerAverageRating = entity.partnerAverageRating,
            partnerReviewCount = entity.partnerReviewCount,
            myItem = toDomainClothingItem(entity.myItem),
            partnerItem = toDomainClothingItem(entity.partnerItem)
        )
    }

    private fun toSnapshot(item: ClothingItem): CachedClothingSnapshot {
        return CachedClothingSnapshot(
            id = item.id,
            ownerId = item.ownerId,
            name = item.name,
            description = item.description,
            category = item.category,
            size = item.size,
            gender = item.gender,
            color = item.color,
            condition = item.condition,
            mainPhotoUri = item.mainPhotoUri,
            detailPhotoUri = item.detailPhotoUri,
            extraPhotoUri = item.extraPhotoUri,
            isBarterAllowed = item.isBarterAllowed,
            status = item.status,
            createdAt = item.createdAt,
            updatedAt = item.updatedAt
        )
    }

    private fun toDomainClothingItem(snapshot: CachedClothingSnapshot): ClothingItem {
        return ClothingItem(
            id = snapshot.id,
            ownerId = snapshot.ownerId,
            name = snapshot.name,
            description = snapshot.description,
            category = snapshot.category,
            size = snapshot.size,
            gender = snapshot.gender,
            color = snapshot.color,
            condition = snapshot.condition,
            mainPhotoUri = snapshot.mainPhotoUri,
            detailPhotoUri = snapshot.detailPhotoUri,
            extraPhotoUri = snapshot.extraPhotoUri,
            isBarterAllowed = snapshot.isBarterAllowed,
            status = snapshot.status,
            createdAt = snapshot.createdAt,
            updatedAt = snapshot.updatedAt
        )
    }
}
