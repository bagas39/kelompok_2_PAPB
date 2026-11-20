package com.example.cloble.data.repository

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteException
import android.database.sqlite.SQLiteTableLockedException
import com.example.cloble.data.local.dao.ChatThreadRow
import com.example.cloble.data.local.dao.NotificationRow
import com.example.cloble.data.local.dao.OutgoingInterestRow
import com.example.cloble.data.local.dao.PassedClothingRow
import com.example.cloble.data.local.dao.RawDiscoverCandidate
import com.example.cloble.data.local.dao.ReviewWithAuthorRow
import com.example.cloble.data.local.dao.WardrobeItemRow
import com.example.cloble.data.local.entity.ChatMessageEntity
import com.example.cloble.data.local.entity.ClothingItemEntity
import com.example.cloble.data.local.entity.UserEntity
import com.example.cloble.domain.model.ActivityHistoryTab
import com.example.cloble.domain.model.ChatMessage
import com.example.cloble.domain.model.ChatThreadSummary
import com.example.cloble.domain.model.ClothingItem
import com.example.cloble.domain.model.DiscoverCandidate
import com.example.cloble.domain.model.NotificationFeedItem
import com.example.cloble.domain.model.OutgoingInterestSummary
import com.example.cloble.domain.model.PassedClothingSummary
import com.example.cloble.domain.model.UserProfile
import com.example.cloble.domain.model.UserReview
import com.example.cloble.domain.model.WardrobeItemSummary
import kotlinx.coroutines.CancellationException

object EntityMappers {

