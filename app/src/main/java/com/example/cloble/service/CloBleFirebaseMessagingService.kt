package com.example.cloble.service

import com.example.cloble.CloBleApplication
import com.example.cloble.util.SystemNotificationHelper
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
        val notifId = remoteMessage.data["id"]?.toIntOrNull()
            ?: (System.currentTimeMillis() % 100000).toInt()

        SystemNotificationHelper.showNotification(
            context = applicationContext,
            id = notifId,
            title = title,
            body = body,
            relatedMatchId = matchId,
            destination = destination
        )

        val app = applicationContext as? CloBleApplication ?: return
        app.container.dataInvalidator.invalidateNotifications()
        app.container.dataInvalidator.invalidateChat()
        app.container.dataInvalidator.invalidateDiscovery()
        app.container.dataInvalidator.invalidateWardrobe()
    }
}
