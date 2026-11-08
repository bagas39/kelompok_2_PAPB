package com.example.cloble.di

import android.content.Context
import com.example.cloble.data.local.db.CloBleDatabase
import com.example.cloble.data.local.session.SessionPreferencesDataSource
import com.example.cloble.data.local.storage.LocalPhotoStorageManager
import com.example.cloble.data.repository.LocalAuthRepository
import com.example.cloble.data.repository.LocalChatRepository
import com.example.cloble.data.repository.LocalDiscoveryMatchRepository
import com.example.cloble.data.repository.LocalProfileReviewRepository
import com.example.cloble.data.repository.LocalWardrobeRepository
import com.example.cloble.domain.repository.AuthRepository
import com.example.cloble.domain.repository.ChatRepository
import com.example.cloble.domain.repository.DiscoveryMatchRepository
import com.example.cloble.domain.repository.ProfileReviewRepository
import com.example.cloble.domain.repository.WardrobeRepository

interface AppContainer {
    val database: CloBleDatabase
    val sessionPrefs: SessionPreferencesDataSource
    val photoStorageManager: LocalPhotoStorageManager
    val authRepository: AuthRepository
    val wardrobeRepository: WardrobeRepository
    val discoveryMatchRepository: DiscoveryMatchRepository
    val chatRepository: ChatRepository
    val profileReviewRepository: ProfileReviewRepository
}

class DefaultAppContainer(private val context: Context) : AppContainer {

    override val database: CloBleDatabase by lazy {
        CloBleDatabase.getInstance(context)
    }

    override val sessionPrefs: SessionPreferencesDataSource by lazy {
        SessionPreferencesDataSource(context)
    }

    override val photoStorageManager: LocalPhotoStorageManager by lazy {
        LocalPhotoStorageManager(context)
    }

    override val authRepository: AuthRepository by lazy {
        LocalAuthRepository(database, sessionPrefs)
    }

    override val wardrobeRepository: WardrobeRepository by lazy {
        LocalWardrobeRepository(database.clothingItemDao(), photoStorageManager)
    }

    override val discoveryMatchRepository: DiscoveryMatchRepository by lazy {
        LocalDiscoveryMatchRepository(
            discoveryDao = database.discoveryDao(),
            interestMatchDao = database.interestMatchDao(),
            notificationDao = database.notificationDao(),
            clothingDao = database.clothingItemDao(),
            userDao = database.userDao(),
            reviewDao = database.reviewDao()
        )
    }

    override val chatRepository: ChatRepository by lazy {
        LocalChatRepository(database.chatDao(), database.userDao())
    }

    override val profileReviewRepository: ProfileReviewRepository by lazy {
        LocalProfileReviewRepository(database.userDao(), database.reviewDao())
    }
}
