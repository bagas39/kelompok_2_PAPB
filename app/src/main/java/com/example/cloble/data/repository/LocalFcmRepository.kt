package com.example.cloble.data.repository

import com.example.cloble.data.local.session.SessionPreferencesDataSource
import com.example.cloble.domain.repository.FcmRepository

class LocalFcmRepository(
    private val sessionPrefs: SessionPreferencesDataSource
) : FcmRepository {

    override suspend fun registerCurrentToken(userId: Long?) {
    }

    override suspend fun unregisterCurrentToken() {
    }

    override suspend fun onNewToken(token: String) {
        sessionPrefs.setFcmToken(token)
    }
}
