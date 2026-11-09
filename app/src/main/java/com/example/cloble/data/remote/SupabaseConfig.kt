package com.example.cloble.data.remote

import com.example.cloble.BuildConfig

object SupabaseConfig {
    val baseUrl: String = BuildConfig.SUPABASE_URL.trimEnd('/')
    val apiKey: String = BuildConfig.SUPABASE_KEY
    val restUrl: String get() = "$baseUrl/rest/v1"
    val storageUrl: String get() = "$baseUrl/storage/v1"
    val realtimeWsUrl: String
        get() {
            val host = baseUrl.removePrefix("https://").removePrefix("http://")
            return "wss://$host/realtime/v1"
        }
    const val PHOTO_BUCKET: String = "clothing-photos"

    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank()
}
