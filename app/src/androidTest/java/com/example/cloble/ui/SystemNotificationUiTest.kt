package com.example.cloble.ui

import android.app.NotificationManager
import android.content.Context
import android.os.Build
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
import com.example.cloble.util.SystemNotificationHelper
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SystemNotificationUiTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var app: CloBleApplication
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        app = context.applicationContext as CloBleApplication
        runBlocking {
            app.container.authRepository.ensureSeeded()
            app.container.authRepository.loginDemoAccount(1L)
        }
    }

    @Test
    fun testNotificationChannelCreated() {
        SystemNotificationHelper.initNotificationChannel(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = manager.getNotificationChannel(SystemNotificationHelper.CHANNEL_ID)
            assertNotNull("Channel notifikasi harus terdaftar di sistem Android", channel)
            assertEquals(SystemNotificationHelper.CHANNEL_NAME, channel.name)
            assertEquals(NotificationManager.IMPORTANCE_HIGH, channel.importance)
        }
    }

    @Test
    fun testNotificationsScreenBellActionButton() {
        composeTestRule.setContent {
            CloBleTheme {
                CloBleNavGraph(startDestination = CloBleDestinations.NOTIFICATIONS)
            }
        }

        composeTestRule.onNodeWithTag("btn_test_system_notification").assertIsDisplayed()
        composeTestRule.onNodeWithTag("btn_test_system_notification").performClick()
    }
}
