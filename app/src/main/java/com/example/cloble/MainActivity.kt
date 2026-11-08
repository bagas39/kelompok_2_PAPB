package com.example.cloble

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.cloble.ui.navigation.CloBleNavGraph
import com.example.cloble.ui.theme.CloBleTheme
import com.example.cloble.util.SystemNotificationHelper
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->

        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        SystemNotificationHelper.initNotificationChannel(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!SystemNotificationHelper.hasNotificationPermission(this)) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        observeIncomingSystemNotifications()

        setContent {
            CloBleTheme {
                CloBleNavGraph()
            }
        }
    }

    private fun observeIncomingSystemNotifications() {
        val app = application as? CloBleApplication ?: return
        val authRepo = app.container.authRepository
        val matchRepo = app.container.discoveryMatchRepository

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                authRepo.observeLoggedInUserId().collectLatest { userId ->
                    if (userId != null) {
                        var isInitialLoad = true
                        val seenIds = mutableSetOf<Long>()
                        matchRepo.observeNotifications(userId).collect { items ->
                            if (isInitialLoad) {
                                items.forEach { seenIds.add(it.id) }
                                isInitialLoad = false
                            } else {
                                val newItems = items.filter { !it.isRead && !seenIds.contains(it.id) }
                                for (item in newItems) {
                                    seenIds.add(item.id)
                                    SystemNotificationHelper.showNotification(
                                        context = this@MainActivity,
                                        id = item.id.toInt(),
                                        title = item.title,
                                        body = item.body,
                                        relatedMatchId = item.relatedMatchId
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
