package com.example.cloble.data.remote

import com.example.cloble.data.local.session.SessionPreferencesDataSource
import com.example.cloble.domain.model.CityPreset
import com.example.cloble.domain.model.DemoAccountSummary
import com.example.cloble.domain.model.UserProfile
import com.example.cloble.domain.repository.AuthRepository
import com.example.cloble.domain.result.AuthResult
import com.example.cloble.domain.result.DbFailure
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
    private val invalidator: SupabaseDataInvalidator
) : AuthRepository {

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

            val storedHash = challengeResp.optString("passwordHash")
            val userId = challengeResp.optLong("userId")
            val matches = PasswordHasher.verify(rawPassword, storedHash)
            if (!matches) {
                return AuthResult.InvalidCredentials
            }

            val sessionParams = JSONObject().apply {
                put("p_user_id", userId)
                put("p_password_hash", storedHash)
                put("p_is_demo", false)
            }
            val sessionResp = client.rpc("rpc_create_session", sessionParams)
            if (sessionResp.optString("code") != "SUCCESS") {
                return AuthResult.InvalidCredentials
            }

            val token = sessionResp.optString("sessionToken")
            client.setSessionToken(token)
            sessionPrefs.setSession(userId, token)
            invalidator.invalidateAll()
            AuthResult.Success(userId)
        } catch (e: Exception) {
            AuthResult.DatabaseError(DbFailure.UnexpectedDatabaseError(e.message))
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
            client.setSessionToken(token)
            sessionPrefs.setSession(userId, token)
            invalidator.invalidateAll()
            AuthResult.Success(userId)
        } catch (e: Exception) {
            AuthResult.DatabaseError(DbFailure.UnexpectedDatabaseError(e.message))
        }
    }

    override suspend fun resetPassword(email: String, newRawPassword: String): ResetPasswordResult {
        val trimmedEmail = email.trim().lowercase()
        if (trimmedEmail.isEmpty() || !trimmedEmail.contains("@")) {
            return ResetPasswordResult.ValidationFailed("Format email tidak valid")
        }
        if (newRawPassword.length < 6) {
            return ResetPasswordResult.ValidationFailed("Password minimal 6 karakter")
        }

        return try {
            val newHash = PasswordHasher.hash(newRawPassword)
            val params = JSONObject().apply {
                put("p_email", trimmedEmail)
                put("p_new_password_hash", newHash)
                put("p_now_millis", System.currentTimeMillis())
            }
            val resp = client.rpc("rpc_reset_password", params)
            when (resp.optString("code")) {
                "SUCCESS" -> ResetPasswordResult.Success
                "EMAIL_NOT_FOUND" -> ResetPasswordResult.EmailNotFound
                "VALIDATION_FAILED" -> ResetPasswordResult.ValidationFailed(
                    resp.optString("message", "Validasi gagal")
                )
                else -> ResetPasswordResult.DatabaseError(
                    DbFailure.UnexpectedDatabaseError("Gagal mereset password")
                )
            }
        } catch (e: Exception) {
            ResetPasswordResult.DatabaseError(DbFailure.UnexpectedDatabaseError(e.message))
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
                    client.setSessionToken(token)
                    sessionPrefs.setSession(newUserId, token)
                    invalidator.invalidateAll()
                    AuthResult.Success(newUserId)
                }
                "EMAIL_ALREADY_REGISTERED" -> AuthResult.EmailAlreadyRegistered
                "VALIDATION_FAILED" -> AuthResult.ValidationFailed(regResp.optString("message", "Validasi gagal"))
                else -> AuthResult.DatabaseError(DbFailure.UnexpectedDatabaseError("Gagal registrasi"))
            }
        } catch (e: Exception) {
            AuthResult.DatabaseError(DbFailure.UnexpectedDatabaseError(e.message))
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
        invalidator.invalidateAll()
    }

    override suspend fun ensureSeeded() {
    }
}
