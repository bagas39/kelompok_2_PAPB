package com.example.cloble.service

import com.example.cloble.CloBleApplication
import com.example.cloble.util.SystemNotificationHelper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class CloBleFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val app = applicationContext as? CloBleApplication ?: return
        serviceScope.launch {
            app.container.fcmRepository.onNewToken(token)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        val title = remoteMessage.data["title"]
            ?: remoteMessage.notification?.title
            ?: "CloBle"

        val body = remoteMessage.data["body"]
            ?: remoteMessage.notification?.body
            ?: ""

        if (title.isBlank() && body.isBlank()) return

        val destination = remoteMessage.data["destination"] ?: "notifications"
        val matchId = remoteMessage.data["matchId"]?.toLongOrNull()
        val notifLongId = remoteMessage.data["notificationId"]?.toLongOrNull()
            ?: remoteMessage.data["id"]?.toLongOrNull()
        val targetUserId = remoteMessage.data["userId"]?.toLongOrNull()
        val notifId = when (destination) {
            "chat" -> {
                val baseId = matchId ?: notifLongId
                if (baseId != null && baseId > 0L) {
                    (1_000_000L + (baseId % 1_000_000L)).toInt()
                } else {
                    (destination + title + body).hashCode()
                }
            }
            else -> notifLongId?.toInt() ?: (destination + title + body + (matchId ?: 0L)).hashCode()
        }

        val app = applicationContext as? CloBleApplication
        serviceScope.launch {
            val loggedInUserId = app?.container?.authRepository?.observeLoggedInUserId()?.firstOrNull()
            if (loggedInUserId == null || (targetUserId != null && targetUserId != loggedInUserId)) {
                return@launch
            }

            val shouldSkipNotification = destination == "notifications" &&
                notifLongId != null &&
                notifLongId > 0L &&
                SystemNotificationHelper.hasBeenNotified(applicationContext, loggedInUserId, notifLongId)

            if (!shouldSkipNotification) {
                val posted = SystemNotificationHelper.showNotification(
                    context = applicationContext,
                    id = notifId,
                    title = title,
                    body = body,
                    relatedMatchId = matchId,
                    destination = destination,
                    notificationId = if (destination == "notifications") notifLongId else null,
                    userId = loggedInUserId
                )
                if (posted &&
                    destination == "notifications" &&
                    notifLongId != null &&
                    notifLongId > 0L
                ) {
                    SystemNotificationHelper.markAsNotified(applicationContext, loggedInUserId, notifLongId)
                }
            }

            app.container.dataInvalidator.invalidateNotifications()
            app.container.dataInvalidator.invalidateChat()
            app.container.dataInvalidator.invalidateDiscovery()
            app.container.dataInvalidator.invalidateWardrobe()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
