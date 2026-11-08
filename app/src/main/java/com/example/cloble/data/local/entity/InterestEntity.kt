package com.example.cloble.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.cloble.domain.model.InterestStatus

@Entity(
    tableName = "interests",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["senderUserId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["receiverUserId"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = ClothingItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["targetItemId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["senderUserId", "targetItemId"], unique = true),
        Index(value = ["receiverUserId", "status"]),
        Index(value = ["targetItemId", "status"])
    ]
)
data class InterestEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val senderUserId: Long,
    val receiverUserId: Long,
    val targetItemId: Long,
    val status: InterestStatus = InterestStatus.PENDING,
    val createdAt: Long = System.currentTimeMillis(),
    val respondedAt: Long? = null
)
