package com.example.cloble.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.cloble.data.local.converter.CloBleTypeConverters
import com.example.cloble.data.local.dao.ChatDao
import com.example.cloble.data.local.dao.ClothingItemDao
import com.example.cloble.data.local.dao.DiscoveryDao
import com.example.cloble.data.local.dao.InterestMatchDao
import com.example.cloble.data.local.dao.NotificationDao
import com.example.cloble.data.local.dao.OfflineChatCacheDao
import com.example.cloble.data.local.dao.ReviewDao
import com.example.cloble.data.local.dao.UserDao
import com.example.cloble.data.local.entity.CachedChatMessageEntity
import com.example.cloble.data.local.entity.CachedChatThreadEntity
import com.example.cloble.data.local.entity.CachedMatchDetailEntity
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
        NotificationEntity::class,
        CachedChatThreadEntity::class,
        CachedChatMessageEntity::class,
        CachedMatchDetailEntity::class
    ],
    version = 3,
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
    abstract fun offlineChatCacheDao(): OfflineChatCacheDao

    companion object {
        const val DATABASE_NAME = "cloble_poc.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE users ADD COLUMN avatarUri TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cached_chat_threads` (
                        `viewerUserId` INTEGER NOT NULL,
                        `matchId` INTEGER NOT NULL,
                        `partnerUserId` INTEGER NOT NULL,
                        `partnerName` TEXT NOT NULL,
                        `partnerCity` TEXT NOT NULL,
                        `partnerAvatarUri` TEXT,
                        `partnerAverageRating` REAL NOT NULL,
                        `myItemName` TEXT NOT NULL,
                        `partnerItemName` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `lastMessageText` TEXT,
                        `lastMessageSentAt` INTEGER,
                        `unreadCount` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`viewerUserId`, `matchId`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_cached_chat_threads_viewerUserId_updatedAt` ON `cached_chat_threads` (`viewerUserId`, `updatedAt`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_cached_chat_threads_viewerUserId_partnerUserId` ON `cached_chat_threads` (`viewerUserId`, `partnerUserId`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cached_chat_messages` (
                        `id` INTEGER NOT NULL,
                        `matchId` INTEGER NOT NULL,
                        `senderId` INTEGER NOT NULL,
                        `senderName` TEXT NOT NULL,
                        `messageText` TEXT NOT NULL,
                        `isRead` INTEGER NOT NULL,
                        `sentAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_cached_chat_messages_matchId_sentAt` ON `cached_chat_messages` (`matchId`, `sentAt`)"
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `cached_match_details` (
                        `viewerUserId` INTEGER NOT NULL,
                        `matchId` INTEGER NOT NULL,
                        `status` TEXT NOT NULL,
                        `meetingLocation` TEXT,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL,
                        `partnerUserId` INTEGER NOT NULL,
                        `partnerName` TEXT NOT NULL,
                        `partnerCity` TEXT NOT NULL,
                        `partnerAvatarUri` TEXT,
                        `partnerAverageRating` REAL NOT NULL,
                        `partnerReviewCount` INTEGER NOT NULL,
                        `myItem_id` INTEGER NOT NULL,
                        `myItem_ownerId` INTEGER NOT NULL,
                        `myItem_name` TEXT NOT NULL,
                        `myItem_description` TEXT NOT NULL,
                        `myItem_category` TEXT NOT NULL,
                        `myItem_size` TEXT NOT NULL,
                        `myItem_gender` TEXT NOT NULL,
                        `myItem_color` TEXT NOT NULL,
                        `myItem_condition` TEXT NOT NULL,
                        `myItem_mainPhotoUri` TEXT,
                        `myItem_detailPhotoUri` TEXT,
                        `myItem_extraPhotoUri` TEXT,
                        `myItem_isBarterAllowed` INTEGER NOT NULL,
                        `myItem_status` TEXT NOT NULL,
                        `myItem_createdAt` INTEGER NOT NULL,
                        `myItem_updatedAt` INTEGER NOT NULL,
                        `partnerItem_id` INTEGER NOT NULL,
                        `partnerItem_ownerId` INTEGER NOT NULL,
                        `partnerItem_name` TEXT NOT NULL,
                        `partnerItem_description` TEXT NOT NULL,
                        `partnerItem_category` TEXT NOT NULL,
                        `partnerItem_size` TEXT NOT NULL,
                        `partnerItem_gender` TEXT NOT NULL,
                        `partnerItem_color` TEXT NOT NULL,
                        `partnerItem_condition` TEXT NOT NULL,
                        `partnerItem_mainPhotoUri` TEXT,
                        `partnerItem_detailPhotoUri` TEXT,
                        `partnerItem_extraPhotoUri` TEXT,
                        `partnerItem_isBarterAllowed` INTEGER NOT NULL,
                        `partnerItem_status` TEXT NOT NULL,
                        `partnerItem_createdAt` INTEGER NOT NULL,
                        `partnerItem_updatedAt` INTEGER NOT NULL,
                        PRIMARY KEY(`viewerUserId`, `matchId`)
                    )
                    """.trimIndent()
                )
            }
        }

        @Volatile
        private var INSTANCE: CloBleDatabase? = null

        fun getInstance(context: Context): CloBleDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CloBleDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
