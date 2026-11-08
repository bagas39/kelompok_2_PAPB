package com.example.cloble.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.cloble.domain.model.MatchStatus

@Entity(
    tableName = "matches",
    foreignKeys = [
        ForeignKey(
            entity = InterestEntity::class,
            parentColumns = ["id"],
            childColumns = ["sourceInterestId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["user1Id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = ClothingItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["user1ItemId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["user2Id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = ClothingItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["user2ItemId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["sourceInterestId"], unique = true),
        Index(value = ["user1Id", "status"]),
        Index(value = ["user2Id", "status"]),
        Index(value = ["user1ItemId", "status"]),
        Index(value = ["user2ItemId", "status"])
    ]
)
data class MatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceInterestId: Long,
    val user1Id: Long,
    val user1ItemId: Long,
    val user2Id: Long,
    val user2ItemId: Long,
    val status: MatchStatus = MatchStatus.ACTIVE,
    val meetingLocation: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
