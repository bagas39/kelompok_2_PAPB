package com.example.cloble.domain.repository

interface FcmRepository {
    suspend fun registerCurrentToken(userId: Long? = null)
    suspend fun unregisterCurrentToken()
    suspend fun onNewToken(token: String)
}
