package com.example.cloble.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.cloble.domain.model.NotificationType

@Entity(
    tableName = "notifications",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["userId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = InterestEntity::class,
            parentColumns = ["id"],
            childColumns = ["relatedInterestId"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = MatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["relatedMatchId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["userId", "createdAt"]),
        Index(value = ["relatedInterestId"]),
        Index(value = ["relatedMatchId"])
    ]
)
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: Long,
    val type: NotificationType,
    val title: String,
    val body: String,
    val relatedInterestId: Long? = null,
    val relatedMatchId: Long? = null,
    val isRead: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
