package com.example.cloble

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.cloble.ui.navigation.CloBleDestinations
import com.example.cloble.ui.navigation.CloBleNavGraph
import com.example.cloble.ui.theme.CloBleTheme
import com.example.cloble.util.SystemNotificationHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var pendingRoute by mutableStateOf<String?>(null)
    private var notificationObserverJob: Job? = null

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted && notificationObserverJob?.isActive != true) {
                observeIncomingSystemNotifications()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleNotificationIntent(intent)

        SystemNotificationHelper.initNotificationChannel(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!SystemNotificationHelper.hasNotificationPermission(this)) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        observeIncomingSystemNotifications()

        setContent {
            CloBleTheme {
                CloBleNavGraph(
                    pendingRoute = pendingRoute,
                    onRouteHandled = { pendingRoute = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleNotificationIntent(intent)
    }

    private fun handleNotificationIntent(intent: Intent?) {
        if (intent == null) return
        val destination = intent.getStringExtra("destination")

        fun readLongExtra(key: String): Long {
            val asLong = intent.getLongExtra(key, -1L)
            if (asLong > 0L) return asLong
            return intent.getStringExtra(key)?.toLongOrNull() ?: -1L
        }

        val matchId = readLongExtra("matchId")
        val notificationId = readLongExtra("notificationId").takeIf { it > 0L } ?: readLongExtra("id")
        val targetUserId = readLongExtra("userId")
        if (notificationId > 0L && targetUserId > 0L) {
            SystemNotificationHelper.markAsNotified(this, targetUserId, notificationId)
        }

        if (destination == "chat" && matchId > 0) {
            pendingRoute = CloBleDestinations.conversationRoute(matchId)
        } else if (destination == "notifications") {
            pendingRoute = CloBleDestinations.NOTIFICATIONS
        }
    }

    private fun observeIncomingSystemNotifications() {
        val app = application as? CloBleApplication ?: return
        val authRepo = app.container.authRepository
        val matchRepo = app.container.discoveryMatchRepository

        notificationObserverJob?.cancel()
        notificationObserverJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                authRepo.observeLoggedInUserId().collectLatest { userId ->
                    if (userId != null) {
                        launch {
                            app.container.fcmRepository.registerCurrentToken(userId)
                        }
                        var hasProcessedInitialBatch = false
                        matchRepo.observeNotifications(userId).collect { items ->
                            val unreadItems = items.filter { !it.isRead }
                            if (!hasProcessedInitialBatch) {
                                hasProcessedInitialBatch = true
                                for (item in unreadItems) {
                                    if (!SystemNotificationHelper.hasBeenNotified(this@MainActivity, userId, item.id)) {
                                        SystemNotificationHelper.markAsNotified(this@MainActivity, userId, item.id)
                                    }
                                }
                            } else {
                                for (item in unreadItems) {
                                    if (!SystemNotificationHelper.hasBeenNotified(this@MainActivity, userId, item.id)) {
                                        val posted = SystemNotificationHelper.showNotification(
                                            context = this@MainActivity,
                                            id = item.id.toInt(),
                                            title = item.title,
                                            body = item.body,
                                            relatedMatchId = item.relatedMatchId,
                                            destination = "notifications",
                                            notificationId = item.id,
                                            userId = userId
                                        )
                                        if (posted) {
                                            SystemNotificationHelper.markAsNotified(this@MainActivity, userId, item.id)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
