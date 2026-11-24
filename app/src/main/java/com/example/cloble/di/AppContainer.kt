package com.example.cloble.di

import android.content.Context
import com.example.cloble.data.local.db.CloBleDatabase
import com.example.cloble.data.local.session.SessionPreferencesDataSource
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
import com.example.cloble.domain.model.DiscoverFilter
import com.example.cloble.domain.repository.AuthRepository
import com.example.cloble.domain.repository.ChatRepository
import com.example.cloble.domain.repository.DiscoveryMatchRepository
import com.example.cloble.domain.repository.FcmRepository
import com.example.cloble.domain.repository.ProfileReviewRepository
import com.example.cloble.domain.repository.WardrobeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow

interface AppContainer {
    val database: CloBleDatabase
    val sessionPrefs: SessionPreferencesDataSource
    val authRepository: AuthRepository
    val wardrobeRepository: WardrobeRepository
    val discoveryMatchRepository: DiscoveryMatchRepository
    val chatRepository: ChatRepository
    val profileReviewRepository: ProfileReviewRepository
    val fcmRepository: FcmRepository
    val dataInvalidator: SupabaseDataInvalidator
    val discoverFilterFlow: MutableStateFlow<DiscoverFilter>
    val cleanupScope: CoroutineScope
}

class DefaultAppContainer(private val context: Context) : AppContainer {

    init {
        require(SupabaseConfig.isConfigured) {
            "Konfigurasi Supabase belum diatur (SUPABASE_URL / SUPABASE_KEY)."
        }
    }

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

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override val cleanupScope: CoroutineScope by lazy {
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    override val authRepository: AuthRepository by lazy {
        SupabaseAuthRepository(
            client = supabaseClient,
            sessionPrefs = sessionPrefs,
            invalidator = dataInvalidator,
            offlineChatCacheDao = database.offlineChatCacheDao(),
            realtimeClient = supabaseRealtimeClient
        ).also { repository ->
            applicationScope.launch {
                resetDiscoverFilterOnAccountChange(repository.observeLoggedInUserId(), discoverFilterFlow)
            }
        }
    }

    override val wardrobeRepository: WardrobeRepository by lazy {
        SupabaseWardrobeRepository(
            client = supabaseClient,
            context = context,
            invalidator = dataInvalidator,
            cleanupScope = cleanupScope
        )
    }

    override val discoveryMatchRepository: DiscoveryMatchRepository by lazy {
        SupabaseDiscoveryMatchRepository(
            client = supabaseClient,
            invalidator = dataInvalidator,
            realtimeClient = supabaseRealtimeClient,
            cacheDao = database.offlineChatCacheDao()
        )
    }

    override val chatRepository: ChatRepository by lazy {
        SupabaseChatRepository(
            client = supabaseClient,
            realtimeClient = supabaseRealtimeClient,
            invalidator = dataInvalidator,
            cacheDao = database.offlineChatCacheDao()
        )
    }

    override val profileReviewRepository: ProfileReviewRepository by lazy {
        SupabaseProfileReviewRepository(
            client = supabaseClient,
            context = context,
            invalidator = dataInvalidator,
            cleanupScope = cleanupScope
        )
    }

    override val fcmRepository: FcmRepository by lazy {
        SupabaseFcmRepository(supabaseClient, sessionPrefs)
    }
}

internal suspend fun resetDiscoverFilterOnAccountChange(
    userIds: Flow<Long?>,
    filters: MutableStateFlow<DiscoverFilter>
) {
    userIds.distinctUntilChanged().collect { filters.value = DiscoverFilter() }
}
