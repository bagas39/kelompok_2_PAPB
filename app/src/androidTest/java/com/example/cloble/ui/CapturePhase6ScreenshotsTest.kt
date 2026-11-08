package com.example.cloble.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class CapturePhase6ScreenshotsTest {

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
            authRepository.ensureSeeded()
            database.discoveryDao().clearSwipePassesForUser(1L)
            authRepository.loginDemoAccount(1L)
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            authRepository.logout()
        }
    }

    private fun saveScreen(filename: String) {
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val file = File("/sdcard/Download/$filename")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    @Test
    fun cap01_discoverFeedBeforeSwipe() {
        composeTestRule.setContent {
            CloBleTheme {
                CloBleNavGraph(startDestination = CloBleDestinations.DISCOVER)
            }
        }
        composeTestRule.waitUntilExactlyOneExists(hasTestTag("discover_candidate_card"), 10000)
        composeTestRule.waitForIdle()
        Thread.sleep(600)
        saveScreen("01_discover_feed_before_swipe.png")
    }

    @Test
    fun cap02_discoverDraggingLeftLewati() {
        composeTestRule.setContent {
            CloBleTheme {
                CloBleNavGraph(startDestination = CloBleDestinations.DISCOVER)
            }
        }
        composeTestRule.waitUntilExactlyOneExists(hasTestTag("discover_candidate_card"), 10000)
        composeTestRule.onNodeWithTag("discover_candidate_card").performTouchInput {
            down(center)
            moveBy(Offset(-220f, 0f))
        }
        composeTestRule.waitForIdle()
        Thread.sleep(500)
        saveScreen("02_discover_dragging_left_lewati.png")
        composeTestRule.onNodeWithTag("discover_candidate_card").performTouchInput {
            up()
        }
    }

    @Test
    fun cap03_discoverDraggingRightTertarik() {
        composeTestRule.setContent {
            CloBleTheme {
                CloBleNavGraph(startDestination = CloBleDestinations.DISCOVER)
            }
        }
        composeTestRule.waitUntilExactlyOneExists(hasTestTag("discover_candidate_card"), 10000)
        composeTestRule.onNodeWithTag("discover_candidate_card").performTouchInput {
            down(center)
            moveBy(Offset(220f, 0f))
        }
        composeTestRule.waitForIdle()
        Thread.sleep(500)
        saveScreen("03_discover_dragging_right_tertarik.png")
        composeTestRule.onNodeWithTag("discover_candidate_card").performTouchInput {
            up()
        }
    }

    @Test
    fun cap04_discoverFeedAfterSwipe() {
        composeTestRule.setContent {
            CloBleTheme {
                CloBleNavGraph(startDestination = CloBleDestinations.DISCOVER)
            }
        }
        composeTestRule.waitUntilExactlyOneExists(hasTestTag("discover_candidate_card"), 10000)
        composeTestRule.onNodeWithTag("discover_candidate_card").performTouchInput {
            swipeLeft()
        }
        composeTestRule.waitForIdle()
        Thread.sleep(600)
        saveScreen("04_discover_feed_after_swipe.png")
    }

    @Test
    fun cap05_activityHistoryTertarikTab() {
        runBlocking {
            val now = System.currentTimeMillis()
            val int1 = database.interestMatchDao().getInterestBySenderAndTarget(1L, 4L)
            if (int1 != null && int1.status == InterestStatus.PENDING) {
                database.interestMatchDao().respondToInterestAndCreateMatch(2L, int1.id, 1L, now)
            }
        }

        composeTestRule.setContent {
            CloBleTheme {
                CloBleNavGraph(startDestination = CloBleDestinations.ACTIVITY_HISTORY)
            }
        }
        composeTestRule.waitUntilExactlyOneExists(hasTestTag("list_history_tertarik"), 10000)
        composeTestRule.waitForIdle()
        Thread.sleep(600)
        saveScreen("05_activity_history_tertarik_tab.png")
    }

    @Test
    fun cap06_activityHistoryDilewatiTab() {
        runBlocking {
            database.discoveryDao().clearSwipePassesForUser(1L)
            database.discoveryDao().insertSwipePass(
                SwipePassEntity(userId = 1L, targetItemId = 5L, createdAt = System.currentTimeMillis())
            )
            database.discoveryDao().insertSwipePass(
                SwipePassEntity(userId = 1L, targetItemId = 7L, createdAt = System.currentTimeMillis() - 60000L)
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
        composeTestRule.waitForIdle()
        Thread.sleep(600)
        saveScreen("06_activity_history_dilewati_tab.png")
    }

    @Test
    fun cap07_activityHistoryAfterConvert() {
        var itemId = 0L
        runBlocking {
            database.discoveryDao().clearSwipePassesForUser(1L)
            itemId = database.clothingItemDao().insertClothing(
                ClothingItemEntity(
                    ownerId = 2L,
                    name = "Kemeja Linen Vintage",
                    description = "Barter santai",
                    category = "Atasan",
                    size = "M",
                    gender = "Uniseks",
                    color = "Krem",
                    condition = "Sangat baik",
                    isBarterAllowed = true,
                    status = ItemStatus.AVAILABLE
                )
            )
            database.discoveryDao().insertSwipePass(
                SwipePassEntity(userId = 1L, targetItemId = itemId, createdAt = System.currentTimeMillis())
            )
        }

        composeTestRule.setContent {
            CloBleTheme {
                CloBleNavGraph(startDestination = CloBleDestinations.ACTIVITY_HISTORY)
            }
        }
        composeTestRule.waitUntilExactlyOneExists(hasTestTag("tab_history_dilewati"), 10000)
        composeTestRule.onNodeWithTag("tab_history_dilewati").performClick()
        composeTestRule.waitUntilExactlyOneExists(hasTestTag("btn_convert_to_interest_$itemId"), 10000)
        composeTestRule.onNodeWithTag("btn_convert_to_interest_$itemId").performClick()
        composeTestRule.waitUntilExactlyOneExists(hasTestTag("history_action_banner"), 10000)
        composeTestRule.waitForIdle()
        Thread.sleep(600)
        saveScreen("07_activity_history_after_convert.png")
    }

    @Test
    fun cap08_activityHistoryAfterRestore() {
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
        composeTestRule.waitForIdle()
        Thread.sleep(600)
        saveScreen("08_activity_history_after_restore.png")
    }

    @Test
    fun cap09_activityHistoryEmptyTertarik() {
        var emptyUserId = 0L
        runBlocking {
            val hash = PasswordHasher.hash("secret123")
            emptyUserId = database.userDao().insertUser(
                UserEntity(
                    name = "User Kosong",
                    email = "empty_${System.currentTimeMillis()}@test.com",
                    passwordHash = hash,
                    city = "Bandung, Jawa Barat",
                    latitude = -6.9175,
                    longitude = 107.6191
                )
            )
            authRepository.login(database.userDao().getUserById(emptyUserId)!!.email, "secret123")
        }

        composeTestRule.setContent {
            CloBleTheme {
                CloBleNavGraph(startDestination = CloBleDestinations.ACTIVITY_HISTORY)
            }
        }
        composeTestRule.waitUntilExactlyOneExists(hasTestTag("empty_history_tertarik"), 10000)
        composeTestRule.waitForIdle()
        Thread.sleep(600)
        saveScreen("09_activity_history_empty_tertarik.png")
    }

    @Test
    fun cap10_activityHistoryEmptyDilewati() {
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
        composeTestRule.waitForIdle()
        Thread.sleep(600)
        saveScreen("10_activity_history_empty_dilewati.png")
    }
}
