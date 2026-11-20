package com.example.cloble.domain.repository

import com.example.cloble.domain.model.DemoAccountSummary
import com.example.cloble.domain.model.UserProfile
import com.example.cloble.domain.result.AuthResult
import com.example.cloble.domain.result.ResetPasswordResult
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    fun observeLoggedInUserId(): Flow<Long?>
    fun observeCurrentUser(): Flow<UserProfile?>
    fun getDemoAccounts(): Flow<List<DemoAccountSummary>>

    suspend fun login(email: String, rawPassword: String): AuthResult
    suspend fun loginDemoAccount(userId: Long): AuthResult
    suspend fun resetPassword(email: String, newRawPassword: String): ResetPasswordResult
    suspend fun register(
        name: String,
        email: String,
        rawPassword: String,
        city: String = "Bandung, Jawa Barat",
        latitude: Double? = null,
        longitude: Double? = null
    ): AuthResult

    suspend fun logout()
    suspend fun ensureSeeded()
}
