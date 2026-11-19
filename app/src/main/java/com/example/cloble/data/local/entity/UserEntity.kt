package com.example.cloble.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "users",
    indices = [
        Index(value = ["email"], unique = true)
    ]
)
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val email: String,
    val passwordHash: String,
    val bio: String = "",
    val avatarUri: String? = null,
    val city: String = "Bandung, Jawa Barat",
    val latitude: Double = -6.9175,
    val longitude: Double = 107.6191,
    val preferredSize: String = "M",
    val preferredGender: String = "Uniseks",
    val preferredRadiusKm: Int = 10,
    val createdAt: Long = System.currentTimeMillis()
)
