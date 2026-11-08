package com.example.cloble.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cloble.CloBleApplication
import com.example.cloble.domain.model.MatchStatus
import com.example.cloble.ui.navigation.CloBleDestinations
import com.example.cloble.ui.navigation.CloBleNavGraph
import com.example.cloble.ui.theme.CloBleTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class CaptureMissingScreenshotsTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var app: CloBleApplication

    @Before
    fun setup() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            app = context.applicationContext as CloBleApplication
            app.container.authRepository.ensureSeeded()
            app.container.authRepository.logout()
        }
    }

    private fun saveScreen(filename: String) {
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val file = File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, filename)
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    @Test
    fun capture01_registerScreen() {
        composeTestRule.setContent {
            CloBleTheme {
                CloBleNavGraph(startDestination = CloBleDestinations.REGISTER)
            }
        }
        composeTestRule.waitUntilExactlyOneExists(hasTestTag("btn_submit_register"), 8000)
        composeTestRule.waitForIdle()
        Thread.sleep(500)
        saveScreen("07_register_screen.png")
    }

    @Test
    fun capture02_editClothingScreen() {
        runBlocking {
            app.container.authRepository.loginDemoAccount(1L)
        }
        val items = runBlocking { app.container.wardrobeRepository.observeMyWardrobe(1L).first() }
        val item = items.first().item

        composeTestRule.setContent {
            CloBleTheme {
                CloBleNavGraph(startDestination = CloBleDestinations.editClothingRoute(item.id))
            }
        }
        composeTestRule.waitUntilExactlyOneExists(hasTestTag("btn_save_clothing"), 8000)
        composeTestRule.waitForIdle()
        Thread.sleep(500)
        saveScreen("08_edit_clothing_screen.png")
    }

    @Test
    fun capture03_deleteClothingDialog() {
        runBlocking {
            app.container.authRepository.loginDemoAccount(1L)
        }
        val items = runBlocking { app.container.wardrobeRepository.observeMyWardrobe(1L).first() }
        val item = items.first().item

        composeTestRule.setContent {
            CloBleTheme {
                CloBleNavGraph(startDestination = CloBleDestinations.editClothingRoute(item.id))
            }
        }
        composeTestRule.waitUntilExactlyOneExists(hasTestTag("btn_delete_clothing"), 8000)
        composeTestRule.onNodeWithTag("btn_delete_clothing").performScrollTo().performClick()

        composeTestRule.waitUntilExactlyOneExists(hasText("Hapus Pakaian?"), 8000)
        composeTestRule.waitForIdle()
        Thread.sleep(500)
        saveScreen("09_delete_clothing_dialog.png")
    }

    @Test
    fun capture04_conversationCancelled() {
        val matchThread = runBlocking {
            app.container.chatRepository.observeChatThreads(3L).first().first { it.status == MatchStatus.CANCELLED }
        }
        runBlocking {
            app.container.authRepository.loginDemoAccount(3L)
        }

        composeTestRule.setContent {
            CloBleTheme {
                CloBleNavGraph(startDestination = CloBleDestinations.conversationRoute(matchThread.matchId))
            }
        }
        composeTestRule.waitUntilExactlyOneExists(hasTestTag("cancelled_status_info_box"), 8000)
        composeTestRule.waitForIdle()
        Thread.sleep(500)
        saveScreen("05_conversation_cancelled.png")
    }
}
