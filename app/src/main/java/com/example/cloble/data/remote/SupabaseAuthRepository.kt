package com.example.cloble.data.remote

import com.example.cloble.data.local.dao.OfflineChatCacheDao
import com.example.cloble.data.local.session.SessionPreferencesDataSource
import com.example.cloble.data.local.session.StoredSession
import com.example.cloble.domain.model.UserProfile
import com.example.cloble.domain.repository.AuthRepository
import com.example.cloble.domain.result.AuthResult
import com.example.cloble.domain.result.ChangePasswordResult
import com.example.cloble.domain.result.DbFailure
import com.example.cloble.domain.result.RequestPasswordResetOtpResult
import com.example.cloble.domain.result.ResetPasswordResult
import com.example.cloble.domain.util.DistanceCalculator
import com.example.cloble.domain.util.PasswordPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import org.json.JSONObject

@OptIn(ExperimentalCoroutinesApi::class)
class SupabaseAuthRepository(
    private val client: SupabaseClient,
    private val sessionPrefs: SessionPreferencesDataSource,
    private val invalidator: SupabaseDataInvalidator,
    private val offlineChatCacheDao: OfflineChatCacheDao? = null,
    private val realtimeClient: SupabaseRealtimeClient? = null
) : AuthRepository {

    private suspend fun clearOfflineCacheIfAccountChanged(newUserId: Long) {
        val previousUserId = sessionPrefs.getLoggedInUserIdOnce()
        if (previousUserId != newUserId) {
            try {
                offlineChatCacheDao?.clearAll()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
        }
    }

    override fun observeLoggedInUserId(): Flow<Long?> =
        sessionPrefs.sessionFlow
            .map { session ->
                resolveAuthenticatedSessionUserId(session) { token ->
                    client.setSessionToken(token)
                }
            }
            .distinctUntilChanged()

    override fun observeCurrentUser(): Flow<UserProfile?> =
        observeLoggedInUserId().flatMapLatest { userId ->
            if (userId == null) {
                flowOf(null)
            } else {
                invalidator.userTrigger
                    .onStart { emit(Unit) }
                    .map { fetchUserProfile(userId) }
                    .flowOn(Dispatchers.IO)
            }
        }

    private suspend fun fetchUserProfile(userId: Long): UserProfile? =
        fetchUserProfileViaRpc(client, userId)

    override suspend fun login(email: String, rawPassword: String): AuthResult {
        val trimmedEmail = email.trim().lowercase()
        if (trimmedEmail.isEmpty() || rawPassword.isEmpty()) {
            return AuthResult.InvalidCredentials
        }

        return try {
            val payload = JSONObject().apply {
                put("action", "login")
                put("email", trimmedEmail)
                put("password", rawPassword)
            }
            val sessionResp = client.invokeEdgeFunction("auth-service", payload)
            when (sessionResp.optString("code")) {
                "SUCCESS" -> {
                    val userId = sessionResp.optLong("userId", 0L)
                    val token = sessionResp.optString("sessionToken")
                    if (userId <= 0L || token.isBlank()) {
                        return AuthResult.InvalidCredentials
                    }
                    clearOfflineCacheIfAccountChanged(userId)
                    client.setSessionToken(token)
                    sessionPrefs.setSession(userId, token)
                    invalidator.invalidateAll()
                    AuthResult.Success(userId)
                }
                "RATE_LIMITED" -> AuthResult.RateLimited(
                    sessionResp.optString("message", "Terlalu banyak percobaan login gagal. Coba lagi nanti.")
                )
                "ERROR", "SERVER_MISCONFIGURED" -> AuthResult.DatabaseError(
                    DbFailure.UnexpectedDatabaseError(CONNECTION_ERROR_MESSAGE)
                )
                else -> AuthResult.InvalidCredentials
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            AuthResult.DatabaseError(DbFailure.UnexpectedDatabaseError(CONNECTION_ERROR_MESSAGE))
        }
    }

    override suspend fun requestPasswordResetOtp(email: String): RequestPasswordResetOtpResult {
        val trimmedEmail = email.trim().lowercase()
        if (!PasswordPolicy.isValidEmail(trimmedEmail)) {
            return RequestPasswordResetOtpResult.ValidationFailed("Format email tidak valid")
        }

        return try {
            val resp = client.invokeEdgeFunction(
                functionName = "send-password-reset-otp",
                body = JSONObject().put("email", trimmedEmail)
            )
            when (resp.optString("code")) {
                "SUCCESS", "EMAIL_NOT_FOUND" -> RequestPasswordResetOtpResult.Success
                "RATE_LIMITED" -> RequestPasswordResetOtpResult.RateLimited(
                    resp.optString("message", "Tunggu 30 detik sebelum meminta kode OTP baru.")
                )
                "SMTP_NOT_CONFIGURED" -> RequestPasswordResetOtpResult.SmtpNotConfigured(
                    resp.optString("message", "Layanan email OTP sedang tidak tersedia. Silakan coba lagi nanti.")
                )
                "VALIDATION_FAILED" -> RequestPasswordResetOtpResult.ValidationFailed(
                    resp.optString("message", "Format email tidak valid")
                )
                else -> RequestPasswordResetOtpResult.DatabaseError(
                    DbFailure.UnexpectedDatabaseError("Gagal memproses permintaan OTP. Silakan coba lagi.")
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            RequestPasswordResetOtpResult.DatabaseError(DbFailure.UnexpectedDatabaseError(CONNECTION_ERROR_MESSAGE))
        }
    }

    override suspend fun verifyOtpAndResetPassword(
        email: String,
        otpCode: String,
        newRawPassword: String
    ): ResetPasswordResult {
        val trimmedEmail = email.trim().lowercase()
        val cleanOtp = otpCode.trim()
        if (!PasswordPolicy.isValidEmail(trimmedEmail)) {
            return ResetPasswordResult.ValidationFailed("Format email tidak valid")
        }
        if (!cleanOtp.matches(Regex("^[0-9]{6}$"))) {
            return ResetPasswordResult.ValidationFailed("Kode OTP harus 6 digit angka")
        }
        PasswordPolicy.validateNewPassword(newRawPassword)?.let { msg ->
            return ResetPasswordResult.ValidationFailed(msg)
        }

        return try {
            val payload = JSONObject().apply {
                put("action", "verify-reset-otp")
                put("email", trimmedEmail)
                put("otpCode", cleanOtp)
                put("newPassword", newRawPassword)
            }
            val resp = client.invokeEdgeFunction("auth-service", payload)
            when (resp.optString("code")) {
                "SUCCESS" -> ResetPasswordResult.Success
                "OTP_INVALID" -> ResetPasswordResult.InvalidOtp(resp.optInt("remainingAttempts", 0))
                "EMAIL_NOT_FOUND", "OTP_EXPIRED_OR_NOT_FOUND" -> ResetPasswordResult.OtpExpiredOrNotFound
                "OTP_MAX_ATTEMPTS_EXCEEDED" -> ResetPasswordResult.OtpMaxAttemptsExceeded
                "VALIDATION_FAILED" -> ResetPasswordResult.ValidationFailed(
                    resp.optString("message", "Validasi gagal")
                )
                else -> ResetPasswordResult.DatabaseError(
                    DbFailure.UnexpectedDatabaseError("Gagal mereset password")
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ResetPasswordResult.DatabaseError(DbFailure.UnexpectedDatabaseError(CONNECTION_ERROR_MESSAGE))
        }
    }

    override suspend fun changePassword(
        currentRawPassword: String,
        newRawPassword: String
    ): ChangePasswordResult {
        if (currentRawPassword.isEmpty()) {
            return ChangePasswordResult.ValidationFailed("Password saat ini wajib diisi")
        }
        PasswordPolicy.validateNewPassword(newRawPassword)?.let { msg ->
            return ChangePasswordResult.ValidationFailed(msg)
        }
        if (currentRawPassword == newRawPassword) {
            return ChangePasswordResult.ValidationFailed("Password baru tidak boleh sama dengan password saat ini")
        }

        return try {
            val payload = JSONObject().apply {
                put("action", "change-password")
                put("currentPassword", currentRawPassword)
                put("newPassword", newRawPassword)
            }
            val resp = client.invokeEdgeFunction("auth-service", payload)
            when (resp.optString("code")) {
                "SUCCESS" -> ChangePasswordResult.Success
                "INVALID_CURRENT_PASSWORD" -> ChangePasswordResult.InvalidCurrentPassword
                "UNAUTHORIZED" -> ChangePasswordResult.Unauthorized
                "VALIDATION_FAILED" -> ChangePasswordResult.ValidationFailed(
                    resp.optString("message", "Validasi gagal")
                )
                else -> ChangePasswordResult.DatabaseError(
                    DbFailure.UnexpectedDatabaseError("Gagal memperbarui password")
                )
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            ChangePasswordResult.DatabaseError(DbFailure.UnexpectedDatabaseError(CONNECTION_ERROR_MESSAGE))
        }
    }

    override suspend fun register(
        name: String,
        email: String,
        rawPassword: String,
        city: String,
        latitude: Double?,
        longitude: Double?
    ): AuthResult {
        val trimmedEmail = email.trim().lowercase()
        val trimmedName = name.trim()
        val trimmedCity = city.trim()

        if (trimmedName.isEmpty()) return AuthResult.ValidationFailed("Nama tidak boleh kosong")
        if (!PasswordPolicy.isValidEmail(trimmedEmail)) return AuthResult.ValidationFailed("Format email tidak valid")
        if (trimmedCity.isEmpty() || latitude == null || longitude == null) {
            return AuthResult.ValidationFailed("Lokasi domisili wajib dipilih")
        }
        PasswordPolicy.validateNewPassword(rawPassword)?.let { msg ->
            return AuthResult.ValidationFailed(msg)
        }

        return try {
            val effective = DistanceCalculator.resolveEffectiveCoordinates(trimmedCity, latitude, longitude)
            val payload = JSONObject().apply {
                put("action", "register")
                put("name", trimmedName)
                put("email", trimmedEmail)
                put("password", rawPassword)
                put("city", trimmedCity)
                put("latitude", effective.first)
                put("longitude", effective.second)
            }

            val regResp = client.invokeEdgeFunction("auth-service", payload)
            when (regResp.optString("code")) {
                "SUCCESS" -> {
                    val newUserId = regResp.optLong("userId", 0L)
                    val token = regResp.optString("sessionToken")
                    if (newUserId <= 0L || token.isBlank()) {
                        return AuthResult.DatabaseError(DbFailure.UnexpectedDatabaseError("Gagal registrasi"))
                    }
                    clearOfflineCacheIfAccountChanged(newUserId)
                    client.setSessionToken(token)
                    sessionPrefs.setSession(newUserId, token)
                    invalidator.invalidateAll()
                    AuthResult.Success(newUserId)
                }
                "EMAIL_ALREADY_REGISTERED" -> AuthResult.EmailAlreadyRegistered
                "VALIDATION_FAILED" -> AuthResult.ValidationFailed(regResp.optString("message", "Validasi gagal"))
                else -> AuthResult.DatabaseError(DbFailure.UnexpectedDatabaseError("Gagal registrasi"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            AuthResult.DatabaseError(DbFailure.UnexpectedDatabaseError(CONNECTION_ERROR_MESSAGE))
        }
    }

    private suspend fun clearInvalidLocalSession() {
        realtimeClient?.disconnect()
        client.setSessionToken(null)
        sessionPrefs.clearSession()
        try {
            offlineChatCacheDao?.clearAll()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
        invalidator.invalidateAll()
    }

    override suspend fun validateSession(): Boolean = validateSessionWithDeps(
        getLocalUserId = { sessionPrefs.getLoggedInUserIdOnce() },
        getLocalToken = { sessionPrefs.sessionTokenFlow.first() },
        setClientToken = { token -> client.setSessionToken(token) },
        clearLocalSession = { clearInvalidLocalSession() },
        callRpc = { functionName, params -> client.rpc(functionName, params) }
    )

    override suspend fun logout() {
        logoutWithDeps(
            getClientToken = { client.getSessionToken() },
            getStoredToken = { sessionPrefs.sessionTokenFlow.firstOrNull() },
            setClientToken = { token -> client.setSessionToken(token) },
            getFcmToken = { sessionPrefs.getFcmTokenOnce() },
            callRpc = { functionName, params -> client.rpc(functionName, params) },
            clearLocalSession = { clearInvalidLocalSession() },
            onDisconnectRealtime = { realtimeClient?.disconnect() }
        )
    }

    private companion object {
        const val CONNECTION_ERROR_MESSAGE = "Terjadi gangguan koneksi ke server. Silakan coba lagi."
    }
}

internal fun resolveAuthenticatedSessionUserId(
    session: StoredSession,
    setClientToken: (String?) -> Unit
): Long? {
    val activeToken = if (session.userId != null && !session.sessionToken.isNullOrBlank()) {
        session.sessionToken
    } else {
        null
    }
    setClientToken(activeToken)
    return if (activeToken != null) session.userId else null
}

internal suspend fun logoutWithDeps(
    getClientToken: () -> String?,
    getStoredToken: suspend () -> String?,
    setClientToken: (String?) -> Unit,
    getFcmToken: suspend () -> String?,
    callRpc: suspend (String, JSONObject) -> JSONObject,
    clearLocalSession: suspend () -> Unit,
    onDisconnectRealtime: () -> Unit = {}
) {
    if (getClientToken().isNullOrBlank()) {
        val storedToken = try {
            getStoredToken()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        if (!storedToken.isNullOrBlank()) {
            setClientToken(storedToken)
        }
    }

    if (!getClientToken().isNullOrBlank()) {
        try {
            val fcmToken = getFcmToken()
            if (!fcmToken.isNullOrBlank()) {
                callRpc("rpc_unregister_fcm_token", JSONObject().put("p_token", fcmToken))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
        try {
            callRpc("rpc_logout_session", JSONObject())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    onDisconnectRealtime()
    clearLocalSession()
}

internal suspend fun validateSessionWithDeps(
    getLocalUserId: suspend () -> Long?,
    getLocalToken: suspend () -> String?,
    setClientToken: (String?) -> Unit,
    clearLocalSession: suspend () -> Unit,
    callRpc: suspend (String, JSONObject) -> JSONObject
): Boolean {
    val localUserId = getLocalUserId() ?: return false
    val localToken = getLocalToken()
    if (localToken.isNullOrBlank()) {
        clearLocalSession()
        return false
    }

    setClientToken(localToken)
    return try {
        val resp = callRpc("rpc_validate_session", JSONObject())
        when (resp.optString("code")) {
            "VALID" -> {
                val serverUserId = resp.optLong("userId", -1L)
                if (serverUserId == localUserId) {
                    true
                } else {
                    clearLocalSession()
                    false
                }
            }
            "INVALID_SESSION", "UNAUTHORIZED" -> {
                clearLocalSession()
                false
            }
            else -> true
        }
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        true
    }
}
