package com.example.cloble.data.repository

import com.example.cloble.data.local.dao.ReviewDao
import com.example.cloble.data.local.dao.UserDao
import com.example.cloble.data.local.db.CloBleDatabase
import com.example.cloble.data.local.entity.UserEntity
import com.example.cloble.data.local.session.SessionPreferencesDataSource
import com.example.cloble.data.seed.PrepopulateDemoData
import com.example.cloble.domain.model.CityPreset
import com.example.cloble.domain.model.DemoAccountSummary
import com.example.cloble.domain.model.UserProfile
import com.example.cloble.domain.repository.AuthRepository
import com.example.cloble.domain.result.AuthResult
import com.example.cloble.domain.result.DbFailure
import com.example.cloble.domain.util.PasswordHasher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class LocalAuthRepository(
    private val database: CloBleDatabase,
    private val sessionPrefs: SessionPreferencesDataSource
) : AuthRepository {

    private val userDao: UserDao = database.userDao()
    private val reviewDao: ReviewDao = database.reviewDao()

    override fun observeLoggedInUserId(): Flow<Long?> = sessionPrefs.loggedInUserIdFlow

    override fun observeCurrentUser(): Flow<UserProfile?> =
        observeLoggedInUserId().flatMapLatest { userId ->
            if (userId == null) {
                flowOf(null)
            } else {
                userDao.getUserByIdFlow(userId).map { userEntity ->
                    userEntity?.let {
                        val avg = reviewDao.getAverageRatingForUser(it.id) ?: 0.0
                        val count = reviewDao.getReviewCountForUser(it.id)
                        EntityMappers.toDomainUserProfile(it, avg, count)
                    }
                }
            }
        }

    override fun getDemoAccounts(): Flow<List<DemoAccountSummary>> {
        return userDao.getAllUsersFlow().map { users ->
            users.map { user ->
                val role = when (user.email.lowercase()) {
                    "raka@cloble.demo" -> "Pengirim & pemilik multi-item (Bandung)"
                    "naya@cloble.demo" -> "Penerima Interest & koleksi barter (Bandung)"
                    "bagas@cloble.demo" -> "0 pakaian barter & ulasan 2★ (Cimahi)"
                    "dinda@cloble.demo" -> "Filter jarak >50km & reputasi rendah (Jakarta)"
                    else -> "Akun pengguna"
                }
                DemoAccountSummary(
                    id = user.id,
                    name = user.name,
                    email = user.email,
                    roleDescription = role
                )
            }
        }
    }

    override suspend fun login(email: String, rawPassword: String): AuthResult {
        val trimmedEmail = email.trim().lowercase()
        val user = userDao.getUserByEmail(trimmedEmail) ?: return AuthResult.InvalidCredentials

        val matches = PasswordHasher.verify(rawPassword, user.passwordHash)
        if (!matches) {
            return AuthResult.InvalidCredentials
        }

        if (PasswordHasher.needsUpgrade(user.passwordHash)) {
            val upgraded = PasswordHasher.hash(rawPassword)
            userDao.updatePasswordHash(user.id, upgraded)
        }

        sessionPrefs.setLoggedInUserId(user.id)
        return AuthResult.Success(user.id)
    }

    override suspend fun loginDemoAccount(userId: Long): AuthResult {
        val user = userDao.getUserById(userId) ?: return AuthResult.InvalidCredentials
        sessionPrefs.setLoggedInUserId(user.id)
        return AuthResult.Success(user.id)
    }

    override suspend fun register(
        name: String,
        email: String,
        rawPassword: String,
        city: String
    ): AuthResult {
        val trimmedEmail = email.trim().lowercase()
        val trimmedName = name.trim()

        if (trimmedName.isEmpty()) {
            return AuthResult.ValidationFailed("Nama tidak boleh kosong")
        }
        if (trimmedEmail.isEmpty() || !trimmedEmail.contains("@")) {
            return AuthResult.ValidationFailed("Format email tidak valid")
        }
        if (rawPassword.length < 6) {
            return AuthResult.ValidationFailed("Password minimal 6 karakter")
        }

        val existing = userDao.getUserByEmail(trimmedEmail)
        if (existing != null) {
            return AuthResult.EmailAlreadyRegistered
        }

        val preset = CityPreset.fromDisplayName(city)
        val passwordHash = PasswordHasher.hash(rawPassword)

        return EntityMappers.runCatchingSqlite(
            onBusyOrLocked = { AuthResult.DatabaseError(DbFailure.DatabaseBusyOrLocked(it)) },
            onConstraintViolation = { AuthResult.EmailAlreadyRegistered },
            onUnexpectedDatabaseError = { AuthResult.DatabaseError(DbFailure.UnexpectedDatabaseError(it)) }
        ) {
            val userId = userDao.insertUser(
                UserEntity(
                    name = trimmedName,
                    email = trimmedEmail,
                    passwordHash = passwordHash,
                    bio = "",
                    city = preset.displayName,
                    latitude = preset.latitude,
                    longitude = preset.longitude,
                    preferredSize = "M",
                    preferredGender = "Uniseks",
                    preferredRadiusKm = 10,
                    createdAt = System.currentTimeMillis()
                )
            )
            sessionPrefs.setLoggedInUserId(userId)
            AuthResult.Success(userId)
        }
    }

    override suspend fun logout() {
        sessionPrefs.clearSession()
    }

    override suspend fun ensureSeeded() {
        PrepopulateDemoData.seedDatabase(database)
        sessionPrefs.setDemoSeeded(true)
    }
}
