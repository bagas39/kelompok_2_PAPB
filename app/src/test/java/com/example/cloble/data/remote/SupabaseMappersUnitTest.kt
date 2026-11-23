package com.example.cloble.data.remote

import com.example.cloble.domain.model.ItemStatus
import com.example.cloble.domain.model.InterestStatus
import com.example.cloble.domain.model.MatchStatus
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseMappersUnitTest {

    @Test
    fun parseUserProfile_parsesCorrectly() {
        val json = JSONObject().apply {
            put("id", 1L)
            put("name", "Raka Pratama")
            put("email", "raka@cloble.demo")
            put("bio", "Penggemar linen")
            put("avatarUri", "https://example.com/avatar.jpg")
            put("city", "Bandung, Jawa Barat")
            put("latitude", -6.9175)
            put("longitude", 107.6191)
            put("preferredSize", "M")
            put("preferredGender", "Uniseks")
            put("preferredRadiusKm", 25)
            put("createdAt", 1730000000000L)
        }

        val profile = SupabaseMappers.parseUserProfile(json, 4.5, 10)
        assertEquals(1L, profile.id)
        assertEquals("Raka Pratama", profile.name)
        assertEquals("https://example.com/avatar.jpg", profile.avatarUri)
        assertEquals(4.5, profile.averageRating, 0.001)
        assertEquals(10, profile.reviewCount)

        val jsonWithoutAvatar = JSONObject().apply {
            put("id", 2L)
            put("name", "Naya")
            put("email", "naya@cloble.demo")
            put("bio", "")
            put("avatarUri", JSONObject.NULL)
        }
        assertNull(SupabaseMappers.parseUserProfile(jsonWithoutAvatar).avatarUri)
    }

    @Test
    fun parseClothingItem_parsesCorrectly() {
        val json = JSONObject().apply {
            put("id", 4L)
            put("ownerId", 2L)
            put("name", "Hoodie hitam")
            put("description", "Fleece tebal")
            put("category", "Luaran")
            put("size", "M")
            put("gender", "Uniseks")
            put("color", "Hitam")
            put("condition", "Sangat baik")
            put("mainPhotoUri", "https://images.unsplash.com/photo-1556905055-8f358a7a47b2")
            put("isBarterAllowed", true)
            put("status", "AVAILABLE")
            put("createdAt", 1730000013000L)
            put("updatedAt", 1730000013000L)
        }

        val item = SupabaseMappers.parseClothingItem(json)
        assertEquals(4L, item.id)
        assertEquals(2L, item.ownerId)
        assertEquals("Hoodie hitam", item.name)
        assertTrue(item.isBarterAllowed)
        assertEquals(ItemStatus.AVAILABLE, item.status)
        assertNotNull(item.mainPhotoUri)
    }

    @Test
    fun parseDiscoverCandidate_parsesNestedUser() {
        val json = JSONObject().apply {
            put("id", 4L)
            put("ownerId", 2L)
            put("name", "Hoodie hitam")
            put("description", "Fleece tebal")
            put("category", "Luaran")
            put("size", "M")
            put("gender", "Uniseks")
            put("color", "Hitam")
            put("condition", "Sangat baik")
            put("isBarterAllowed", true)
            put("status", "AVAILABLE")
            put("users", JSONObject().apply {
                put("id", 2L)
                put("name", "Naya")
                put("city", "Bandung")
            })
        }

        val candidate = SupabaseMappers.parseDiscoverCandidate(json, 5, 4.8, 12)
        assertEquals("Naya", candidate.ownerName)
        assertEquals("Bandung", candidate.ownerCity)
        assertEquals(5, candidate.distanceKm)
        assertEquals(4.8, candidate.ownerAverageRating, 0.001)
        assertEquals(12, candidate.ownerReviewCount)
    }

    @Test
    fun parseNotification_incomingInterest_withNestedEntities_parsesCorrectly() {
        val json = JSONObject().apply {
            put("id", 2L)
            put("userId", 1L)
            put("type", "INCOMING_INTEREST")
            put("title", "Naya tertarik pada Kemeja linen krem")
            put("body", "Buka lemari Naya dan pilih tepat satu pakaian berizin barter.")
            put("relatedInterestId", 2L)
            put("isRead", false)
            put("createdAt", 1730000031100L)
            put("interests", JSONObject().apply {
                put("id", 2L)
                put("senderUserId", 2L)
                put("status", "PENDING")
                put("users", JSONObject().apply {
                    put("id", 2L)
                    put("name", "Naya")
                })
                put("clothing_items", JSONObject().apply {
                    put("id", 1L)
                    put("name", "Kemeja linen krem")
                    put("status", "AVAILABLE")
                    put("isBarterAllowed", true)
                    put("mainPhotoUri", "https://example.com/photo.jpg")
                })
            })
        }

        val notif = SupabaseMappers.parseNotification(json)
        assertEquals(2L, notif.id)
        assertEquals(1L, notif.userId)
        assertEquals(2L, notif.counterpartUserId)
        assertEquals("Naya", notif.counterpartName)
        assertEquals(2L, notif.relatedInterestId)
        assertEquals(1L, notif.targetItemId)
        assertEquals("Kemeja linen krem", notif.targetItemName)
        assertEquals("https://example.com/photo.jpg", notif.counterpartPhotoUri)
        assertTrue(notif.canReplyToInterest)
    }

    @Test
    fun parseNotification_otherItemsInActiveMatch_doNotBlockNewInterest() {
        val notification = JSONObject("""
            {
              "id": 10, "userId": 1, "type": "INCOMING_INTEREST",
              "interests": {
                "id": 20, "senderUserId": 2, "status": "PENDING",
                "users": {"id": 2, "name": "Naya"},
                "clothing_items": {"id": 4, "status": "AVAILABLE", "isBarterAllowed": true}
              }
            }
        """.trimIndent())

        val available = SupabaseMappers.parseNotification(notification, activeItemIds = setOf(1L, 2L))
        assertTrue(available.canReplyToInterest)
        assertNull(available.disabledReasonText)

        val locked = SupabaseMappers.parseNotification(notification, activeItemIds = setOf(1L, 2L, 4L))
        assertFalse(locked.canReplyToInterest)
        assertEquals("Pakaian sedang terikat dalam Match aktif", locked.disabledReasonText)
    }

    @Test
    fun parseNotification_disabledReasonText_whenStatusNotPending() {
        val json = JSONObject().apply {
            put("id", 3L)
            put("userId", 1L)
            put("type", "INCOMING_INTEREST")
            put("title", "Tawaran Barter")
            put("body", "...")
            put("relatedInterestId", 3L)
            put("isRead", true)
            put("createdAt", 1730000031100L)
            put("interests", JSONObject().apply {
                put("id", 3L)
                put("senderUserId", 2L)
                put("status", "ACCEPTED_MATCHED")
                put("users", JSONObject().apply {
                    put("id", 2L)
                    put("name", "Naya")
                })
            })
        }

        val notif = SupabaseMappers.parseNotification(json)
        assertEquals("Sudah terhubung dalam Match", notif.disabledReasonText)
    }

    @Test
    fun parseOutgoingInterest_disabledOrDeletedItemIsNotFalselyLockedByOtherMatch() {
        val item = JSONObject().apply {
            put("id", 20L)
            put("ownerId", 2L)
            put("status", "AVAILABLE")
            put("isBarterAllowed", false)
        }
        val interest = JSONObject().apply {
            put("id", 10L)
            put("targetItemId", 20L)
            put("status", "INVALIDATED")
            put("clothing_items", item)
        }
        val disabledSummary = SupabaseMappers.parseOutgoingInterest(interest)
        assertFalse(disabledSummary.isLockedByOtherMatch)
        assertEquals(InterestStatus.INVALIDATED, disabledSummary.interestStatus)

        item.put("status", "HIDDEN_DELETED")
        assertFalse(SupabaseMappers.parseOutgoingInterest(interest).isLockedByOtherMatch)

        item.put("status", "AVAILABLE")
        item.put("isBarterAllowed", true)
        val reactivated = SupabaseMappers.parseOutgoingInterest(interest)
        assertFalse(reactivated.isLockedByOtherMatch)
        assertEquals(InterestStatus.INVALIDATED, reactivated.interestStatus)
        assertEquals(10L, reactivated.interestId)
        assertNull(reactivated.matchId)

        assertTrue(SupabaseMappers.parseOutgoingInterest(interest, activeItemIds = setOf(20L)).isLockedByOtherMatch)
        item.put("status", "EXCHANGED")
        assertTrue(SupabaseMappers.parseOutgoingInterest(interest).isLockedByOtherMatch)
    }

    @Test
    fun parseOutgoingInterest_retryDoesNotRewriteOldInterestStatus() {
        val old = JSONObject("""
            {"id": 10, "targetItemId": 20, "status": "INVALIDATED",
             "clothing_items": {"status": "AVAILABLE", "isBarterAllowed": true}}
        """.trimIndent())
        val pending = JSONObject(old.toString()).put("id", 11L).put("status", "PENDING")
        assertEquals(InterestStatus.INVALIDATED, SupabaseMappers.parseOutgoingInterest(old).interestStatus)
        assertEquals(InterestStatus.PENDING, SupabaseMappers.parseOutgoingInterest(pending).interestStatus)
        assertFalse(SupabaseMappers.parseOutgoingInterest(pending).isLockedByOtherMatch)
    }

    @Test
    fun parseOutgoingInterest_cancelledMatchWithOtherActiveItem_marksLockedByOtherMatch() {
        val interest = JSONObject("""
            {"id": 10, "targetItemId": 20, "status": "ACCEPTED_MATCHED",
             "clothing_items": {"status": "AVAILABLE", "isBarterAllowed": true},
             "matches": [{"id": 30, "status": "CANCELLED"}]}
        """.trimIndent())

        assertFalse(SupabaseMappers.parseOutgoingInterest(interest).isLockedByOtherMatch)
        assertTrue(SupabaseMappers.parseOutgoingInterest(interest, activeItemIds = setOf(20L)).isLockedByOtherMatch)
    }

    @Test
    fun parseOutgoingInterest_rpcMatchRemainsLinkedWhenItsItemIsReserved() {
        val interest = JSONObject("""
            {"id": 10, "targetItemId": 20, "status": "RESOLVED_BY_MATCH",
             "clothing_items": {"status": "AVAILABLE", "isBarterAllowed": true},
             "matches": [{"id": 30, "status": "ACTIVE"}]}
        """.trimIndent())

        val summary = SupabaseMappers.parseOutgoingInterest(interest, activeItemIds = setOf(20L))
        assertEquals(30L, summary.matchId)
        assertEquals(MatchStatus.ACTIVE, summary.matchStatus)
        assertFalse(summary.isLockedByOtherMatch)
    }
}
