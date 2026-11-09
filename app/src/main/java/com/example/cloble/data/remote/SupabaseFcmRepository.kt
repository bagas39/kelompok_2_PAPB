package com.example.cloble.data.remote

import android.os.Build
import com.example.cloble.data.local.session.SessionPreferencesDataSource
import com.example.cloble.domain.repository.FcmRepository
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

class SupabaseFcmRepository(
    private val client: SupabaseClient,
    private val sessionPrefs: SessionPreferencesDataSource
) : FcmRepository {

    override suspend fun registerCurrentToken(userId: Long?) {
        val currentUserId = userId ?: sessionPrefs.getLoggedInUserIdOnce() ?: return
        val token = sessionPrefs.getFcmTokenOnce() ?: fetchFirebaseToken() ?: return

        sessionPrefs.setFcmToken(token)

        try {
            val body = JSONObject().apply {
                put("p_token", token)
                put("p_device_info", "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})")
            }
            client.rpc("rpc_register_fcm_token", body)
        } catch (_: Exception) {
        }
    }

    override suspend fun unregisterCurrentToken() {
        val token = sessionPrefs.getFcmTokenOnce() ?: return
        try {
            val body = JSONObject().apply {
                put("p_token", token)
            }
            client.rpc("rpc_unregister_fcm_token", body)
        } catch (_: Exception) {
        }
    }

    override suspend fun onNewToken(token: String) {
        sessionPrefs.setFcmToken(token)
        val currentUserId = sessionPrefs.getLoggedInUserIdOnce()
        if (currentUserId != null) {
            try {
                val body = JSONObject().apply {
                    put("p_token", token)
                    put("p_device_info", "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})")
                }
                client.rpc("rpc_register_fcm_token", body)
            } catch (_: Exception) {
            }
        }
    }

    private suspend fun fetchFirebaseToken(): String? =
        suspendCancellableCoroutine { continuation ->
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    continuation.resume(task.result)
                } else {
                    continuation.resume(null)
                }
            }
        }
}
