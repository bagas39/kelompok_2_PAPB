package com.example.cloble.data.remote

import com.example.cloble.domain.model.ChatMessage
import com.example.cloble.domain.model.ClothingItem
import com.example.cloble.domain.model.DiscoverCandidate
import com.example.cloble.domain.model.InterestStatus
import com.example.cloble.domain.model.ItemStatus
import com.example.cloble.domain.model.MatchStatus
import com.example.cloble.domain.model.NotificationFeedItem
import com.example.cloble.domain.model.NotificationType
import com.example.cloble.domain.model.OutgoingInterestSummary
import com.example.cloble.domain.model.PassedClothingSummary
import com.example.cloble.domain.model.UserProfile
import com.example.cloble.domain.model.UserReview
import com.example.cloble.domain.model.WardrobeItemSummary
import org.json.JSONObject

object SupabaseMappers {

    fun parseUserProfile(json: JSONObject, avgRating: Double = 0.0, reviewCount: Int = 0): UserProfile {
        return UserProfile(
            id = json.optLong("id"),
            name = json.optString("name"),
            email = json.optString("email"),
            bio = json.optString("bio"),
            avatarUri = json.optStringOrNull("avatarUri"),
            city = json.optString("city", "Bandung, Jawa Barat"),
            latitude = json.optDouble("latitude", -6.9175),
            longitude = json.optDouble("longitude", 107.6191),
            preferredSize = json.optString("preferredSize", "M"),
            preferredGender = json.optString("preferredGender", "Uniseks"),
            preferredRadiusKm = json.optInt("preferredRadiusKm", 10),
            averageRating = avgRating,
            reviewCount = reviewCount,
            createdAt = json.optLong("createdAt", System.currentTimeMillis())
        )
    }

    fun parseClothingItem(json: JSONObject): ClothingItem {
        return ClothingItem(
            id = json.optLong("id"),
            ownerId = json.optLong("ownerId"),
            name = json.optString("name"),
            description = json.optString("description"),
            category = json.optString("category"),
            size = json.optString("size"),
            gender = json.optString("gender"),
            color = json.optString("color"),
            condition = json.optString("condition"),
            mainPhotoUri = json.optStringOrNull("mainPhotoUri"),
            detailPhotoUri = json.optStringOrNull("detailPhotoUri"),
            extraPhotoUri = json.optStringOrNull("extraPhotoUri"),
            isBarterAllowed = json.optBoolean("isBarterAllowed", false),
            status = parseItemStatus(json.optString("status")),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = json.optLong("updatedAt", System.currentTimeMillis())
        )
    }

    fun parseWardrobeItemSummary(
        json: JSONObject,
        isInActiveMatch: Boolean = false,
        interestedSenders: List<String> = emptyList()
    ): WardrobeItemSummary {
        val item = parseClothingItem(json)
        return WardrobeItemSummary(
            item = item,
            isInActiveMatch = isInActiveMatch,
            interestedSenderNames = interestedSenders
        )
    }

    fun parseDiscoverCandidate(
        json: JSONObject,
        distanceKm: Int,
        ownerAvgRating: Double = 0.0,
        ownerReviewCount: Int = 0
    ): DiscoverCandidate {
        val ownerJson = json.optJSONObject("users") ?: JSONObject()
        val item = parseClothingItem(json)
        return DiscoverCandidate(
            item = item,
            ownerName = ownerJson.optString("name", "Pengguna"),
            ownerCity = ownerJson.optString("city", "Bandung"),
            ownerAvatarUri = ownerJson.optStringOrNull("avatarUri"),
            ownerAverageRating = ownerAvgRating,
            ownerReviewCount = ownerReviewCount,
            distanceKm = distanceKm
        )
    }

