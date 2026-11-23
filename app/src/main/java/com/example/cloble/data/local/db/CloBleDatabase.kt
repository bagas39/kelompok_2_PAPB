package com.example.cloble.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.cloble.data.local.converter.CloBleTypeConverters
import com.example.cloble.data.local.dao.OfflineChatCacheDao
import com.example.cloble.data.local.entity.CachedChatMessageEntity
import com.example.cloble.data.local.entity.CachedChatThreadEntity
import com.example.cloble.data.local.entity.CachedMatchDetailEntity

@Database(
    entities = [
        CachedChatThreadEntity::class,
        CachedChatMessageEntity::class,
        CachedMatchDetailEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(CloBleTypeConverters::class)
abstract class CloBleDatabase : RoomDatabase() {

    abstract fun offlineChatCacheDao(): OfflineChatCacheDao

    companion object {
        const val DATABASE_NAME = "cloble_cache.db"

        @Volatile
        private var INSTANCE: CloBleDatabase? = null

        fun getInstance(context: Context): CloBleDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CloBleDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
