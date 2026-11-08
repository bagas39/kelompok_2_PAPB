package com.example.cloble.data

import android.content.Context
import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cloble.data.local.dao.CancelMatchDaoResult
import com.example.cloble.data.local.dao.CompleteMatchDaoResult
import com.example.cloble.data.local.dao.RespondInterestDaoResult
import com.example.cloble.data.local.dao.ReviewSubmitDaoResult
import com.example.cloble.data.local.dao.SendMessageDaoResult
import com.example.cloble.data.local.dao.SendInterestDaoResult
import com.example.cloble.data.local.dao.WardrobeMutationDaoResult
import com.example.cloble.data.local.db.CloBleDatabase
import com.example.cloble.data.local.entity.ClothingItemEntity
import com.example.cloble.data.local.entity.InterestEntity
import com.example.cloble.data.local.entity.SwipePassEntity
import com.example.cloble.data.local.entity.UserEntity
import com.example.cloble.data.repository.EntityMappers
import com.example.cloble.data.repository.LocalDiscoveryMatchRepository
import com.example.cloble.domain.model.InterestStatus
import com.example.cloble.domain.model.ItemStatus
import com.example.cloble.domain.model.MatchStatus
import com.example.cloble.domain.result.SendInterestResult
import com.example.cloble.domain.util.PasswordHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CloBleTransactionAndConcurrencyTest {

    private lateinit var db: CloBleDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, CloBleDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    private fun countInterests(): Int {
        val cursor = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM interests")
        return try {
            cursor.moveToFirst()
            cursor.getInt(0)
        } finally {
            cursor.close()
        }
    }

    private fun countNotifications(): Int {
        val cursor = db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM notifications")
        return try {
            cursor.moveToFirst()
            cursor.getInt(0)
        } finally {
            cursor.close()
        }
    }

    private suspend fun insertTestUser(
        name: String,
        email: String,
        city: String = "Bandung, Jawa Barat",
        lat: Double = -6.9175,
        lng: Double = 107.6191
    ): Long {
        val hash = PasswordHasher.hash("testpass123")
        return db.userDao().insertUser(
            UserEntity(
                name = name,
                email = email,
                passwordHash = hash,
                city = city,
                latitude = lat,
                longitude = lng,
                createdAt = System.currentTimeMillis()
            )
        )
    }

    private suspend fun insertTestItem(
        ownerId: Long,
        name: String,
        isBarterAllowed: Boolean = true,
        status: ItemStatus = ItemStatus.AVAILABLE
    ): Long {
        val now = System.currentTimeMillis()
        return db.clothingItemDao().insertClothing(
            ClothingItemEntity(
                ownerId = ownerId,
                name = name,
                description = "Deskripsi $name",
                category = "Atasan",
                size = "M",
                gender = "Uniseks",
                color = "Hitam",
                condition = "Baik",
                mainPhotoUri = null,
                isBarterAllowed = isBarterAllowed,
                status = status,
                createdAt = now,
                updatedAt = now
            )
        )
    }

    @Test
    fun tc01_sendInterest_happyPath() = runBlocking {
        val userA = insertTestUser("User A", "a@test.com")
        val userB = insertTestUser("User B", "b@test.com")

        insertTestItem(userA, "Item A1", isBarterAllowed = true)
        val itemB1 = insertTestItem(userB, "Item B1", isBarterAllowed = true)

        val now = System.currentTimeMillis()
        val result = db.interestMatchDao().sendInterest(userA, itemB1, now)
        assertTrue("Expected SendInterestDaoResult.Success", result is SendInterestDaoResult.Success)

        val success = result as SendInterestDaoResult.Success
        val interest = db.interestMatchDao().getInterestById(success.interestId)
        assertNotNull(interest)
        assertEquals(InterestStatus.PENDING, interest?.status)
        assertEquals(userA, interest?.senderUserId)
        assertEquals(userB, interest?.receiverUserId)

        val notifs = db.notificationDao().getNotifications(userB)
        assertEquals(1, notifs.size)
        assertEquals(interest?.id, notifs[0].relatedInterestId)
    }

    @Test
    fun tc02a_respondToInterest_happyPath() = runBlocking {
        val userA = insertTestUser("User A", "a@test.com")
        val userB = insertTestUser("User B", "b@test.com")

        val itemA1 = insertTestItem(userA, "Item A1", isBarterAllowed = true)
        val itemB1 = insertTestItem(userB, "Item B1", isBarterAllowed = true)

        val now = System.currentTimeMillis()
        val sendRes = db.interestMatchDao().sendInterest(userA, itemB1, now) as SendInterestDaoResult.Success
        val respondRes = db.interestMatchDao().respondToInterestAndCreateMatch(
            actorUserId = userB,
            interestId = sendRes.interestId,
            chosenSenderItemId = itemA1,
            nowMillis = now
        )
        assertTrue("Expected RespondInterestDaoResult.Success", respondRes is RespondInterestDaoResult.Success)

        val matchId = (respondRes as RespondInterestDaoResult.Success).matchId
        val match = db.interestMatchDao().getMatchById(matchId)
        assertNotNull(match)
        assertEquals(MatchStatus.ACTIVE, match?.status)
        assertEquals(itemB1, match?.user1ItemId)
        assertEquals(itemA1, match?.user2ItemId)

        val interest = db.interestMatchDao().getInterestById(sendRes.interestId)
        assertEquals(InterestStatus.ACCEPTED_MATCHED, interest?.status)
    }

    @Test
    fun tc02b_nonCrossMatch_requiresExplicitRespond() = runBlocking {
        val userA = insertTestUser("User A", "a@test.com")
        val userB = insertTestUser("User B", "b@test.com")

        val itemA1 = insertTestItem(userA, "Item A1", isBarterAllowed = true)
        val itemB1 = insertTestItem(userB, "Item B1", isBarterAllowed = true)

        val now = System.currentTimeMillis()
        val resA = db.interestMatchDao().sendInterest(userA, itemB1, now)
        val resB = db.interestMatchDao().sendInterest(userB, itemA1, now)

        assertTrue(resA is SendInterestDaoResult.Success)
        assertTrue(resB is SendInterestDaoResult.Success)

        val activeMatches = db.interestMatchDao().countActiveMatchesUsingEitherItem(itemA1, itemB1)
        assertEquals("Cross interest must NOT automatically form a match", 0, activeMatches)
    }

    @Test
    fun tc02c_duplicateMatchConflict_rejected() = runBlocking {
        val userA = insertTestUser("User A", "a@test.com")
        val userB = insertTestUser("User B", "b@test.com")
        val userC = insertTestUser("User C", "c@test.com")

        val itemA1 = insertTestItem(userA, "Item A1", isBarterAllowed = true)
        val itemB1 = insertTestItem(userB, "Item B1", isBarterAllowed = true)
        val itemC1 = insertTestItem(userC, "Item C1", isBarterAllowed = true)

        val now = System.currentTimeMillis()
        val intA = (db.interestMatchDao().sendInterest(userA, itemB1, now) as SendInterestDaoResult.Success).interestId
        val intC = (db.interestMatchDao().sendInterest(userC, itemB1, now) as SendInterestDaoResult.Success).interestId

        val respA = db.interestMatchDao().respondToInterestAndCreateMatch(userB, intA, itemA1, now)
        assertTrue(respA is RespondInterestDaoResult.Success)

        val respC = db.interestMatchDao().respondToInterestAndCreateMatch(userB, intC, itemC1, now)
        assertEquals(RespondInterestDaoResult.ItemAlreadyInActiveMatch, respC)
    }

    @Test
    fun tc02d_barterNotAllowed_rejected() = runBlocking {
        val userA = insertTestUser("User A", "a@test.com")
        val userB = insertTestUser("User B", "b@test.com")

        insertTestItem(userA, "Item A1", isBarterAllowed = true)
        val itemBNoBarter = insertTestItem(userB, "Item B Not Allowed", isBarterAllowed = false)

        val now = System.currentTimeMillis()
        val res = db.interestMatchDao().sendInterest(userA, itemBNoBarter, now)
        assertEquals(SendInterestDaoResult.TargetItemNotEligible, res)
    }

    @Test
    fun tc02e_zeroBarterableItems_rejected() = runBlocking {
        val userA = insertTestUser("User A", "a@test.com")
        val userB = insertTestUser("User B", "b@test.com")

        insertTestItem(userA, "Item A", isBarterAllowed = false)
        val itemB1 = insertTestItem(userB, "Item B1", isBarterAllowed = true)

        val now = System.currentTimeMillis()
        val res = db.interestMatchDao().sendInterest(userA, itemB1, now)
        assertEquals(SendInterestDaoResult.RequiresBarterableItemInWardrobe, res)
    }

    @Test
    fun tc08_strictOneShotAcrossAllStatuses() = runBlocking {
        val userA = insertTestUser("User A", "a@test.com")
        val userB = insertTestUser("User B", "b@test.com")

        val itemA1 = insertTestItem(userA, "Item A1", isBarterAllowed = true)
        val itemB1 = insertTestItem(userB, "Item B1", isBarterAllowed = true)

        val now = System.currentTimeMillis()

        val beforeOwnInterests = countInterests()
        val beforeOwnNotifs = countNotifications()
        val ownItemRes = db.interestMatchDao().sendInterest(userA, itemA1, now)
        assertEquals(SendInterestDaoResult.CannotInterestOwnItem, ownItemRes)
        assertEquals(beforeOwnInterests, countInterests())
        assertEquals(beforeOwnNotifs, countNotifications())

        val firstRes = db.interestMatchDao().sendInterest(userA, itemB1, now)
        assertTrue(firstRes is SendInterestDaoResult.Success)
        val interestId = (firstRes as SendInterestDaoResult.Success).interestId

        val baseInterests = countInterests()
        val baseNotifs = countNotifications()

        val pendingRes = db.interestMatchDao().sendInterest(userA, itemB1, now)
        assertEquals(SendInterestDaoResult.AlreadyInterested(InterestStatus.PENDING), pendingRes)
        assertEquals(baseInterests, countInterests())
        assertEquals(baseNotifs, countNotifications())

        db.interestMatchDao().markInterestStatus(interestId, InterestStatus.ACCEPTED_MATCHED, now)
        val acceptedRes = db.interestMatchDao().sendInterest(userA, itemB1, now)
        assertEquals(SendInterestDaoResult.AlreadyInterested(InterestStatus.ACCEPTED_MATCHED), acceptedRes)
        assertEquals(baseInterests, countInterests())
        assertEquals(baseNotifs, countNotifications())

        db.interestMatchDao().markInterestStatus(interestId, InterestStatus.RESOLVED_BY_MATCH, now)
        val resolvedRes = db.interestMatchDao().sendInterest(userA, itemB1, now)
        assertEquals(SendInterestDaoResult.AlreadyInterested(InterestStatus.RESOLVED_BY_MATCH), resolvedRes)
        assertEquals(baseInterests, countInterests())
        assertEquals(baseNotifs, countNotifications())

        db.interestMatchDao().markInterestStatus(interestId, InterestStatus.DECLINED, now)
        val declinedRes = db.interestMatchDao().sendInterest(userA, itemB1, now)
        assertEquals(SendInterestDaoResult.AlreadyInterested(InterestStatus.DECLINED), declinedRes)
        assertEquals(baseInterests, countInterests())
        assertEquals(baseNotifs, countNotifications())

        db.interestMatchDao().markInterestStatus(interestId, InterestStatus.INVALIDATED, now)
        val invalidatedRes = db.interestMatchDao().sendInterest(userA, itemB1, now)
        assertEquals(SendInterestDaoResult.AlreadyInterested(InterestStatus.INVALIDATED), invalidatedRes)
        assertEquals(baseInterests, countInterests())
        assertEquals(baseNotifs, countNotifications())
    }

    @Test
    fun tc08_existingInterestPrecedence_overAllConditions() = runBlocking {
        val userA = insertTestUser("User A", "a@test.com")
        val userB = insertTestUser("User B", "b@test.com")
        val userC = insertTestUser("User C", "c@test.com")

        val itemA = insertTestItem(userA, "Item A", isBarterAllowed = true)
        val itemB = insertTestItem(userB, "Item B", isBarterAllowed = true)
        val itemC = insertTestItem(userC, "Item C", isBarterAllowed = true)

        val now = System.currentTimeMillis()

        val firstRes = db.interestMatchDao().sendInterest(userA, itemB, now)
        assertTrue(firstRes is SendInterestDaoResult.Success)

        val expectedInterests = countInterests()
        val expectedNotifs = countNotifications()

        db.clothingItemDao().updateClothingProtected(
            itemId = itemA,
            actorUserId = userA,
            name = "Item A Non-Barter",
            description = "Desc",
            category = "Atasan",
            size = "M",
            gender = "Uniseks",
            color = "Hitam",
            condition = "Baik",
            mainPhotoUri = null,
            detailPhotoUri = null,
            extraPhotoUri = null,
            isBarterAllowed = false,
            nowMillis = now
        )
        val resZeroBarter = db.interestMatchDao().sendInterest(userA, itemB, now)
        assertEquals(SendInterestDaoResult.AlreadyInterested(InterestStatus.PENDING), resZeroBarter)
        assertEquals(expectedInterests, countInterests())
        assertEquals(expectedNotifs, countNotifications())

        db.clothingItemDao().updateClothingProtected(
            itemId = itemA,
            actorUserId = userA,
            name = "Item A Barter",
            description = "Desc",
            category = "Atasan",
            size = "M",
            gender = "Uniseks",
            color = "Hitam",
            condition = "Baik",
            mainPhotoUri = null,
            detailPhotoUri = null,
            extraPhotoUri = null,
            isBarterAllowed = true,
            nowMillis = now
        )

        db.clothingItemDao().softDeleteClothingProtected(itemB, userB, now)
        val resDeleted = db.interestMatchDao().sendInterest(userA, itemB, now)
        assertEquals(SendInterestDaoResult.AlreadyInterested(InterestStatus.INVALIDATED), resDeleted)
        assertEquals(expectedInterests, countInterests())
        assertEquals(expectedNotifs, countNotifications())

        db.interestMatchDao().updateItemsStatus(listOf(itemB), ItemStatus.EXCHANGED, now)
        val resExchanged = db.interestMatchDao().sendInterest(userA, itemB, now)
        assertEquals(SendInterestDaoResult.AlreadyInterested(InterestStatus.INVALIDATED), resExchanged)
        assertEquals(expectedInterests, countInterests())
        assertEquals(expectedNotifs, countNotifications())

        db.interestMatchDao().updateItemsStatus(listOf(itemB), ItemStatus.AVAILABLE, now)
        db.interestMatchDao().insertMatch(
            com.example.cloble.data.local.entity.MatchEntity(
                sourceInterestId = (firstRes as SendInterestDaoResult.Success).interestId,
                user1Id = userB,
                user1ItemId = itemB,
                user2Id = userC,
                user2ItemId = itemC,
                status = MatchStatus.ACTIVE,
                meetingLocation = null,
                createdAt = now,
                updatedAt = now
            )
        )
        val resLocked = db.interestMatchDao().sendInterest(userA, itemB, now)
        assertEquals(SendInterestDaoResult.AlreadyInterested(InterestStatus.INVALIDATED), resLocked)
        assertEquals(expectedInterests, countInterests())
        assertEquals(expectedNotifs, countNotifications())
    }

    @Test
    fun tc08_senderNotFound_daoAndRepository() = runBlocking {
        val userB = insertTestUser("User B", "b@test.com")
        val itemB = insertTestItem(userB, "Item B", isBarterAllowed = true)
        val nonExistentUser = 99999L
        val now = System.currentTimeMillis()

        val beforeInterests = countInterests()
        val beforeNotifs = countNotifications()

        val daoRes = db.interestMatchDao().sendInterest(nonExistentUser, itemB, now)
        assertEquals(SendInterestDaoResult.SenderNotFound, daoRes)
        assertEquals(beforeInterests, countInterests())
        assertEquals(beforeNotifs, countNotifications())

        val repo = LocalDiscoveryMatchRepository(
            discoveryDao = db.discoveryDao(),
            interestMatchDao = db.interestMatchDao(),
            notificationDao = db.notificationDao(),
            clothingDao = db.clothingItemDao(),
            userDao = db.userDao(),
            reviewDao = db.reviewDao()
        )
        val repoRes = repo.sendInterest(nonExistentUser, itemB)
        assertEquals(SendInterestResult.InvalidOrMissingSenderSession, repoRes)
        assertEquals(beforeInterests, countInterests())
        assertEquals(beforeNotifs, countNotifications())
    }

    @Test
    fun tc08_discoveryFeedExcludesAllInterestStatuses() = runBlocking {
        val userA = insertTestUser("User A", "a@test.com")
        val userB = insertTestUser("User B", "b@test.com")
        insertTestItem(userA, "Item A", isBarterAllowed = true)
        val itemB = insertTestItem(userB, "Item B", isBarterAllowed = true)

        val now = System.currentTimeMillis()

        val initialFeed = db.discoveryDao().getDiscoverCandidatesFlow(userA).first()
        assertTrue("Item B harus muncul di feed sebelum ada interest", initialFeed.any { it.item.id == itemB })

        val statuses = listOf(
            InterestStatus.PENDING,
            InterestStatus.ACCEPTED_MATCHED,
            InterestStatus.RESOLVED_BY_MATCH,
            InterestStatus.DECLINED,
            InterestStatus.INVALIDATED
        )

        for (status in statuses) {
            val intId = db.interestMatchDao().insertInterest(
                InterestEntity(
                    senderUserId = userA,
                    receiverUserId = userB,
                    targetItemId = itemB,
                    status = status,
                    createdAt = now,
                    respondedAt = now
                )
            )

            val feed = db.discoveryDao().getDiscoverCandidatesFlow(userA).first()
            assertFalse(
                "Item B tidak boleh muncul di Discover feed jika sudah ada interest dengan status $status",
                feed.any { it.item.id == itemB }
            )

            db.openHelper.writableDatabase.execSQL("DELETE FROM interests WHERE id = $intId")
        }
    }

    @Test
    fun tc09a_cancelMatch_releasesLock() = runBlocking {
        val userA = insertTestUser("User A", "a@test.com")
        val userB = insertTestUser("User B", "b@test.com")
        val itemA1 = insertTestItem(userA, "Item A1", isBarterAllowed = true)
        val itemB1 = insertTestItem(userB, "Item B1", isBarterAllowed = true)

        val now = System.currentTimeMillis()
        val intId = (db.interestMatchDao().sendInterest(userA, itemB1, now) as SendInterestDaoResult.Success).interestId
        val matchId = (db.interestMatchDao().respondToInterestAndCreateMatch(userB, intId, itemA1, now) as RespondInterestDaoResult.Success).matchId

        assertEquals(1, db.clothingItemDao().countActiveMatchesUsingItem(itemA1))

        val cancelRes = db.interestMatchDao().cancelMatch(matchId, userA, now)
        assertEquals(CancelMatchDaoResult.Success, cancelRes)

        val match = db.interestMatchDao().getMatchById(matchId)
        assertEquals(MatchStatus.CANCELLED, match?.status)
        assertEquals(0, db.clothingItemDao().countActiveMatchesUsingItem(itemA1))
        assertEquals(0, db.clothingItemDao().countActiveMatchesUsingItem(itemB1))
    }

    @Test
    fun tc09b1_completeMatch_marksItemsExchanged() = runBlocking {
        val userA = insertTestUser("User A", "a@test.com")
        val userB = insertTestUser("User B", "b@test.com")
        val itemA1 = insertTestItem(userA, "Item A1", isBarterAllowed = true)
        val itemB1 = insertTestItem(userB, "Item B1", isBarterAllowed = true)

        val now = System.currentTimeMillis()
        val intId = (db.interestMatchDao().sendInterest(userA, itemB1, now) as SendInterestDaoResult.Success).interestId
        val matchId = (db.interestMatchDao().respondToInterestAndCreateMatch(userB, intId, itemA1, now) as RespondInterestDaoResult.Success).matchId

        val compRes = db.interestMatchDao().completeMatchExchange(matchId, userA, "Balai Kota", now)
        assertEquals(CompleteMatchDaoResult.Success, compRes)

        val itemA = db.clothingItemDao().getClothingById(itemA1)
        val itemB = db.clothingItemDao().getClothingById(itemB1)
        assertEquals(ItemStatus.EXCHANGED, itemA?.status)
        assertEquals(ItemStatus.EXCHANGED, itemB?.status)
    }

    @Test
    fun tc09b2_chatReadOnly_onCancelledOrCompleted() = runBlocking {
        val userA = insertTestUser("User A", "a@test.com")
        val userB = insertTestUser("User B", "b@test.com")
        val itemA1 = insertTestItem(userA, "Item A1", isBarterAllowed = true)
        val itemB1 = insertTestItem(userB, "Item B1", isBarterAllowed = true)

        val now = System.currentTimeMillis()
        val intId = (db.interestMatchDao().sendInterest(userA, itemB1, now) as SendInterestDaoResult.Success).interestId
        val matchId = (db.interestMatchDao().respondToInterestAndCreateMatch(userB, intId, itemA1, now) as RespondInterestDaoResult.Success).matchId

        val sendActive = db.chatDao().sendMessageProtected(matchId, userA, "Halo, barang masih ada?", now)
        assertTrue(sendActive is SendMessageDaoResult.Success)

        db.interestMatchDao().cancelMatch(matchId, userA, now)

        val sendAfterCancel = db.chatDao().sendMessageProtected(matchId, userA, "Pesan setelah batal", now)
        assertEquals(SendMessageDaoResult.ChatReadOnly, sendAfterCancel)
    }

    @Test
    fun tc10_reviewSubmission_enforcements() = runBlocking {
        val userA = insertTestUser("User A", "a@test.com")
        val userB = insertTestUser("User B", "b@test.com")
        val itemA1 = insertTestItem(userA, "Item A1", isBarterAllowed = true)
        val itemB1 = insertTestItem(userB, "Item B1", isBarterAllowed = true)

        val now = System.currentTimeMillis()
        val intId = (db.interestMatchDao().sendInterest(userA, itemB1, now) as SendInterestDaoResult.Success).interestId
        val matchId = (db.interestMatchDao().respondToInterestAndCreateMatch(userB, intId, itemA1, now) as RespondInterestDaoResult.Success).matchId

        val revActive = db.reviewDao().submitReviewProtected(matchId, userA, 5, "Sangat baik!", now)
        assertEquals(ReviewSubmitDaoResult.MatchNotCompleted, revActive)

        db.interestMatchDao().completeMatchExchange(matchId, userA, "Balai Kota", now)

        val revSuccess = db.reviewDao().submitReviewProtected(matchId, userA, 5, "Sangat baik!", now)
        assertTrue(revSuccess is ReviewSubmitDaoResult.Success)

        val revDup = db.reviewDao().submitReviewProtected(matchId, userA, 4, "Ulasan kedua", now)
        assertEquals(ReviewSubmitDaoResult.AlreadyReviewed, revDup)
    }

    @Test
    fun tc11_concurrencySafeMatchCreation() = runBlocking {
        val userA = insertTestUser("User A", "a@test.com")
        val userB = insertTestUser("User B", "b@test.com")
        val userC = insertTestUser("User C", "c@test.com")

        val itemA1 = insertTestItem(userA, "Item A1", isBarterAllowed = true)
        val itemB1 = insertTestItem(userB, "Item B1", isBarterAllowed = true)
        val itemC1 = insertTestItem(userC, "Item C1", isBarterAllowed = true)

        val now = System.currentTimeMillis()
        val intA = (db.interestMatchDao().sendInterest(userA, itemB1, now) as SendInterestDaoResult.Success).interestId
        val intC = (db.interestMatchDao().sendInterest(userC, itemB1, now) as SendInterestDaoResult.Success).interestId

        val taskA = async(Dispatchers.Default) {
            db.interestMatchDao().respondToInterestAndCreateMatch(userB, intA, itemA1, now)
        }
        val taskC = async(Dispatchers.Default) {
            db.interestMatchDao().respondToInterestAndCreateMatch(userB, intC, itemC1, now)
        }

        val results = awaitAll(taskA, taskC)
        val successCount = results.count { it is RespondInterestDaoResult.Success }
        val failureCount = results.count { it !is RespondInterestDaoResult.Success }

        assertEquals("Exactly one match must succeed", 1, successCount)
        assertEquals("Competing match must fail safely", 1, failureCount)
    }

    @Test
    fun tc12_softDeleteProtection() = runBlocking {
        val userA = insertTestUser("User A", "a@test.com")
        val userB = insertTestUser("User B", "b@test.com")
        val itemA1 = insertTestItem(userA, "Item A1", isBarterAllowed = true)
        val itemB1 = insertTestItem(userB, "Item B1", isBarterAllowed = true)

        val now = System.currentTimeMillis()
        val intId = (db.interestMatchDao().sendInterest(userA, itemB1, now) as SendInterestDaoResult.Success).interestId
        val matchId = (db.interestMatchDao().respondToInterestAndCreateMatch(userB, intId, itemA1, now) as RespondInterestDaoResult.Success).matchId

        val delLocked = db.clothingItemDao().softDeleteClothingProtected(itemA1, userA, now)
        assertEquals(WardrobeMutationDaoResult.LockedInActiveMatch, delLocked)

        db.interestMatchDao().cancelMatch(matchId, userA, now)
        val delUnlocked = db.clothingItemDao().softDeleteClothingProtected(itemA1, userA, now)
        assertEquals(WardrobeMutationDaoResult.Success(itemA1), delUnlocked)

        val itemAfterDel = db.clothingItemDao().getClothingById(itemA1)
        assertEquals(ItemStatus.HIDDEN_DELETED, itemAfterDel?.status)
    }

    @Test
    fun tc13_lowCredibilityBadgeCalculation() = runBlocking {
        val userA = insertTestUser("User A", "a@test.com")
        val userB = insertTestUser("User B", "b@test.com")
        val itemA1 = insertTestItem(userA, "Item A1", isBarterAllowed = true)
        val itemB1 = insertTestItem(userB, "Item B1", isBarterAllowed = true)

        val now = System.currentTimeMillis()
        val intId = (db.interestMatchDao().sendInterest(userA, itemB1, now) as SendInterestDaoResult.Success).interestId
        val matchId = (db.interestMatchDao().respondToInterestAndCreateMatch(userB, intId, itemA1, now) as RespondInterestDaoResult.Success).matchId
        db.interestMatchDao().completeMatchExchange(matchId, userA, "Balai Kota", now)

        db.reviewDao().submitReviewProtected(matchId, userA, 2, "Kecewa, kondisi buruk", now)

        val avg = db.reviewDao().getAverageRatingForUser(userB) ?: 0.0
        val count = db.reviewDao().getReviewCountForUser(userB)
        assertEquals(2.0, avg, 0.01)
        assertEquals(1, count)

        val userEntity = db.userDao().getUserById(userB)!!
        val userProfile = EntityMappers.toDomainUserProfile(userEntity, avg, count)
        assertTrue("User with rating < 3.0 must have isLowCredibility = true", userProfile.isLowCredibility)
    }

    @Test
    fun tc14_sendInterest_rollbackOnNotificationFailure() = runBlocking {
        val userA = insertTestUser("User A", "a@test.com")
        val userB = insertTestUser("User B", "b@test.com")
        insertTestItem(userA, "Item A", isBarterAllowed = true)
        val itemB = insertTestItem(userB, "Item B", isBarterAllowed = true)
        val now = System.currentTimeMillis()

        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_notif_tc14 BEFORE INSERT ON notifications BEGIN SELECT RAISE(ABORT, 'Simulated notification failure'); END;"
        )

        try {
            db.interestMatchDao().sendInterest(userA, itemB, now)
            fail("sendInterest harus melempar SQLiteException saat trigger notification aktif")
        } catch (e: SQLiteException) {
            assertTrue("Exception message harus berisi failure trigger", e.message?.contains("Simulated notification failure") == true)
        } finally {
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS fail_notif_tc14")
        }

        assertEquals("Interest harus di-rollback (0 baris)", 0, countInterests())
        assertEquals("Notification harus tetap 0", 0, countNotifications())
    }

    @Test
    fun tc15_respondToInterest_rollbackOnNotificationFailure() = runBlocking {
        val userA = insertTestUser("User A", "a@test.com")
        val userB = insertTestUser("User B", "b@test.com")
        val itemA = insertTestItem(userA, "Item A", isBarterAllowed = true)
        val itemB = insertTestItem(userB, "Item B", isBarterAllowed = true)
        val now = System.currentTimeMillis()

        val intRes = db.interestMatchDao().sendInterest(userA, itemB, now)
        assertTrue(intRes is SendInterestDaoResult.Success)
        val interestId = (intRes as SendInterestDaoResult.Success).interestId

        val beforeMatchCount = db.interestMatchDao().countActiveMatchesUsingItem(itemB)
        assertEquals(0, beforeMatchCount)

        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_notif_tc15 BEFORE INSERT ON notifications BEGIN SELECT RAISE(ABORT, 'Simulated notification failure'); END;"
        )

        try {
            db.interestMatchDao().respondToInterestAndCreateMatch(userB, interestId, itemA, now)
            fail("respondToInterest harus melempar SQLiteException saat notification trigger aktif")
        } catch (e: SQLiteException) {
            assertTrue(e.message?.contains("Simulated notification failure") == true)
        } finally {
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS fail_notif_tc15")
        }

        val matchAfter = db.interestMatchDao().countActiveMatchesUsingItem(itemB)
        assertEquals("Match tidak boleh terbentuk setelah rollback", 0, matchAfter)
        val interestAfter = db.interestMatchDao().getInterestById(interestId)
        assertEquals("Interest harus tetap PENDING setelah rollback", InterestStatus.PENDING, interestAfter?.status)
    }

    @Test
    fun tc16_cancelMatch_rollbackOnNotificationFailure() = runBlocking {
        val userA = insertTestUser("User A", "a@test.com")
        val userB = insertTestUser("User B", "b@test.com")
        val itemA = insertTestItem(userA, "Item A", isBarterAllowed = true)
        val itemB = insertTestItem(userB, "Item B", isBarterAllowed = true)
        val now = System.currentTimeMillis()

        val intId = (db.interestMatchDao().sendInterest(userA, itemB, now) as SendInterestDaoResult.Success).interestId
        val matchId = (db.interestMatchDao().respondToInterestAndCreateMatch(userB, intId, itemA, now) as RespondInterestDaoResult.Success).matchId

        val matchBefore = db.interestMatchDao().getMatchById(matchId)
        assertEquals(MatchStatus.ACTIVE, matchBefore?.status)

        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_notif_tc16 BEFORE INSERT ON notifications BEGIN SELECT RAISE(ABORT, 'Simulated notification failure'); END;"
        )

        try {
            db.interestMatchDao().cancelMatch(matchId, userA, now)
            fail("cancelMatch harus melempar SQLiteException saat trigger aktif")
        } catch (e: SQLiteException) {
            assertTrue(e.message?.contains("Simulated notification failure") == true)
        } finally {
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS fail_notif_tc16")
        }

        val matchAfter = db.interestMatchDao().getMatchById(matchId)
        assertEquals("Match harus tetap ACTIVE jika notifikasi pembatalan gagal", MatchStatus.ACTIVE, matchAfter?.status)
    }

    @Test
    fun tc17_completeMatch_rollbackOnNotificationFailure() = runBlocking {
        val userA = insertTestUser("User A", "a@test.com")
        val userB = insertTestUser("User B", "b@test.com")
        val itemA = insertTestItem(userA, "Item A", isBarterAllowed = true)
        val itemB = insertTestItem(userB, "Item B", isBarterAllowed = true)
        val now = System.currentTimeMillis()

        val intId = (db.interestMatchDao().sendInterest(userA, itemB, now) as SendInterestDaoResult.Success).interestId
        val matchId = (db.interestMatchDao().respondToInterestAndCreateMatch(userB, intId, itemA, now) as RespondInterestDaoResult.Success).matchId

        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_notif_tc17 BEFORE INSERT ON notifications BEGIN SELECT RAISE(ABORT, 'Simulated notification failure'); END;"
        )

        try {
            db.interestMatchDao().completeMatchExchange(matchId, userA, "BIP", now)
            fail("completeMatchExchange harus melempar SQLiteException saat trigger aktif")
        } catch (e: SQLiteException) {
            assertTrue(e.message?.contains("Simulated notification failure") == true)
        } finally {
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS fail_notif_tc17")
        }

        val matchAfter = db.interestMatchDao().getMatchById(matchId)
        assertEquals("Match harus tetap ACTIVE jika penyelesaian rollback", MatchStatus.ACTIVE, matchAfter?.status)

        val itemAAfter = db.clothingItemDao().getClothingById(itemA)
        val itemBAfter = db.clothingItemDao().getClothingById(itemB)
        assertEquals("Item A harus tetap AVAILABLE", ItemStatus.AVAILABLE, itemAAfter?.status)
        assertEquals("Item B harus tetap AVAILABLE", ItemStatus.AVAILABLE, itemBAfter?.status)
    }

    @Test
    fun tc18_convertSwipePassToInterest_happyPath() = runBlocking {
        val userA = insertTestUser("User A", "a_p6@test.com")
        val userB = insertTestUser("User B", "b_p6@test.com")
        insertTestItem(userA, "Item A Barterable", isBarterAllowed = true)
        val itemB = insertTestItem(userB, "Item B Target", isBarterAllowed = true)
        val now = System.currentTimeMillis()

        db.discoveryDao().insertSwipePass(
            SwipePassEntity(userId = userA, targetItemId = itemB, createdAt = now)
        )
        assertEquals(1, db.discoveryDao().hasUserPassedItem(userA, itemB))

        val result = db.interestMatchDao().convertSwipePassToInterest(userA, itemB, now)
        assertTrue("Konversi harus berhasil", result is SendInterestDaoResult.Success)

        assertEquals(0, db.discoveryDao().hasUserPassedItem(userA, itemB))

        val interest = db.interestMatchDao().getInterestBySenderAndTarget(userA, itemB)
        assertNotNull(interest)
        assertEquals(InterestStatus.PENDING, interest?.status)

        val notifs = db.notificationDao().getNotifications(userB)
        assertTrue(notifs.any { it.relatedInterestId == interest?.id })
    }

    @Test
    fun tc19_convertSwipePassToInterest_rollbackOnFailureTrigger() = runBlocking {
        val userA = insertTestUser("User A", "a_tc19@test.com")
        val userB = insertTestUser("User B", "b_tc19@test.com")
        insertTestItem(userA, "Item A", isBarterAllowed = true)
        val itemB = insertTestItem(userB, "Item B", isBarterAllowed = true)
        val now = System.currentTimeMillis()

        db.discoveryDao().insertSwipePass(
            SwipePassEntity(userId = userA, targetItemId = itemB, createdAt = now)
        )
        assertEquals(1, db.discoveryDao().hasUserPassedItem(userA, itemB))

        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_notif_tc19 BEFORE INSERT ON notifications BEGIN SELECT RAISE(ABORT, 'Simulated notif error tc19'); END;"
        )

        try {
            db.interestMatchDao().convertSwipePassToInterest(userA, itemB, now)
            fail("convertSwipePassToInterest harus melempar SQLiteException saat trigger aktif")
        } catch (e: SQLiteException) {
            assertTrue(e.message?.contains("Simulated notif error tc19") == true)
        } finally {
            db.openHelper.writableDatabase.execSQL("DROP TRIGGER IF EXISTS fail_notif_tc19")
        }

        assertEquals("SwipePass harus tetap ada setelah transaksi rollback", 1, db.discoveryDao().hasUserPassedItem(userA, itemB))
        val interest = db.interestMatchDao().getInterestBySenderAndTarget(userA, itemB)
        assertNull("Interest tidak boleh ada jika transaksi rollback", interest)
    }

    @Test
    fun tc20_convertSwipePassToInterest_failsWhenNoBarterItems_preservesPass() = runBlocking {
        val userA = insertTestUser("User A", "a_tc20@test.com")
        val userB = insertTestUser("User B", "b_tc20@test.com")

        insertTestItem(userA, "Item A Personal", isBarterAllowed = false)
        val itemB = insertTestItem(userB, "Item B Target", isBarterAllowed = true)
        val now = System.currentTimeMillis()

        db.discoveryDao().insertSwipePass(
            SwipePassEntity(userId = userA, targetItemId = itemB, createdAt = now)
        )

        val result = db.interestMatchDao().convertSwipePassToInterest(userA, itemB, now)
        assertEquals(SendInterestDaoResult.RequiresBarterableItemInWardrobe, result)

        assertEquals(1, db.discoveryDao().hasUserPassedItem(userA, itemB))
    }

    @Test
    fun tc21_convertSwipePassToInterest_failsWhenAlreadyInterested_preservesPass() = runBlocking {
        val userA = insertTestUser("User A", "a_tc21@test.com")
        val userB = insertTestUser("User B", "b_tc21@test.com")
        insertTestItem(userA, "Item A", isBarterAllowed = true)
        val itemB = insertTestItem(userB, "Item B Target", isBarterAllowed = true)
        val now = System.currentTimeMillis()

        val firstResult = db.interestMatchDao().sendInterest(userA, itemB, now)
        assertTrue(firstResult is SendInterestDaoResult.Success)

        db.discoveryDao().insertSwipePass(
            SwipePassEntity(userId = userA, targetItemId = itemB, createdAt = now)
        )

        val result = db.interestMatchDao().convertSwipePassToInterest(userA, itemB, now)
        assertTrue(result is SendInterestDaoResult.AlreadyInterested)
        assertEquals(InterestStatus.PENDING, (result as SendInterestDaoResult.AlreadyInterested).existingStatus)

        assertEquals(1, db.discoveryDao().hasUserPassedItem(userA, itemB))
    }

    @Test
    fun tc22_restorePassToDiscover_restoresCandidateInFlow() = runBlocking {
        val userA = insertTestUser("User A", "a_tc22@test.com")
        val userB = insertTestUser("User B", "b_tc22@test.com")
        val itemB = insertTestItem(userB, "Item B Target", isBarterAllowed = true)

        val initialCandidates = db.discoveryDao().getDiscoverCandidatesFlow(userA).first()
        assertTrue(initialCandidates.any { it.item.id == itemB })

        db.discoveryDao().insertSwipePass(
            SwipePassEntity(userId = userA, targetItemId = itemB, createdAt = System.currentTimeMillis())
        )
        val passedCandidates = db.discoveryDao().getDiscoverCandidatesFlow(userA).first()
        assertFalse("Item yang dilewati harus hilang dari kandidat Discover", passedCandidates.any { it.item.id == itemB })

        val deletedCount = db.discoveryDao().deleteSwipePass(userA, itemB)
        assertEquals(1, deletedCount)

        val restoredCandidates = db.discoveryDao().getDiscoverCandidatesFlow(userA).first()
        assertTrue("Item harus muncul kembali di kandidat Discover setelah dipulihkan", restoredCandidates.any { it.item.id == itemB })
    }

    @Test
    fun tc23_getOutgoingInterestsFlow_and_getPassedClothingFlow() = runBlocking {
        val userA = insertTestUser("User A", "a_tc23@test.com")
        val userB = insertTestUser("User B", "b_tc23@test.com")
        val itemA = insertTestItem(userA, "Item A", isBarterAllowed = true)
        val itemB = insertTestItem(userB, "Item B", isBarterAllowed = true)
        val itemC = insertTestItem(userB, "Item C", isBarterAllowed = true)
        val now = System.currentTimeMillis()

        val intResult = db.interestMatchDao().sendInterest(userA, itemB, now)
        val intId = (intResult as SendInterestDaoResult.Success).interestId

        val outgoingList = db.interestMatchDao().getOutgoingInterestsFlow(userA).first()
        assertEquals(1, outgoingList.size)
        assertEquals(intId, outgoingList[0].interestId)
        assertEquals("Item B", outgoingList[0].targetItemName)
        assertEquals(InterestStatus.PENDING, outgoingList[0].interestStatus)
        assertNull(outgoingList[0].matchId)

        db.discoveryDao().insertSwipePass(
            SwipePassEntity(userId = userA, targetItemId = itemC, createdAt = now)
        )
        val passedList = db.discoveryDao().getPassedClothingFlow(userA).first()
        assertEquals(1, passedList.size)
        assertEquals(itemC, passedList[0].clothingId)
        assertEquals("Item C", passedList[0].clothingName)
        assertEquals(userB, passedList[0].ownerId)

        val matchResult = db.interestMatchDao().respondToInterestAndCreateMatch(userB, intId, itemA, now)
        val matchId = (matchResult as RespondInterestDaoResult.Success).matchId

        val updatedOutgoing = db.interestMatchDao().getOutgoingInterestsFlow(userA).first()
        assertEquals(1, updatedOutgoing.size)
        assertEquals(InterestStatus.ACCEPTED_MATCHED, updatedOutgoing[0].interestStatus)
        assertEquals(matchId, updatedOutgoing[0].matchId)
        assertEquals(MatchStatus.ACTIVE, updatedOutgoing[0].matchStatus)
    }
}