    fun parseNotification(
        json: JSONObject,
        activeItemIds: Set<Long> = emptySet(),
        reviewedMatchIds: Set<Long> = emptySet()
    ): NotificationFeedItem {
        val typeStr = json.optString("type")
        val notifType = try {
            NotificationType.valueOf(typeStr)
        } catch (_: Exception) {
            NotificationType.INCOMING_INTEREST
        }

        val interestJson = json.optJSONObject("interests")
        val senderJson = interestJson?.optJSONObject("users")
        val targetItemJson = interestJson?.optJSONObject("clothing_items")
        val matchJson = json.optJSONObject("matches")

        val relatedInterestId = json.optLongOrNull("relatedInterestId") ?: interestJson?.optLongOrNull("id")
        val relatedMatchId = json.optLongOrNull("relatedMatchId") ?: matchJson?.optLongOrNull("id")
        val matchStatus = matchJson?.optStringOrNull("status")?.let { statusStr ->
            try {
                MatchStatus.valueOf(statusStr)
            } catch (_: Exception) {
                null
            }
        }
        val hasReviewedMatch = relatedMatchId != null && reviewedMatchIds.contains(relatedMatchId)

        var counterpartUserId: Long? = senderJson?.optLongOrNull("id") ?: interestJson?.optLongOrNull("senderUserId")
        var counterpartName: String? = senderJson?.optStringOrNull("name")
        var counterpartPhotoUri: String? = targetItemJson?.optStringOrNull("mainPhotoUri")
        var targetItemId: Long? = targetItemJson?.optLongOrNull("id") ?: interestJson?.optLongOrNull("targetItemId")
        var targetItemName: String? = targetItemJson?.optStringOrNull("name")

        if (matchJson != null) {
            val user1Json = matchJson.optJSONObject("user1")
            val user2Json = matchJson.optJSONObject("user2")
            val u1Id = matchJson.optLongOrNull("user1Id") ?: user1Json?.optLongOrNull("id")
            val u2Id = matchJson.optLongOrNull("user2Id") ?: user2Json?.optLongOrNull("id")
            val notifUserId = json.optLong("userId")
            if (counterpartUserId == null) {
                if (u1Id != null && u1Id != notifUserId) {
                    counterpartUserId = u1Id
                    counterpartName = user1Json?.optStringOrNull("name")
                } else if (u2Id != null && u2Id != notifUserId) {
                    counterpartUserId = u2Id
                    counterpartName = user2Json?.optStringOrNull("name")
                }
            }
        }

        val interestStatus = interestJson?.optStringOrNull("status")
        val itemStatus = targetItemJson?.optStringOrNull("status")
        val isBarterAllowed = targetItemJson?.optBoolean("isBarterAllowed", true) ?: true

        val targetInActiveMatch = targetItemId != null && activeItemIds.contains(targetItemId)

        val canReplyToInterest = (notifType == NotificationType.INCOMING_INTEREST) &&
                (interestStatus == null || interestStatus == "PENDING") &&
                (itemStatus == null || itemStatus == "AVAILABLE") &&
                isBarterAllowed &&
                !targetInActiveMatch &&
                (counterpartUserId != null)

        val disabledReasonText = when {
            interestStatus == "ACCEPTED_MATCHED" -> "Sudah terhubung dalam Match"
            interestStatus == "RESOLVED_BY_MATCH" -> "Diselesaikan melalui Match pasangan"
            interestStatus == "DECLINED" -> "Tawaran telah ditolak"
            interestStatus == "INVALIDATED" || (itemStatus != null && itemStatus != "AVAILABLE") || !isBarterAllowed ->
                "Pakaian sudah tidak tersedia"
            interestStatus == "PENDING" && targetInActiveMatch ->
                "Pakaian sedang terikat dalam Match aktif"
            else -> null
        }

        return NotificationFeedItem(
            id = json.optLong("id"),
            userId = json.optLong("userId"),
            type = notifType,
            title = json.optString("title"),
            body = json.optString("body"),
            relatedInterestId = relatedInterestId,
            relatedMatchId = relatedMatchId,
            isRead = json.optBoolean("isRead", false),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            canReplyToInterest = canReplyToInterest,
            disabledReasonText = disabledReasonText,
            counterpartUserId = counterpartUserId,
            counterpartName = counterpartName,
            counterpartPhotoUri = counterpartPhotoUri,
            targetItemId = targetItemId,
            targetItemName = targetItemName,
            matchStatus = matchStatus,
            hasReviewedMatch = hasReviewedMatch
        )
    }

