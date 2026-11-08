package com.example.cloble.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.cloble.data.local.converter.CloBleTypeConverters
import com.example.cloble.data.local.dao.ChatDao
import com.example.cloble.data.local.dao.ClothingItemDao
import com.example.cloble.data.local.dao.DiscoveryDao
import com.example.cloble.data.local.dao.InterestMatchDao
import com.example.cloble.data.local.dao.NotificationDao
import com.example.cloble.data.local.dao.ReviewDao
import com.example.cloble.data.local.dao.UserDao
import com.example.cloble.data.local.entity.ChatMessageEntity
import com.example.cloble.data.local.entity.ClothingItemEntity
import com.example.cloble.data.local.entity.InterestEntity
import com.example.cloble.data.local.entity.MatchEntity
import com.example.cloble.data.local.entity.NotificationEntity
import com.example.cloble.data.local.entity.ReviewEntity
import com.example.cloble.data.local.entity.SwipePassEntity
import com.example.cloble.data.local.entity.UserEntity

@Database(
    entities = [
        UserEntity::class,
        ClothingItemEntity::class,
        SwipePassEntity::class,
        InterestEntity::class,
        MatchEntity::class,
        ChatMessageEntity::class,
        ReviewEntity::class,
        NotificationEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(CloBleTypeConverters::class)
abstract class CloBleDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun clothingItemDao(): ClothingItemDao
    abstract fun discoveryDao(): DiscoveryDao
    abstract fun interestMatchDao(): InterestMatchDao
    abstract fun chatDao(): ChatDao
    abstract fun reviewDao(): ReviewDao
    abstract fun notificationDao(): NotificationDao

    companion object {
        const val DATABASE_NAME = "cloble_poc.db"

        @Volatile
        private var INSTANCE: CloBleDatabase? = null

        fun getInstance(context: Context): CloBleDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CloBleDatabase::class.java,
                    DATABASE_NAME
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
