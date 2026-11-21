package com.example.cloble.data.remote

import com.example.cloble.data.local.dao.OfflineChatCacheDao
import com.example.cloble.data.local.session.SessionPreferencesDataSource
import com.example.cloble.domain.model.CityPreset
import com.example.cloble.domain.model.DemoAccountSummary
import com.example.cloble.domain.model.UserProfile
import com.example.cloble.domain.repository.AuthRepository
import com.example.cloble.domain.result.AuthResult
import com.example.cloble.domain.result.ChangePasswordResult
import com.example.cloble.domain.result.DbFailure
import com.example.cloble.domain.result.RequestPasswordResetOtpResult
import com.example.cloble.domain.result.ResetPasswordResult
import com.example.cloble.domain.util.DistanceCalculator
import com.example.cloble.domain.util.PasswordHasher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
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
    private val offlineChatCacheDao: OfflineChatCacheDao? = null
) : AuthRepository {

    private suspend fun clearOfflineCacheIfAccountChanged(newUserId: Long) {
        val previousUserId = sessionPrefs.getLoggedInUserIdOnce()
        if (previousUserId != newUserId) {
            runCatching { offlineChatCacheDao?.clearAll() }
        }
    }

    override fun observeLoggedInUserId(): Flow<Long?> =
        combine(
            sessionPrefs.loggedInUserIdFlow,
            sessionPrefs.sessionTokenFlow
        ) { userId, token ->
            client.setSessionToken(token)
            if (userId != null && !token.isNullOrBlank()) userId else null
        }.distinctUntilChanged()

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

    private suspend fun fetchUserProfile(userId: Long): UserProfile? {
        return try {
            val userJson = client.getJsonObjectOrNull("users?id=eq.$userId") ?: return null
            val reviewsArr = client.getJsonArray("reviews?revieweeId=eq.$userId&select=rating")
            var sum = 0.0
            for (i in 0 until reviewsArr.length()) {
                sum += reviewsArr.getJSONObject(i).optDouble("rating", 0.0)
            }
            val count = reviewsArr.length()
            val avg = if (count > 0) sum / count else 0.0
            SupabaseMappers.parseUserProfile(userJson, avg, count)
        } catch (_: Exception) {
            null
        }
    }

    override fun getDemoAccounts(): Flow<List<DemoAccountSummary>> = flow {
        try {
            val usersArr = client.getJsonArray("users?order=id.asc")
            val list = mutableListOf<DemoAccountSummary>()
            for (i in 0 until usersArr.length()) {
                val u = usersArr.getJSONObject(i)
                val email = u.optString("email")
                val role = when (email.lowercase()) {
                    "raka@cloble.demo" -> "Pengirim & pemilik multi-item (Bandung)"
                    "naya@cloble.demo" -> "Penerima Interest & koleksi barter (Bandung)"
                    "bagas@cloble.demo" -> "0 pakaian barter & ulasan 2 bintang (Cimahi)"
                    "dinda@cloble.demo" -> "Filter jarak >50km & reputasi rendah (Jakarta)"
                    else -> "Akun pengguna"
                }
                list.add(
                    DemoAccountSummary(
                        id = u.optLong("id"),
                        name = u.optString("name"),
                        email = email,
                        roleDescription = role
                    )
                )
            }
            emit(list)
        } catch (_: Exception) {
            emit(emptyList())
        }
    }.flowOn(Dispatchers.IO)

    override suspend fun login(email: String, rawPassword: String): AuthResult {
        return try {
            val trimmedEmail = email.trim().lowercase()
            val challengeParams = JSONObject().apply {
                put("p_email", trimmedEmail)
            }
            val challengeResp = client.rpc("rpc_get_login_challenge", challengeParams)
            if (challengeResp.optString("code") != "SUCCESS") {
                return AuthResult.InvalidCredentials
            }

            val iterations = challengeResp.optInt("iterations", PasswordHasher.DEFAULT_ITERATIONS)
            val saltHex = challengeResp.optString("saltHex")
            if (saltHex.isBlank()) {
                return AuthResult.InvalidCredentials
            }

            val computedHash = PasswordHasher.hashWithSaltHex(rawPassword, saltHex, iterations)
                ?: return AuthResult.InvalidCredentials

            val sessionParams = JSONObject().apply {
                put("p_user_id", JSONObject.NULL)
                put("p_password_hash", computedHash)
                put("p_is_demo", false)
                put("p_email", trimmedEmail)
            }
            val sessionResp = client.rpc("rpc_create_session", sessionParams)
            if (sessionResp.optString("code") != "SUCCESS") {
                return AuthResult.InvalidCredentials
            }

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
        } catch (_: Exception) {
            AuthResult.DatabaseError(DbFailure.UnexpectedDatabaseError(CONNECTION_ERROR_MESSAGE))
        }
    }

    override suspend fun loginDemoAccount(userId: Long): AuthResult {
        return try {
            val sessionParams = JSONObject().apply {
                put("p_user_id", userId)
                put("p_is_demo", true)
            }
            val sessionResp = client.rpc("rpc_create_session", sessionParams)
            if (sessionResp.optString("code") != "SUCCESS") {
                return AuthResult.InvalidCredentials
            }

            val token = sessionResp.optString("sessionToken")
            clearOfflineCacheIfAccountChanged(userId)
            client.setSessionToken(token)
            sessionPrefs.setSession(userId, token)
            invalidator.invalidateAll()
            AuthResult.Success(userId)
        } catch (_: Exception) {
            AuthResult.DatabaseError(DbFailure.UnexpectedDatabaseError(CONNECTION_ERROR_MESSAGE))
        }
    }

    override suspend fun requestPasswordResetOtp(email: String): RequestPasswordResetOtpResult {
        val trimmedEmail = email.trim().lowercase()
        if (trimmedEmail.isEmpty() || !trimmedEmail.contains("@")) {
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
                    resp.optString("message", "Layanan email OTP Gmail SMTP belum dikonfigurasi oleh admin.")
                )
                "VALIDATION_FAILED" -> RequestPasswordResetOtpResult.ValidationFailed(
                    resp.optString("message", "Format email tidak valid")
                )
                else -> RequestPasswordResetOtpResult.DatabaseError(
                    DbFailure.UnexpectedDatabaseError("Gagal memproses permintaan OTP. Silakan coba lagi.")
                )
            }
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
        if (trimmedEmail.isEmpty() || !trimmedEmail.contains("@")) {
            return ResetPasswordResult.ValidationFailed("Format email tidak valid")
        }
        if (!cleanOtp.matches(Regex("^[0-9]{6}$"))) {
            return ResetPasswordResult.ValidationFailed("Kode OTP harus 6 digit angka")
        }
        if (newRawPassword.length < 6) {
            return ResetPasswordResult.ValidationFailed("Password minimal 6 karakter")
        }

        return try {
            val newHash = PasswordHasher.hash(newRawPassword)
            val params = JSONObject().apply {
                put("p_email", trimmedEmail)
                put("p_otp_code", cleanOtp)
                put("p_new_password_hash", newHash)
                put("p_now_millis", System.currentTimeMillis())
            }
            val resp = client.rpc("rpc_verify_otp_and_reset_password", params)
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
        if (newRawPassword.length < 6) {
            return ChangePasswordResult.ValidationFailed("Password baru minimal 6 karakter")
        }
        if (currentRawPassword == newRawPassword) {
            return ChangePasswordResult.ValidationFailed("Password baru tidak boleh sama dengan password saat ini")
        }

        return try {
            val challengeResp = client.rpc("rpc_get_current_password_challenge", JSONObject())
            if (challengeResp.optString("code") != "SUCCESS") {
                return ChangePasswordResult.Unauthorized
            }
            val iterations = challengeResp.optInt("iterations", PasswordHasher.DEFAULT_ITERATIONS)
            val saltHex = challengeResp.optString("saltHex")
            if (saltHex.isBlank()) {
                return ChangePasswordResult.Unauthorized
            }

            val currentHash = PasswordHasher.hashWithSaltHex(currentRawPassword, saltHex, iterations)
                ?: return ChangePasswordResult.InvalidCurrentPassword
            val newHash = PasswordHasher.hash(newRawPassword)

            val params = JSONObject().apply {
                put("p_current_password_hash", currentHash)
                put("p_new_password_hash", newHash)
                put("p_now_millis", System.currentTimeMillis())
            }
            val resp = client.rpc("rpc_change_password_authenticated", params)
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

        if (trimmedName.isEmpty()) return AuthResult.ValidationFailed("Nama tidak boleh kosong")
        if (trimmedEmail.isEmpty() || !trimmedEmail.contains("@")) return AuthResult.ValidationFailed("Format email tidak valid")
        if (rawPassword.length < 6) return AuthResult.ValidationFailed("Password minimal 6 karakter")

        return try {
            val resolvedCity: String
            val resolvedLat: Double
            val resolvedLon: Double
            if (latitude != null && longitude != null) {
                resolvedCity = city.trim().ifEmpty { CityPreset.BANDUNG.displayName }
                val effective = DistanceCalculator.resolveEffectiveCoordinates(resolvedCity, latitude, longitude)
                resolvedLat = effective.first
                resolvedLon = effective.second
            } else {
                val preset = CityPreset.fromDisplayName(city)
                resolvedCity = if (city.isNotBlank() && city != preset.displayName) city.trim() else preset.displayName
                val effective = DistanceCalculator.resolveEffectiveCoordinates(
                    resolvedCity,
                    preset.latitude,
                    preset.longitude
                )
                resolvedLat = effective.first
                resolvedLon = effective.second
            }
            val hash = PasswordHasher.hash(rawPassword)
            val now = System.currentTimeMillis()

            val regParams = JSONObject().apply {
                put("p_name", trimmedName)
                put("p_email", trimmedEmail)
                put("p_password_hash", hash)
                put("p_city", resolvedCity)
                put("p_latitude", resolvedLat)
                put("p_longitude", resolvedLon)
                put("p_now_millis", now)
            }

            val regResp = client.rpc("rpc_register_user", regParams)
            when (regResp.optString("code")) {
                "SUCCESS" -> {
                    val newUserId = regResp.optLong("userId")
                    val token = regResp.optString("sessionToken")
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
        } catch (_: Exception) {
            AuthResult.DatabaseError(DbFailure.UnexpectedDatabaseError(CONNECTION_ERROR_MESSAGE))
        }
    }

    override suspend fun logout() {
        try {
            val fcmToken = sessionPrefs.getFcmTokenOnce()
            if (!fcmToken.isNullOrBlank()) {
                client.rpc("rpc_unregister_fcm_token", JSONObject().put("p_token", fcmToken))
            }
        } catch (_: Exception) {
        }
        try {
            client.rpc("rpc_logout_session", JSONObject())
        } catch (_: Exception) {
        }
        client.setSessionToken(null)
        sessionPrefs.clearSession()
        runCatching { offlineChatCacheDao?.clearAll() }
        invalidator.invalidateAll()
    }

    override suspend fun ensureSeeded() {
    }

    private companion object {
        const val CONNECTION_ERROR_MESSAGE = "Terjadi gangguan koneksi ke server. Silakan coba lagi."
    }
}
