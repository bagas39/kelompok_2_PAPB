package com.example.cloble.data.remote

import com.example.cloble.domain.model.ItemStatus
import org.json.JSONObject
import org.junit.Assert.assertEquals
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
}
