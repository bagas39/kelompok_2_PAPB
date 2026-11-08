package com.example.cloble.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cloble.CloBleApplication
import com.example.cloble.data.local.db.CloBleDatabase
import com.example.cloble.domain.repository.AuthRepository
import com.example.cloble.domain.repository.DiscoveryMatchRepository
import com.example.cloble.ui.navigation.CloBleDestinations
import com.example.cloble.ui.navigation.CloBleNavGraph
import com.example.cloble.ui.theme.CloBleTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class DiscoverSwipeGestureUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var app: CloBleApplication
    private lateinit var authRepository: AuthRepository
    private lateinit var discoveryRepository: DiscoveryMatchRepository
    private lateinit var database: CloBleDatabase

    @Before
    fun setup() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            app = context.applicationContext as CloBleApplication
            authRepository = app.container.authRepository
            discoveryRepository = app.container.discoveryMatchRepository
            database = app.container.database

            database.openHelper.writableDatabase.execSQL("DELETE FROM swipe_passes WHERE userId = 1")
            database.openHelper.writableDatabase.execSQL("DELETE FROM interests WHERE senderUserId = 1")
            database.openHelper.writableDatabase.execSQL("DELETE FROM notifications WHERE userId = 1 OR relatedInterestId IS NOT NULL")

            authRepository.ensureSeeded()
            authRepository.loginDemoAccount(1L)
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            database.openHelper.writableDatabase.execSQL("DELETE FROM swipe_passes WHERE userId = 1")
            database.openHelper.writableDatabase.execSQL("DELETE FROM interests WHERE senderUserId = 1")
            authRepository.logout()
        }
    }

    @Test
    fun swipe01_candidateCardDisplaysCorrectInfo() {
        composeTestRule.setContent {
            CloBleTheme {
                CloBleNavGraph(startDestination = CloBleDestinations.DISCOVER)
            }
        }

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("discover_candidate_card"), 10000)
        composeTestRule.onNodeWithTag("discover_candidate_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_swipe_pass").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_swipe_like").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_history_from_discover").assertIsDisplayed()
    }

    @Test
    fun swipe02_draggingRight_showsTertarikBadge() {
        composeTestRule.setContent {
            CloBleTheme {
                CloBleNavGraph(startDestination = CloBleDestinations.DISCOVER)
            }
        }

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("discover_candidate_card"), 10000)

        composeTestRule.onNodeWithTag("discover_candidate_card").performTouchInput {
            down(center)
            moveBy(Offset(250f, 0f))
        }

        composeTestRule.onNodeWithTag("badge_tertarik", useUnmergedTree = true).assertExists()

        composeTestRule.onNodeWithTag("discover_candidate_card").performTouchInput {
            up()
        }
    }

    @Test
    fun swipe03_draggingLeft_showsLewatiBadge() {
        composeTestRule.setContent {
            CloBleTheme {
                CloBleNavGraph(startDestination = CloBleDestinations.DISCOVER)
            }
        }

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("discover_candidate_card"), 10000)

        composeTestRule.onNodeWithTag("discover_candidate_card").performTouchInput {
            down(center)
            moveBy(Offset(-250f, 0f))
        }

        composeTestRule.onNodeWithTag("badge_lewati", useUnmergedTree = true).assertExists()

        composeTestRule.onNodeWithTag("discover_candidate_card").performTouchInput {
            up()
        }
    }

    @Test
    fun swipe04_incompleteDrag_springsBack_preservesCandidate() {
        composeTestRule.setContent {
            CloBleTheme {
                CloBleNavGraph(startDestination = CloBleDestinations.DISCOVER)
            }
        }

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("discover_candidate_card"), 10000)

        composeTestRule.onNodeWithTag("discover_candidate_card").performTouchInput {
            down(center)
            moveBy(Offset(35f, 0f))
            up()
        }

        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag("discover_candidate_card").assertIsDisplayed()
        runBlocking {
            assertEquals(0, database.discoveryDao().countSwipePassesForUser(1L))
        }
    }

    @Test
    fun swipe05_fullSwipeLeft_passesCandidate_recordsSwipePass() {
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

        runBlocking {
            val passCount = database.discoveryDao().countSwipePassesForUser(1L)
            assertTrue("Swipe pass harus tercatat di database setelah swipe left", passCount >= 1)
        }
    }

    @Test
    fun swipe06_fullSwipeRight_sendsInterest_advancesCandidate() {
        composeTestRule.setContent {
            CloBleTheme {
                CloBleNavGraph(startDestination = CloBleDestinations.DISCOVER)
            }
        }

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("discover_candidate_card"), 10000)

        composeTestRule.onNodeWithTag("discover_candidate_card").performTouchInput {
            swipeRight()
        }

        composeTestRule.waitForIdle()

        runBlocking {
            val outgoing = database.interestMatchDao().getOutgoingInterestsFlow(1L).first()
            assertTrue("Outgoing interest harus tercatat setelah swipe right", outgoing.isNotEmpty())
        }
    }

    @Test
    fun swipe07_tapCandidateCard_navigatesToItemDetail() {
        composeTestRule.setContent {
            CloBleTheme {
                CloBleNavGraph(startDestination = CloBleDestinations.DISCOVER)
            }
        }

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("discover_candidate_card"), 10000)

        composeTestRule.onNodeWithTag("discover_candidate_card").performClick()

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("detail_clothing_title"), 10000)
        composeTestRule.onNodeWithTag("detail_clothing_title").assertIsDisplayed()
    }

    @Test
    fun swipe08_clickPassButton_passesCandidate() {
        composeTestRule.setContent {
            CloBleTheme {
                CloBleNavGraph(startDestination = CloBleDestinations.DISCOVER)
            }
        }

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("btn_swipe_pass"), 10000)

        composeTestRule.onNodeWithTag("btn_swipe_pass").performClick()
        composeTestRule.waitForIdle()

        runBlocking {
            val passCount = database.discoveryDao().countSwipePassesForUser(1L)
            assertTrue("Tombol X harus mencatat swipe pass", passCount >= 1)
        }
    }

    @Test
    fun swipe09_clickLikeButton_sendsInterest() {
        composeTestRule.setContent {
            CloBleTheme {
                CloBleNavGraph(startDestination = CloBleDestinations.DISCOVER)
            }
        }

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("btn_swipe_like"), 10000)

        composeTestRule.onNodeWithTag("btn_swipe_like").performClick()
        composeTestRule.waitForIdle()

        runBlocking {
            val outgoing = database.interestMatchDao().getOutgoingInterestsFlow(1L).first()
            assertTrue("Tombol Heart harus mencatat outgoing interest", outgoing.isNotEmpty())
        }
    }

    @Test
    fun swipe10_rapidDoubleAction_guardedBySubmittingFlag() {
        composeTestRule.setContent {
            CloBleTheme {
                CloBleNavGraph(startDestination = CloBleDestinations.DISCOVER)
            }
        }

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("btn_swipe_pass"), 10000)

        composeTestRule.onNodeWithTag("btn_swipe_pass").performClick()
        composeTestRule.onNodeWithTag("btn_swipe_pass").performClick()

        composeTestRule.waitForIdle()

        runBlocking {
            val passCount = database.discoveryDao().countSwipePassesForUser(1L)
            assertTrue("Pass harus tercatat tanpa crash akibat rapid click", passCount >= 1)
        }
    }
}
