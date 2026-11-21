package com.example.cloble.di

import android.content.Context
import com.example.cloble.data.local.db.CloBleDatabase
import com.example.cloble.data.local.session.SessionPreferencesDataSource
import com.example.cloble.data.local.storage.LocalPhotoStorageManager
import com.example.cloble.data.remote.SupabaseAuthRepository
import com.example.cloble.data.remote.SupabaseChatRepository
import com.example.cloble.data.remote.SupabaseClient
import com.example.cloble.data.remote.SupabaseConfig
import com.example.cloble.data.remote.SupabaseDataInvalidator
import com.example.cloble.data.remote.SupabaseDiscoveryMatchRepository
import com.example.cloble.data.remote.SupabaseFcmRepository
import com.example.cloble.data.remote.SupabaseProfileReviewRepository
import com.example.cloble.data.remote.SupabaseRealtimeClient
import com.example.cloble.data.remote.SupabaseWardrobeRepository
import com.example.cloble.data.repository.LocalAuthRepository
import com.example.cloble.data.repository.LocalChatRepository
import com.example.cloble.data.repository.LocalDiscoveryMatchRepository
import com.example.cloble.data.repository.LocalFcmRepository
import com.example.cloble.data.repository.LocalProfileReviewRepository
import com.example.cloble.data.repository.LocalWardrobeRepository
import com.example.cloble.domain.model.DiscoverFilter
import com.example.cloble.domain.repository.AuthRepository
import com.example.cloble.domain.repository.ChatRepository
import com.example.cloble.domain.repository.DiscoveryMatchRepository
import com.example.cloble.domain.repository.FcmRepository
import com.example.cloble.domain.repository.ProfileReviewRepository
import com.example.cloble.domain.repository.WardrobeRepository
import kotlinx.coroutines.flow.MutableStateFlow

interface AppContainer {
    val database: CloBleDatabase
    val sessionPrefs: SessionPreferencesDataSource
    val photoStorageManager: LocalPhotoStorageManager
    val authRepository: AuthRepository
    val wardrobeRepository: WardrobeRepository
    val discoveryMatchRepository: DiscoveryMatchRepository
    val chatRepository: ChatRepository
    val profileReviewRepository: ProfileReviewRepository
    val fcmRepository: FcmRepository
    val dataInvalidator: SupabaseDataInvalidator
    val discoverFilterFlow: MutableStateFlow<DiscoverFilter>
}

class DefaultAppContainer(private val context: Context) : AppContainer {

    private val supabaseClient: SupabaseClient by lazy {
        SupabaseClient(SupabaseConfig)
    }

    private val supabaseRealtimeClient: SupabaseRealtimeClient by lazy {
        SupabaseRealtimeClient(SupabaseConfig)
    }

    override val dataInvalidator: SupabaseDataInvalidator by lazy {
        SupabaseDataInvalidator()
    }

    override val discoverFilterFlow: MutableStateFlow<DiscoverFilter> by lazy {
        MutableStateFlow(DiscoverFilter())
    }

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
        if (SupabaseConfig.isConfigured) {
            SupabaseAuthRepository(
                client = supabaseClient,
                sessionPrefs = sessionPrefs,
                invalidator = dataInvalidator,
                offlineChatCacheDao = database.offlineChatCacheDao()
            )
        } else {
            LocalAuthRepository(database, sessionPrefs)
        }
    }

    override val wardrobeRepository: WardrobeRepository by lazy {
        if (SupabaseConfig.isConfigured) {
            SupabaseWardrobeRepository(supabaseClient, context, dataInvalidator)
        } else {
            LocalWardrobeRepository(database.clothingItemDao(), photoStorageManager)
        }
    }

    override val discoveryMatchRepository: DiscoveryMatchRepository by lazy {
        if (SupabaseConfig.isConfigured) {
            SupabaseDiscoveryMatchRepository(
                client = supabaseClient,
                invalidator = dataInvalidator,
                realtimeClient = supabaseRealtimeClient,
                cacheDao = database.offlineChatCacheDao()
            )
        } else {
            LocalDiscoveryMatchRepository(
                discoveryDao = database.discoveryDao(),
                interestMatchDao = database.interestMatchDao(),
                notificationDao = database.notificationDao(),
                clothingDao = database.clothingItemDao(),
                userDao = database.userDao(),
                reviewDao = database.reviewDao()
            )
        }
    }

    override val chatRepository: ChatRepository by lazy {
        if (SupabaseConfig.isConfigured) {
            SupabaseChatRepository(
                client = supabaseClient,
                realtimeClient = supabaseRealtimeClient,
                invalidator = dataInvalidator,
                cacheDao = database.offlineChatCacheDao()
            )
        } else {
            LocalChatRepository(database.chatDao(), database.userDao())
        }
    }

    override val profileReviewRepository: ProfileReviewRepository by lazy {
        if (SupabaseConfig.isConfigured) {
            SupabaseProfileReviewRepository(supabaseClient, context, dataInvalidator)
        } else {
            LocalProfileReviewRepository(database.userDao(), database.reviewDao(), photoStorageManager)
        }
    }

    override val fcmRepository: FcmRepository by lazy {
        if (SupabaseConfig.isConfigured) {
            SupabaseFcmRepository(supabaseClient, sessionPrefs)
        } else {
            LocalFcmRepository(sessionPrefs)
        }
    }
}
