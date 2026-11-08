package com.example.cloble.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.cloble.data.local.entity.UserEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertUser(user: UserEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(users: List<UserEntity>): List<Long>

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    suspend fun getUserById(id: Long): UserEntity?

    @Query("SELECT * FROM users WHERE id = :id LIMIT 1")
    fun getUserByIdFlow(id: Long): Flow<UserEntity?>

    @Query("SELECT * FROM users WHERE LOWER(email) = LOWER(:email) LIMIT 1")
    suspend fun getUserByEmail(email: String): UserEntity?

    @Query("SELECT name FROM users WHERE id = :id LIMIT 1")
    suspend fun getUserNameById(id: Long): String?

    @Query("UPDATE users SET bio = :bio WHERE id = :userId")
    suspend fun updateBio(userId: Long, bio: String)

    @Query(
        """
        UPDATE users
        SET preferredSize = :preferredSize, preferredGender = :preferredGender,
            city = :city, latitude = :latitude, longitude = :longitude,
            preferredRadiusKm = :preferredRadiusKm
        WHERE id = :userId
        """
    )
    suspend fun updatePreferences(
        userId: Long,
        preferredSize: String,
        preferredGender: String,
        city: String,
        latitude: Double,
        longitude: Double,
        preferredRadiusKm: Int
    )

    @Query("UPDATE users SET passwordHash = :hash WHERE id = :userId")
    suspend fun updatePasswordHash(userId: Long, hash: String)

    @Query("SELECT COUNT(*) FROM users")
    suspend fun countUsers(): Int

    @Query("SELECT * FROM users ORDER BY id ASC")
    fun getAllUsersFlow(): Flow<List<UserEntity>>
}