    fun parseOutgoingInterest(
        json: JSONObject,
        resolvedMatchId: Long? = null,
        resolvedMatchStatus: MatchStatus? = null,
        activeItemIds: Set<Long> = emptySet()
    ): OutgoingInterestSummary {
        val targetItemJson = json.optJSONObject("clothing_items") ?: JSONObject()
        val ownerJson = targetItemJson.optJSONObject("users") ?: JSONObject()
        val matchArr = json.optJSONArray("matches")
        val matchJson = if (matchArr != null && matchArr.length() > 0) matchArr.optJSONObject(0) else null

        val interestStatus = try {
            InterestStatus.valueOf(json.optString("status"))
        } catch (_: Exception) {
            InterestStatus.PENDING
        }

        val targetItemStatus = try {
            ItemStatus.valueOf(targetItemJson.optString("status", "AVAILABLE"))
        } catch (_: Exception) {
            ItemStatus.AVAILABLE
        }

        val finalMatchId = resolvedMatchId ?: matchJson?.optLongOrNull("id")
        val finalMatchStatus = resolvedMatchStatus ?: matchJson?.optString("status")?.let {
            try { MatchStatus.valueOf(it) } catch (_: Exception) { null }
        }

        val targetItemId = json.optLong("targetItemId")
        val isLockedByOtherMatch = finalMatchId == null && (
            interestStatus == InterestStatus.RESOLVED_BY_MATCH ||
                targetItemStatus != ItemStatus.AVAILABLE ||
                !targetItemJson.optBoolean("isBarterAllowed", false) ||
                activeItemIds.contains(targetItemId)
        )

        return OutgoingInterestSummary(
            interestId = json.optLong("id"),
            targetItemId = targetItemId,
            targetItemName = targetItemJson.optString("name"),
            targetItemCategory = targetItemJson.optString("category"),
            targetItemPhotoUri = targetItemJson.optStringOrNull("mainPhotoUri"),
            targetItemStatus = targetItemStatus,
            ownerId = targetItemJson.optLong("ownerId"),
            ownerName = ownerJson.optString("name", "Pengguna"),
            ownerCity = ownerJson.optString("city", "Bandung"),
            interestStatus = interestStatus,
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            matchId = finalMatchId,
            matchStatus = finalMatchStatus,
            isLockedByOtherMatch = isLockedByOtherMatch
        )
    }

    fun parsePassedClothing(
        json: JSONObject,
        isInActiveMatch: Boolean = false
    ): PassedClothingSummary {
        val itemJson = json.optJSONObject("clothing_items") ?: JSONObject()
        val ownerJson = itemJson.optJSONObject("users") ?: JSONObject()
        val itemStatus = try {
            ItemStatus.valueOf(itemJson.optString("status", "AVAILABLE"))
        } catch (_: Exception) {
            ItemStatus.AVAILABLE
        }

        return PassedClothingSummary(
            swipePassId = json.optLong("id"),
            clothingId = json.optLong("targetItemId"),
            clothingName = itemJson.optString("name"),
            clothingCategory = itemJson.optString("category"),
            clothingPhotoUri = itemJson.optStringOrNull("mainPhotoUri"),
            clothingStatus = itemStatus,
            isBarterAllowed = itemJson.optBoolean("isBarterAllowed", false),
            ownerId = itemJson.optLong("ownerId"),
            ownerName = ownerJson.optString("name", "Pengguna"),
            ownerCity = ownerJson.optString("city", "Bandung"),
            passedAt = json.optLong("createdAt", System.currentTimeMillis()),
            isInActiveMatch = isInActiveMatch
        )
    }

    fun parseChatMessage(json: JSONObject, viewerUserId: Long, senderName: String): ChatMessage {
        val senderId = json.optLong("senderId")
        return ChatMessage(
            id = json.optLong("id"),
            matchId = json.optLong("matchId"),
            senderId = senderId,
            senderName = senderName,
            messageText = json.optString("messageText"),
            isFromMe = senderId == viewerUserId,
            isRead = json.optBoolean("isRead", false),
            sentAt = json.optLong("sentAt", System.currentTimeMillis())
        )
    }

    fun parseUserReview(json: JSONObject): UserReview {
        val reviewerJson = json.optJSONObject("users") ?: JSONObject()
        return UserReview(
            id = json.optLong("id"),
            matchId = json.optLong("matchId"),
            reviewerId = json.optLong("reviewerId"),
            reviewerName = reviewerJson.optString("name", "Pengguna"),
            reviewerCity = reviewerJson.optString("city", "Bandung"),
            revieweeId = json.optLong("revieweeId"),
            rating = json.optInt("rating", 5),
            comment = json.optString("comment"),
            createdAt = json.optLong("createdAt", System.currentTimeMillis())
        )
    }

    private fun parseItemStatus(str: String?): ItemStatus {
        return try {
            ItemStatus.valueOf(str ?: "AVAILABLE")
        } catch (_: Exception) {
            ItemStatus.AVAILABLE
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        val v = optString(key)
        return if (v.isEmpty() || v.equals("null", ignoreCase = true)) null else v
    }

    private fun JSONObject.optLongOrNull(key: String): Long? {
        return if (isNull(key)) null else optLong(key)
    }
}
