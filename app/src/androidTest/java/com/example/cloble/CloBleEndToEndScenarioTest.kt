package com.example.cloble

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cloble.data.local.db.CloBleDatabase
import com.example.cloble.data.local.session.SessionPreferencesDataSource
import com.example.cloble.data.local.storage.LocalPhotoStorageManager
import com.example.cloble.data.repository.LocalAuthRepository
import com.example.cloble.data.repository.LocalChatRepository
import com.example.cloble.data.repository.LocalDiscoveryMatchRepository
import com.example.cloble.data.repository.LocalProfileReviewRepository
import com.example.cloble.data.repository.LocalWardrobeRepository
import com.example.cloble.data.seed.PrepopulateDemoData
import com.example.cloble.domain.model.ClothingDraft
import com.example.cloble.domain.model.InterestStatus
import com.example.cloble.domain.model.ItemStatus
import com.example.cloble.domain.model.MatchStatus
import com.example.cloble.domain.repository.AuthRepository
import com.example.cloble.domain.repository.ChatRepository
import com.example.cloble.domain.repository.DiscoveryMatchRepository
import com.example.cloble.domain.repository.ProfileReviewRepository
import com.example.cloble.domain.repository.WardrobeRepository
import com.example.cloble.domain.result.AuthResult
import com.example.cloble.domain.result.CancelMatchResult
import com.example.cloble.domain.result.CompleteMatchResult
import com.example.cloble.domain.result.RespondInterestResult
import com.example.cloble.domain.result.ReviewSubmitResult
import com.example.cloble.domain.result.SendInterestResult
import com.example.cloble.domain.result.SendMessageResult
import com.example.cloble.domain.result.WardrobeMutationResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CloBleEndToEndScenarioTest {

    private lateinit var db: CloBleDatabase
    private lateinit var authRepository: AuthRepository
    private lateinit var wardrobeRepository: WardrobeRepository
    private lateinit var discoveryMatchRepository: DiscoveryMatchRepository
    private lateinit var chatRepository: ChatRepository
    private lateinit var profileReviewRepository: ProfileReviewRepository

    private var rakaId: Long = 0L
    private var nayaId: Long = 0L
    private var bagasId: Long = 0L
    private var dindaId: Long = 0L

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, CloBleDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        val sessionPrefs = SessionPreferencesDataSource(context)
        val photoManager = LocalPhotoStorageManager(context)

        authRepository = LocalAuthRepository(db, sessionPrefs)
        wardrobeRepository = LocalWardrobeRepository(db.clothingItemDao(), photoManager)
        discoveryMatchRepository = LocalDiscoveryMatchRepository(
            discoveryDao = db.discoveryDao(),
            interestMatchDao = db.interestMatchDao(),
            notificationDao = db.notificationDao(),
            clothingDao = db.clothingItemDao(),
            userDao = db.userDao(),
            reviewDao = db.reviewDao()
        )
        chatRepository = LocalChatRepository(
            chatDao = db.chatDao(),
            userDao = db.userDao()
        )
        profileReviewRepository = LocalProfileReviewRepository(
            userDao = db.userDao(),
            reviewDao = db.reviewDao()
        )

        PrepopulateDemoData.seedDatabase(db, force = true)

        rakaId = db.userDao().getUserByEmail("raka@cloble.demo")!!.id
        nayaId = db.userDao().getUserByEmail("naya@cloble.demo")!!.id
        bagasId = db.userDao().getUserByEmail("bagas@cloble.demo")!!.id
        dindaId = db.userDao().getUserByEmail("dinda@cloble.demo")!!.id
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testFullScenarioBSevenStageLifecycle() = runBlocking {

        val bagasLogin = authRepository.login("bagas@cloble.demo", "CloBle123!")
        assertTrue("Bagas harus berhasil login", bagasLogin is AuthResult.Success)

        val nayaWardrobe = wardrobeRepository.observeMyWardrobe(nayaId).first()
        val nayaHoodie = nayaWardrobe.first { it.item.name == "Hoodie hitam" }.item

        discoveryMatchRepository.passCandidate(bagasId, nayaHoodie.id)

        val bagasInterestResult = discoveryMatchRepository.sendInterest(bagasId, nayaHoodie.id)
        assertTrue(
            "Bagas dengan 0 pakaian barter harus mendapat RequiresBarterableItemInWardrobe",
            bagasInterestResult is SendInterestResult.RequiresBarterableItemInWardrobe
        )

        val cancelledMatch = db.interestMatchDao().getMatchById(4L)
        assertNotNull("Match cancelled harus ada di seed", cancelledMatch)
        assertEquals(MatchStatus.CANCELLED, cancelledMatch!!.status)

        val bagasSendToCancelled = chatRepository.sendMessage(cancelledMatch.id, bagasId, "Halo Dinda")
        assertTrue("Kirim pesan ke chat CANCELLED harus ditolak", bagasSendToCancelled is SendMessageResult.ChatReadOnly)

        val rakaLogin = authRepository.login("raka@cloble.demo", "CloBle123!")
        assertTrue(rakaLogin is AuthResult.Success)

        val addResult = wardrobeRepository.addClothingItem(
            ownerId = rakaId,
            draft = ClothingDraft(
                name = "Kaos polos putih",
                description = "Koleksi kaos santai di rumah",
                category = "Atasan",
                size = "M",
                gender = "Pria",
                color = "Putih",
                condition = "Baik",
                mainPhotoUri = null,
                detailPhotoUri = null,
                extraPhotoUri = null,
                isBarterAllowed = false
            )
        )
        assertTrue("Tambah pakaian harus sukses", addResult is WardrobeMutationResult.Success)
        val addedItemId = (addResult as WardrobeMutationResult.Success).itemId
        val savedKaos = db.clothingItemDao().getClothingById(addedItemId)
        assertNotNull(savedKaos)
        assertFalse("isBarterAllowed kaos baru harus false", savedKaos!!.isBarterAllowed)

        val nayaJeans = nayaWardrobe.first { it.item.name == "Celana jeans biru" }.item
        val rakaInterestResult = discoveryMatchRepository.sendInterest(rakaId, nayaJeans.id)
        assertTrue("Raka harus berhasil mengirim interest", rakaInterestResult is SendInterestResult.Success)

        val rakaWardrobe = wardrobeRepository.observeMyWardrobe(rakaId).first()
        val rakaShirt = rakaWardrobe.first { it.item.name == "Kemeja linen krem" }.item
        val crossInterest = db.interestMatchDao().getInterestBySenderAndTarget(nayaId, rakaShirt.id)
        assertNotNull("Cross interest harus ada", crossInterest)
        assertEquals(InterestStatus.PENDING, crossInterest!!.status)

        val nayaLogin = authRepository.login("naya@cloble.demo", "CloBle123!")
        assertTrue(nayaLogin is AuthResult.Success)

        val interest1 = db.interestMatchDao().getInterestBySenderAndTarget(rakaId, nayaHoodie.id)
        assertNotNull("Interest 1 harus ada", interest1)

        val matchCreateResult = discoveryMatchRepository.respondToInterestAndCreateMatch(
            actorUserId = nayaId,
            interestId = interest1!!.id,
            chosenSenderItemId = rakaShirt.id
        )
        assertTrue("Pembuatan Match harus sukses", matchCreateResult is RespondInterestResult.Success)
        val matchNayaRakaId = (matchCreateResult as RespondInterestResult.Success).matchId

        val matchNayaRaka = db.interestMatchDao().getMatchById(matchNayaRakaId)
        assertNotNull(matchNayaRaka)
        assertEquals(MatchStatus.ACTIVE, matchNayaRaka!!.status)
        assertEquals(nayaHoodie.id, matchNayaRaka.user1ItemId)
        assertEquals(rakaShirt.id, matchNayaRaka.user2ItemId)

        val updatedCrossInterest = db.interestMatchDao().getInterestById(crossInterest.id)
        assertNotNull(updatedCrossInterest)
        assertEquals(InterestStatus.RESOLVED_BY_MATCH, updatedCrossInterest!!.status)

        val nayaMsgResult = chatRepository.sendMessage(matchNayaRakaId, nayaId, "Halo Raka, kapan bisa barter di BIP?")
        assertTrue("Naya harus berhasil mengirim pesan", nayaMsgResult is SendMessageResult.Success)

        val dindaLogin = authRepository.login("dinda@cloble.demo", "CloBle123!")
        assertTrue(dindaLogin is AuthResult.Success)

        val dindaProfile = profileReviewRepository.observeUserProfile(dindaId).first()
        assertNotNull(dindaProfile)
        assertEquals(2.0, dindaProfile!!.averageRating, 0.01)
        assertEquals(1, dindaProfile.reviewCount)
        assertTrue("Dinda harus berstatus low credibility", dindaProfile.isLowCredibility)

        val dindaWardrobe = wardrobeRepository.observeMyWardrobe(dindaId).first()
        val dindaBlazer = dindaWardrobe.first { it.item.name == "Blazer hitam kasual" }.item
        val interestDinda = db.interestMatchDao().getInterestBySenderAndTarget(rakaId, dindaBlazer.id)
        assertNotNull("Interest Dinda harus ada", interestDinda)

        val dindaRespondConflict = discoveryMatchRepository.respondToInterestAndCreateMatch(
            actorUserId = dindaId,
            interestId = interestDinda!!.id,
            chosenSenderItemId = rakaShirt.id
        )
        assertTrue(
            "Pakaian yang terikat Match ACTIVE harus ditolak dengan ItemAlreadyInActiveMatch",
            dindaRespondConflict is RespondInterestResult.ItemAlreadyInActiveMatch
        )

        val interestDindaAfterReject = db.interestMatchDao().getInterestById(interestDinda.id)
        assertEquals(InterestStatus.PENDING, interestDindaAfterReject!!.status)

        authRepository.login("raka@cloble.demo", "CloBle123!")

        val rakaMsgResult = chatRepository.sendMessage(matchNayaRakaId, rakaId, "Halo Naya, maaf saya harus batalkan.")
        assertTrue("Raka berhasil kirim pesan", rakaMsgResult is SendMessageResult.Success)

        val cancelResult = discoveryMatchRepository.cancelMatch(matchNayaRakaId, rakaId)
        assertTrue("Pembatalan Match harus sukses", cancelResult is CancelMatchResult.Success)

        val cancelledMatchNayaRaka = db.interestMatchDao().getMatchById(matchNayaRakaId)
        assertEquals(MatchStatus.CANCELLED, cancelledMatchNayaRaka!!.status)

        val msgAfterCancel = chatRepository.sendMessage(matchNayaRakaId, rakaId, "Pesan setelah batal")
        assertTrue("Chat harus Read-Only setelah dibatalkan", msgAfterCancel is SendMessageResult.ChatReadOnly)

        authRepository.login("dinda@cloble.demo", "CloBle123!")

        val dindaNewMatchResult = discoveryMatchRepository.respondToInterestAndCreateMatch(
            actorUserId = dindaId,
            interestId = interestDinda.id,
            chosenSenderItemId = rakaShirt.id
        )
        assertTrue("Match Dinda-Raka harus berhasil dibentuk", dindaNewMatchResult is RespondInterestResult.Success)
        val matchDindaRakaId = (dindaNewMatchResult as RespondInterestResult.Success).matchId

        val dindaMsg = chatRepository.sendMessage(matchDindaRakaId, dindaId, "Ketemu di Stasiun Bandung ya")
        assertTrue(dindaMsg is SendMessageResult.Success)

        val completeResult = discoveryMatchRepository.completeMatchExchange(matchDindaRakaId, dindaId, "Stasiun Bandung")
        assertTrue("Selesaikan pertukaran harus sukses", completeResult is CompleteMatchResult.Success)

        val completedMatch = db.interestMatchDao().getMatchById(matchDindaRakaId)
        assertEquals(MatchStatus.COMPLETED, completedMatch!!.status)
        assertEquals("Stasiun Bandung", completedMatch.meetingLocation)

        val dindaBlazerAfter = db.clothingItemDao().getClothingById(dindaBlazer.id)
        val rakaShirtAfter = db.clothingItemDao().getClothingById(rakaShirt.id)
        assertEquals(ItemStatus.EXCHANGED, dindaBlazerAfter!!.status)
        assertEquals(ItemStatus.EXCHANGED, rakaShirtAfter!!.status)

        val dindaReviewResult = profileReviewRepository.submitReview(
            matchId = matchDindaRakaId,
            reviewerId = dindaId,
            rating = 5,
            comment = "Barter sangat lancar dan pakaian sesuai!"
        )
        assertTrue("Dinda berhasil memberi ulasan", dindaReviewResult is ReviewSubmitResult.Success)

        val dindaSecondReview = profileReviewRepository.submitReview(
            matchId = matchDindaRakaId,
            reviewerId = dindaId,
            rating = 4,
            comment = "Ulasan kedua"
        )
        assertTrue("Ulasan kedua harus ditolak AlreadyReviewed", dindaSecondReview is ReviewSubmitResult.AlreadyReviewed)

        authRepository.login("raka@cloble.demo", "CloBle123!")

        val rakaReviewResult = profileReviewRepository.submitReview(
            matchId = matchDindaRakaId,
            reviewerId = rakaId,
            rating = 5,
            comment = "Dinda sangat ramah dan bajunya sangat rapi."
        )
        assertTrue("Raka berhasil memberi ulasan balik", rakaReviewResult is ReviewSubmitResult.Success)

        val updatedDindaProfile = profileReviewRepository.observeUserProfile(dindaId).first()
        assertNotNull(updatedDindaProfile)
        assertEquals(2, updatedDindaProfile!!.reviewCount)
        assertEquals(3.5, updatedDindaProfile.averageRating, 0.01)
        assertFalse(
            "Peringatan low credibility harus hilang karena rating 3.5 >= 3.0",
            updatedDindaProfile.isLowCredibility
        )
    }
}
