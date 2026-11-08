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
import androidx.compose.ui.test.performTextInput
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cloble.CloBleApplication
import com.example.cloble.domain.repository.AuthRepository
import com.example.cloble.ui.navigation.CloBleDestinations
import com.example.cloble.ui.navigation.CloBleNavGraph
import com.example.cloble.ui.theme.CloBleTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class CloBleNavigationUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var app: CloBleApplication
    private lateinit var authRepository: AuthRepository

    @Before
    fun setup() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            app = context.applicationContext as CloBleApplication
            authRepository = app.container.authRepository
            authRepository.ensureSeeded()
            authRepository.logout()
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            authRepository.logout()
        }
    }

    @Test
    fun nav01_startAppWithoutSession_showsLogin() {
        composeTestRule.setContent {
            CloBleTheme {
                CloBleNavGraph()
            }
        }

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("btn_submit_login"), 8000)
        composeTestRule.onNodeWithTag("app_brand_title").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_submit_login").assertIsDisplayed()
    }

    @Test
    fun nav02_startAppWithRakaSession_showsDiscover() {
        runBlocking {
            authRepository.loginDemoAccount(1L)
        }

        composeTestRule.setContent {
            CloBleTheme {
                CloBleNavGraph()
            }
        }

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("bottom_tab_discover"), 8000)
        composeTestRule.onNodeWithTag("bottom_tab_discover").assertIsDisplayed()
    }

    @Test
    fun nav03_demoQuickLogin_showsDiscover_loginNotOnBackStack() {
        var testNavController: NavHostController? = null
        composeTestRule.setContent {
            val nav = rememberNavController()
            testNavController = nav
            CloBleTheme {
                CloBleNavGraph(navController = nav)
            }
        }

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("demo_account_raka_pratama"), 8000)
        composeTestRule.onNodeWithTag("demo_account_raka_pratama").performScrollTo().performClick()

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("bottom_tab_discover"), 8000)
        composeTestRule.onNodeWithTag("bottom_tab_discover").assertIsDisplayed()

        assertEquals(CloBleDestinations.DISCOVER, testNavController?.currentDestination?.route)
        assertNull("Login harus di-pop dari back stack", testNavController?.previousBackStackEntry)
    }

    @Test
    fun nav04_logoutFromProfile_showsLogin_sessionEmpty() {
        runBlocking {
            authRepository.loginDemoAccount(1L)
        }

        composeTestRule.setContent {
            CloBleTheme {
                CloBleNavGraph()
            }
        }

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("bottom_tab_profile"), 8000)
        composeTestRule.onNodeWithTag("bottom_tab_profile").performClick()

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("btn_logout"), 8000)
        composeTestRule.onNodeWithTag("btn_logout").performScrollTo().performClick()

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("btn_submit_login"), 8000)
        composeTestRule.onNodeWithTag("btn_submit_login").assertIsDisplayed()

        val currentUserId = runBlocking { authRepository.observeLoggedInUserId().first() }
        assertNull("Session DataStore harus kosong setelah logout", currentUserId)
    }

    @Test
    fun nav05_backPressAfterLogout_doesNotReturnToPreviousScreens() {
        runBlocking {
            authRepository.loginDemoAccount(1L)
        }

        var testNavController: NavHostController? = null
        composeTestRule.setContent {
            val nav = rememberNavController()
            testNavController = nav
            CloBleTheme {
                CloBleNavGraph(navController = nav)
            }
        }

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("bottom_tab_profile"), 8000)
        composeTestRule.onNodeWithTag("bottom_tab_profile").performClick()

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("btn_logout"), 8000)
        composeTestRule.onNodeWithTag("btn_logout").performScrollTo().performClick()

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("btn_submit_login"), 8000)
        composeTestRule.onNodeWithTag("btn_submit_login").assertIsDisplayed()

        assertEquals(CloBleDestinations.LOGIN, testNavController?.currentDestination?.route)
        assertNull("Back stack harus bersih setelah logout", testNavController?.previousBackStackEntry)
    }

    @Test
    fun nav06_registerNewAccount_navigatesToOnboardingPreferences() {
        composeTestRule.setContent {
            CloBleTheme {
                CloBleNavGraph()
            }
        }

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("btn_go_to_register"), 8000)
        composeTestRule.onNodeWithTag("btn_go_to_register").performClick()

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("input_register_name"), 8000)
        val timestamp = System.currentTimeMillis()
        composeTestRule.onNodeWithTag("input_register_name").performScrollTo().performTextInput("Pengguna Baru")
        composeTestRule.onNodeWithTag("input_register_email").performScrollTo().performTextInput("baru$timestamp@test.com")
        composeTestRule.onNodeWithTag("input_register_password").performScrollTo().performTextInput("password123")
        composeTestRule.onNodeWithTag("input_register_confirm_password").performScrollTo().performTextInput("password123")

        composeTestRule.onNodeWithTag("btn_submit_register").performScrollTo().performClick()

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("btn_save_preferences"), 8000)
        composeTestRule.onNodeWithText("Atur Preferensi Awal").assertIsDisplayed()
        composeTestRule.onNodeWithText("Lanjut ke Jelajah").assertIsDisplayed()
    }

    @Test
    fun nav07_saveOnboardingPreferences_navigatesToDiscover() {
        composeTestRule.setContent {
            CloBleTheme {
                CloBleNavGraph()
            }
        }

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("btn_go_to_register"), 8000)
        composeTestRule.onNodeWithTag("btn_go_to_register").performClick()

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("input_register_name"), 8000)
        val timestamp = System.currentTimeMillis()
        composeTestRule.onNodeWithTag("input_register_name").performScrollTo().performTextInput("User Onboard")
        composeTestRule.onNodeWithTag("input_register_email").performScrollTo().performTextInput("onboard$timestamp@test.com")
        composeTestRule.onNodeWithTag("input_register_password").performScrollTo().performTextInput("password123")
        composeTestRule.onNodeWithTag("input_register_confirm_password").performScrollTo().performTextInput("password123")

        composeTestRule.onNodeWithTag("btn_submit_register").performScrollTo().performClick()

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("btn_save_preferences"), 8000)
        composeTestRule.onNodeWithTag("btn_save_preferences").performScrollTo().performClick()

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("bottom_tab_discover"), 8000)
        composeTestRule.onNodeWithTag("bottom_tab_discover").assertIsDisplayed()
    }

    @Test
    fun nav08_editPreferencesFromProfile_returnsToProfileAfterSave() {
        runBlocking {
            authRepository.loginDemoAccount(1L)
        }

        composeTestRule.setContent {
            CloBleTheme {
                CloBleNavGraph()
            }
        }

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("bottom_tab_profile"), 8000)
        composeTestRule.onNodeWithTag("bottom_tab_profile").performClick()

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("btn_go_to_preferences"), 8000)
        composeTestRule.onNodeWithTag("btn_go_to_preferences").performScrollTo().performClick()

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("btn_save_preferences"), 8000)
        composeTestRule.onNodeWithText("Preferensi Pakaian").assertIsDisplayed()
        composeTestRule.onNodeWithText("Simpan Preferensi").assertIsDisplayed()

        composeTestRule.onNodeWithTag("btn_save_preferences").performScrollTo().performClick()

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("btn_logout"), 8000)
        composeTestRule.onNodeWithTag("btn_logout").assertIsDisplayed()
    }

    @Test
    fun nav09_logoutThenLoginAsNaya_displaysNayaData() {
        runBlocking {
            authRepository.loginDemoAccount(1L)
        }

        composeTestRule.setContent {
            CloBleTheme {
                CloBleNavGraph()
            }
        }

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("bottom_tab_profile"), 8000)
        composeTestRule.onNodeWithTag("bottom_tab_profile").performClick()

        composeTestRule.waitUntilExactlyOneExists(hasText("Raka Pratama"), 8000)
        composeTestRule.onNodeWithText("Raka Pratama").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_logout").performScrollTo().performClick()

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("demo_account_naya"), 8000)
        composeTestRule.onNodeWithTag("demo_account_naya").performScrollTo().performClick()

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("bottom_tab_profile"), 8000)
        composeTestRule.onNodeWithTag("bottom_tab_profile").performClick()

        composeTestRule.waitUntilExactlyOneExists(hasText("Naya"), 8000)
        composeTestRule.onNodeWithText("Naya").performScrollTo().assertIsDisplayed()
        assertEquals(0, composeTestRule.onAllNodes(hasText("Raka Pratama")).fetchSemanticsNodes().size)
    }

    @Test
    fun nav10_relaunchWithSavedSession_directlyToDiscover() {
        runBlocking {
            authRepository.loginDemoAccount(1L)
        }

        composeTestRule.setContent {
            CloBleTheme {
                CloBleNavGraph()
            }
        }

        composeTestRule.waitUntilExactlyOneExists(hasTestTag("bottom_tab_discover"), 8000)
        composeTestRule.onNodeWithTag("bottom_tab_discover").assertIsDisplayed()
        assertEquals(0, composeTestRule.onAllNodesWithTag("btn_submit_login").fetchSemanticsNodes().size)
    }
}