    inline fun <T> runCatchingSqlite(
        onBusyOrLocked: (String?) -> T,
        onConstraintViolation: (String?) -> T,
        onUnexpectedDatabaseError: (String?) -> T,
        block: () -> T,
    ): T {
        return try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: SQLiteDatabaseLockedException) {
            onBusyOrLocked(e.message)
        } catch (e: SQLiteTableLockedException) {
            onBusyOrLocked(e.message)
        } catch (e: SQLiteConstraintException) {
            onConstraintViolation(e.message)
        } catch (e: SQLiteException) {
            onUnexpectedDatabaseError(e.message)
        }
    }

    fun toDomainClothingItem(entity: ClothingItemEntity): ClothingItem {
        return ClothingItem(
            id = entity.id,
            ownerId = entity.ownerId,
            name = entity.name,
            description = entity.description,
            category = entity.category,
            size = entity.size,
            gender = entity.gender,
            color = entity.color,
            condition = entity.condition,
            mainPhotoUri = entity.mainPhotoUri,
            detailPhotoUri = entity.detailPhotoUri,
            extraPhotoUri = entity.extraPhotoUri,
            isBarterAllowed = entity.isBarterAllowed,
            status = entity.status,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt
        )
    }

    fun toDomainWardrobeSummary(
        row: WardrobeItemRow,
        interestedSenderNames: List<String> = emptyList()
    ): WardrobeItemSummary {
        return WardrobeItemSummary(
            item = toDomainClothingItem(row.item),
            isInActiveMatch = row.isInActiveMatch,
            interestedSenderNames = interestedSenderNames
        )
    }

    fun toDomainDiscoverCandidate(
        raw: RawDiscoverCandidate,
        distanceKm: Int
    ): DiscoverCandidate {
        return DiscoverCandidate(
            item = toDomainClothingItem(raw.item),
            ownerName = raw.ownerName,
            ownerCity = raw.ownerCity,
            ownerAvatarUri = raw.ownerAvatarUri,
            ownerAverageRating = raw.ownerAverageRating,
            ownerReviewCount = raw.ownerReviewCount,
            distanceKm = distanceKm
        )
    }

    fun toDomainNotificationItem(row: NotificationRow): NotificationFeedItem {
        return NotificationFeedItem(
            id = row.id,
            userId = row.userId,
            type = row.type,
            title = row.title,
            body = row.body,
            relatedInterestId = row.relatedInterestId,
            relatedMatchId = row.relatedMatchId,
            isRead = row.isRead,
            createdAt = row.createdAt,
            canReplyToInterest = row.canReplyToInterest,
            disabledReasonText = row.disabledReasonText,
            counterpartUserId = row.counterpartUserId,
            counterpartName = row.counterpartName,
            counterpartPhotoUri = row.counterpartPhotoUri,
            targetItemId = row.targetItemId,
            targetItemName = row.targetItemName
        )
    }

    fun toDomainChatThread(row: ChatThreadRow): ChatThreadSummary {
        return ChatThreadSummary(
            matchId = row.matchId,
            partnerUserId = row.partnerUserId,
            partnerName = row.partnerName,
            partnerCity = row.partnerCity,
            partnerAvatarUri = row.partnerAvatarUri,
            partnerAverageRating = row.partnerAverageRating,
            myItemName = row.myItemName,
            partnerItemName = row.partnerItemName,
            status = row.status,
            lastMessageText = row.lastMessageText,
            lastMessageSentAt = row.lastMessageSentAt,
            unreadCount = row.unreadCount,
            updatedAt = row.updatedAt
        )
    }

    fun toDomainChatMessage(entity: ChatMessageEntity, viewerUserId: Long, senderName: String): ChatMessage {
        return ChatMessage(
            id = entity.id,
            matchId = entity.matchId,
            senderId = entity.senderId,
            senderName = senderName,
            messageText = entity.messageText,
            isRead = entity.isRead,
            sentAt = entity.sentAt,
            isFromMe = entity.senderId == viewerUserId
        )
    }

    fun toDomainReview(row: ReviewWithAuthorRow): UserReview {
        return UserReview(
            id = row.id,
            matchId = row.matchId,
            reviewerId = row.reviewerId,
            reviewerName = row.reviewerName,
            reviewerCity = row.reviewerCity,
            revieweeId = row.revieweeId,
            rating = row.rating,
            comment = row.comment,
            createdAt = row.createdAt
        )
    }

    fun toDomainUserProfile(
        user: UserEntity,
        averageRating: Double = 0.0,
        reviewCount: Int = 0
    ): UserProfile {
        return UserProfile(
            id = user.id,
            name = user.name,
            email = user.email,
            bio = user.bio,
            avatarUri = user.avatarUri,
            city = user.city,
            latitude = user.latitude,
            longitude = user.longitude,
            preferredSize = user.preferredSize,
            preferredGender = user.preferredGender,
            preferredRadiusKm = user.preferredRadiusKm,
            createdAt = user.createdAt,
            averageRating = averageRating,
            reviewCount = reviewCount
        )
    }

    fun toDomainOutgoingInterest(row: OutgoingInterestRow): OutgoingInterestSummary {
        return OutgoingInterestSummary(
            interestId = row.interestId,
            targetItemId = row.targetItemId,
            targetItemName = row.targetItemName,
            targetItemCategory = row.targetItemCategory,
            targetItemPhotoUri = row.targetItemPhotoUri,
            targetItemStatus = row.targetItemStatus,
            ownerId = row.ownerId,
            ownerName = row.ownerName,
            ownerCity = row.ownerCity,
            interestStatus = row.interestStatus,
            createdAt = row.createdAt,
            matchId = row.matchId,
            matchStatus = row.matchStatus,
            isLockedByOtherMatch = row.isLockedByOtherMatch
        )
    }

    fun toDomainPassedClothing(row: PassedClothingRow): PassedClothingSummary {
        return PassedClothingSummary(
            swipePassId = row.swipePassId,
            clothingId = row.clothingId,
            clothingName = row.clothingName,
            clothingCategory = row.clothingCategory,
            clothingPhotoUri = row.clothingPhotoUri,
            clothingStatus = row.clothingStatus,
            isBarterAllowed = row.isBarterAllowed,
            ownerId = row.ownerId,
            ownerName = row.ownerName,
            ownerCity = row.ownerCity,
            passedAt = row.passedAt,
            isInActiveMatch = row.isInActiveMatch
        )
    }
}
