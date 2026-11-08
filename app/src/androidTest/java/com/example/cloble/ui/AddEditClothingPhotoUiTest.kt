package com.example.cloble.ui

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cloble.CloBleApplication
import com.example.cloble.ui.navigation.CloBleDestinations
import com.example.cloble.ui.navigation.CloBleNavGraph
import com.example.cloble.ui.theme.CloBleTheme
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddEditClothingPhotoUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var app: CloBleApplication

    @Before
    fun setup() {
        runBlocking {
            val context = ApplicationProvider.getApplicationContext<Context>()
            app = context.applicationContext as CloBleApplication
            app.container.authRepository.ensureSeeded()
            app.container.authRepository.loginDemoAccount(1L)
        }
    }

    @Test
    fun testPhotoSlotsAndPickButtonAreDisplayed() {
        composeTestRule.setContent {
            CloBleTheme {
                CloBleNavGraph(startDestination = CloBleDestinations.ADD_CLOTHING)
            }
        }

        composeTestRule.onNodeWithTag("photo_slot_main").assertIsDisplayed()
        composeTestRule.onNodeWithTag("photo_slot_detail").assertIsDisplayed()
        composeTestRule.onNodeWithTag("photo_slot_extra").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_pick_photo").assertIsDisplayed()

        val bitmap = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        val file = java.io.File("/sdcard/Download/06_add_clothing_screen.png")
        java.io.FileOutputStream(file).use { out ->
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out)
        }
    }
}
