package com.example.cloble.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cloble.CloBleApplication
import com.example.cloble.data.local.db.CloBleDatabase
import com.example.cloble.data.local.entity.ClothingItemEntity
import com.example.cloble.data.local.entity.SwipePassEntity
import com.example.cloble.data.local.entity.UserEntity
import com.example.cloble.domain.model.InterestStatus
import com.example.cloble.domain.model.ItemStatus
import com.example.cloble.domain.repository.AuthRepository
import com.example.cloble.domain.util.PasswordHasher
import com.example.cloble.ui.navigation.CloBleDestinations
import com.example.cloble.ui.navigation.CloBleNavGraph
import com.example.cloble.ui.theme.CloBleTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class ActivityHistoryUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var app: CloBleApplication
    private lateinit var authRepository: AuthRepository
    private lateinit var database: CloBleDatabase

    @Before
    fun setup() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            app = context.applicationContext as CloBleApplication
            authRepository = app.container.authRepository
            database = app.container.database

            database.openHelper.writableDatabase.execSQL("DELETE FROM swipe_passes")
            database.openHelper.writableDatabase.execSQL("DELETE FROM notifications WHERE relatedInterestId IS NOT NULL OR relatedMatchId IS NOT NULL")
            database.openHelper.writableDatabase.execSQL("DELETE FROM matches WHERE id > 4")
            database.openHelper.writableDatabase.execSQL("DELETE FROM interests WHERE id > 7")
            database.openHelper.writableDatabase.execSQL("UPDATE clothing_items SET status = 'AVAILABLE' WHERE id IN (1, 4, 5, 7)")
            database.openHelper.writableDatabase.execSQL("UPDATE interests SET status = 'PENDING' WHERE id = 1")

            authRepository.ensureSeeded()
            authRepository.loginDemoAccount(1L)
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            database.openHelper.writableDatabase.execSQL("DELETE FROM swipe_passes")
            authRepository.logout()
        }
    }

    @Test
    fun hist01_navigateFromDiscoverTopBar() {
        composeTestRule.setContent {
            CloBleTheme {
                CloBleNavGraph(startDestination = CloBleDestinations.DISCOVER)
            }
        }

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("btn_history_from_discover"), 10000)
        composeTestRule.onNodeWithTag("btn_history_from_discover").performClick()

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("tab_history_tertarik"), 10000)
        composeTestRule.onNodeWithTag("tab_history_tertarik").assertIsDisplayed()
        composeTestRule.onNodeWithTag("tab_history_dilewati").assertIsDisplayed()
    }

    @Test
    fun hist02_navigateFromProfile() {
        composeTestRule.setContent {
            CloBleTheme {
                CloBleNavGraph(startDestination = CloBleDestinations.MY_PROFILE)
            }
        }

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("btn_go_to_history"), 10000)
        composeTestRule.onNodeWithTag("btn_go_to_history").performScrollTo().performClick()

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("tab_history_tertarik"), 10000)
        composeTestRule.onNodeWithTag("tab_history_tertarik").assertIsDisplayed()
    }

    @Test
    fun hist03_emptyStateTertarik() {

        var newUserId = 0L
        runBlocking {
            val hash = PasswordHasher.hash("secret123")
            newUserId = database.userDao().insertUser(
                UserEntity(
                    name = "User Kosong",
                    email = "kosong_${System.currentTimeMillis()}@test.com",
                    passwordHash = hash,
                    city = "Bandung, Jawa Barat",
                    latitude = -6.9175,
                    longitude = 107.6191
                )
            )
            authRepository.login(database.userDao().getUserById(newUserId)!!.email, "secret123")
        }

        composeTestRule.setContent {
            CloBleTheme {
                CloBleNavGraph(startDestination = CloBleDestinations.ACTIVITY_HISTORY)
            }
        }

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("empty_history_tertarik"), 10000)
        composeTestRule.onNodeWithTag("empty_history_tertarik").assertIsDisplayed()
    }

    @Test
    fun hist04_emptyStateDilewati() {
        runBlocking {
            database.discoveryDao().clearSwipePassesForUser(1L)
        }

        composeTestRule.setContent {
            CloBleTheme {
                CloBleNavGraph(startDestination = CloBleDestinations.ACTIVITY_HISTORY)
            }
        }

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("tab_history_dilewati"), 10000)
        composeTestRule.onNodeWithTag("tab_history_dilewati").performClick()

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("empty_history_dilewati"), 10000)
        composeTestRule.onNodeWithTag("empty_history_dilewati").assertIsDisplayed()
    }

    @Test
    fun hist05_switchTabs() {
        composeTestRule.setContent {
            CloBleTheme {
                CloBleNavGraph(startDestination = CloBleDestinations.ACTIVITY_HISTORY)
            }
        }

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("tab_history_tertarik"), 10000)
        composeTestRule.onNodeWithTag("tab_history_dilewati").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("tab_history_tertarik").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("tab_history_tertarik").assertIsDisplayed()
    }

    @Test
    fun hist06_outgoingInterestDisplayedAfterSwipeRight() {
        composeTestRule.setContent {
            CloBleTheme {
                CloBleNavGraph(startDestination = CloBleDestinations.ACTIVITY_HISTORY)
            }
        }

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("list_history_tertarik"), 10000)
        composeTestRule.onNodeWithTag("list_history_tertarik").assertIsDisplayed()
        composeTestRule.onNodeWithText("Hoodie hitam").assertIsDisplayed()
    }

    @Test
    fun hist07_outgoingInterestDisplaysStatusBadge() {
        composeTestRule.setContent {
            CloBleTheme {
                CloBleNavGraph(startDestination = CloBleDestinations.ACTIVITY_HISTORY)
            }
        }

        composeTestRule.waitUntilAtLeastOneExists(hasText("Menunggu Respon"), 10000)
        composeTestRule.onAllNodes(hasText("Menunggu Respon"))[0].assertIsDisplayed()
    }

    @Test
    fun hist08_outgoingInterestWithMatchDisplaysLihatMatch() {

        runBlocking {
            val now = System.currentTimeMillis()
            database.interestMatchDao().respondToInterestAndCreateMatch(
                actorUserId = 2L,
                interestId = 1L,
                chosenSenderItemId = 1L,
                nowMillis = now
            )
        }

        composeTestRule.setContent {
            CloBleTheme {
                CloBleNavGraph(startDestination = CloBleDestinations.ACTIVITY_HISTORY)
            }
        }

        composeTestRule.waitUntilExactlyOneExists(hasText("Berhasil Match!"), 10000)
        composeTestRule.onNodeWithText("Berhasil Match!").assertIsDisplayed()
        composeTestRule.onNodeWithText("Lihat Match").assertIsDisplayed()
    }

    @Test
    fun hist09_passedItemDisplayedAfterSwipeLeft() {
        runBlocking {
            database.discoveryDao().clearSwipePassesForUser(1L)
            database.discoveryDao().insertSwipePass(
                SwipePassEntity(userId = 1L, targetItemId = 5L, createdAt = System.currentTimeMillis())
            )
        }

        composeTestRule.setContent {
            CloBleTheme {
                CloBleNavGraph(startDestination = CloBleDestinations.ACTIVITY_HISTORY)
            }
        }

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("tab_history_dilewati"), 10000)
        composeTestRule.onNodeWithTag("tab_history_dilewati").performClick()

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("list_history_dilewati"), 10000)
        composeTestRule.onNodeWithTag("list_history_dilewati").assertIsDisplayed()
        composeTestRule.onNodeWithTag("passed_clothing_card_5").assertIsDisplayed()
    }

    @Test
    fun hist10_passedItemDisplaysActionButtons() {
        runBlocking {
            database.discoveryDao().clearSwipePassesForUser(1L)
            database.discoveryDao().insertSwipePass(
                SwipePassEntity(userId = 1L, targetItemId = 5L, createdAt = System.currentTimeMillis())
            )
        }

        composeTestRule.setContent {
            CloBleTheme {
                CloBleNavGraph(startDestination = CloBleDestinations.ACTIVITY_HISTORY)
            }
        }

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("tab_history_dilewati"), 10000)
        composeTestRule.onNodeWithTag("tab_history_dilewati").performClick()

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("btn_convert_to_interest_5"), 10000)
        composeTestRule.onNodeWithTag("btn_convert_to_interest_5").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_restore_to_discover_5").assertIsDisplayed()
    }

    @Test
    fun hist11_restoreItemRemovesFromDilewatiAndShowsBanner() {
        runBlocking {
            database.discoveryDao().clearSwipePassesForUser(1L)
            database.discoveryDao().insertSwipePass(
                SwipePassEntity(userId = 1L, targetItemId = 5L, createdAt = System.currentTimeMillis())
            )
        }

        composeTestRule.setContent {
            CloBleTheme {
                CloBleNavGraph(startDestination = CloBleDestinations.ACTIVITY_HISTORY)
            }
        }

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("tab_history_dilewati"), 10000)
        composeTestRule.onNodeWithTag("tab_history_dilewati").performClick()

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("btn_restore_to_discover_5"), 10000)
        composeTestRule.onNodeWithTag("btn_restore_to_discover_5").performClick()

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("history_action_banner"), 10000)
        composeTestRule.onNodeWithTag("history_action_banner").assertIsDisplayed()

        runBlocking {
            assertEquals(0, database.discoveryDao().hasUserPassedItem(1L, 5L))
        }
    }

    @Test
    fun hist12_restoredItemReappearsInDiscover() {
        runBlocking {

            database.discoveryDao().deleteSwipePass(1L, 5L)
            val candidates = database.discoveryDao().getDiscoverCandidatesFlow(1L).first()
            assertTrue("Item 5 harus muncul di feed Discover", candidates.any { it.item.id == 5L })
        }
    }

    @Test
    fun hist13_convertPassToInterest_atomicSuccess() {
        var testItemId = 0L
        runBlocking {
            testItemId = database.clothingItemDao().insertClothing(
                ClothingItemEntity(
                    ownerId = 2L,
                    name = "Kemeja Pantai Tropis",
                    description = "Bagus untuk barter",
                    category = "Atasan",
                    size = "M",
                    gender = "Uniseks",
                    color = "Biru",
                    condition = "Sangat baik",
                    isBarterAllowed = true,
                    status = ItemStatus.AVAILABLE
                )
            )
            database.discoveryDao().insertSwipePass(
                SwipePassEntity(userId = 1L, targetItemId = testItemId, createdAt = System.currentTimeMillis())
            )
        }

        composeTestRule.setContent {
            CloBleTheme {
                CloBleNavGraph(startDestination = CloBleDestinations.ACTIVITY_HISTORY)
            }
        }

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("tab_history_dilewati"), 10000)
        composeTestRule.onNodeWithTag("tab_history_dilewati").performClick()

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("btn_convert_to_interest_$testItemId"), 10000)
        composeTestRule.onNodeWithTag("btn_convert_to_interest_$testItemId").performClick()

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("history_action_banner"), 10000)
        composeTestRule.onNodeWithTag("history_action_banner").assertIsDisplayed()

        runBlocking {
            assertEquals(0, database.discoveryDao().hasUserPassedItem(1L, testItemId))
            val interest = database.interestMatchDao().getInterestBySenderAndTarget(1L, testItemId)
            assertNotNull(interest)
            assertEquals(InterestStatus.PENDING, interest?.status)
        }
    }

    @Test
    fun hist14_convertPassToInterest_failureWhenNoBarterItems_preservesPass() {
        var userTanpaBarter = 0L
        runBlocking {
            val hash = PasswordHasher.hash("secret123")
            userTanpaBarter = database.userDao().insertUser(
                UserEntity(
                    name = "User Tanpa Barter",
                    email = "nobarter_${System.currentTimeMillis()}@test.com",
                    passwordHash = hash,
                    city = "Bandung, Jawa Barat",
                    latitude = -6.9175,
                    longitude = 107.6191
                )
            )

            database.clothingItemDao().insertClothing(
                ClothingItemEntity(
                    ownerId = userTanpaBarter,
                    name = "Baju Pribadi",
                    description = "Tidak untuk barter",
                    category = "Atasan",
                    size = "L",
                    gender = "Pria",
                    color = "Putih",
                    condition = "Baik",
                    isBarterAllowed = false,
                    status = ItemStatus.AVAILABLE
                )
            )

            database.discoveryDao().insertSwipePass(
                SwipePassEntity(userId = userTanpaBarter, targetItemId = 5L, createdAt = System.currentTimeMillis())
            )
            authRepository.login(database.userDao().getUserById(userTanpaBarter)!!.email, "secret123")
        }

        composeTestRule.setContent {
            CloBleTheme {
                CloBleNavGraph(startDestination = CloBleDestinations.ACTIVITY_HISTORY)
            }
        }

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("tab_history_dilewati"), 10000)
        composeTestRule.onNodeWithTag("tab_history_dilewati").performClick()

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("btn_convert_to_interest_5"), 10000)
        composeTestRule.onNodeWithTag("btn_convert_to_interest_5").performClick()

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("history_error_banner"), 10000)
        composeTestRule.onNodeWithTag("history_error_banner").assertIsDisplayed()

        runBlocking {
            assertEquals(1, database.discoveryDao().hasUserPassedItem(userTanpaBarter, 5L))
        }
    }
}
