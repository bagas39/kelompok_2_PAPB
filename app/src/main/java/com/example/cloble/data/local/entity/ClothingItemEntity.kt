package com.example.cloble.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.cloble.domain.model.ItemStatus

@Entity(
    tableName = "clothing_items",
    foreignKeys = [
        ForeignKey(
            entity = UserEntity::class,
            parentColumns = ["id"],
            childColumns = ["ownerId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["ownerId"]),
        Index(value = ["status", "isBarterAllowed"])
    ]
)
data class ClothingItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ownerId: Long,
    val name: String,
    val description: String,
    val category: String,
    val size: String,
    val gender: String,
    val color: String,
    val condition: String,
    val mainPhotoUri: String? = null,
    val detailPhotoUri: String? = null,
    val extraPhotoUri: String? = null,
    val isBarterAllowed: Boolean = false,
    val status: ItemStatus = ItemStatus.AVAILABLE,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
