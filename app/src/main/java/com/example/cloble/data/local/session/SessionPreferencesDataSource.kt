package com.example.cloble.data.local.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cloble_session_prefs")

class SessionPreferencesDataSource(private val context: Context) {

    private object PreferencesKeys {
        val KEY_LOGGED_IN_USER_ID = longPreferencesKey("logged_in_user_id")
        val KEY_SESSION_TOKEN = stringPreferencesKey("session_token")
        val KEY_IS_DEMO_SEEDED = booleanPreferencesKey("is_demo_seeded")
        val KEY_FCM_TOKEN = stringPreferencesKey("fcm_token")
    }

    val loggedInUserIdFlow: Flow<Long?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.KEY_LOGGED_IN_USER_ID]
        }

    val sessionTokenFlow: Flow<String?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.KEY_SESSION_TOKEN]
        }

    val fcmTokenFlow: Flow<String?> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.KEY_FCM_TOKEN]
        }

    val isDemoSeededFlow: Flow<Boolean> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.KEY_IS_DEMO_SEEDED] ?: false
        }

    suspend fun setLoggedInUserId(userId: Long?) {
        context.dataStore.edit { preferences ->
            if (userId != null) {
                preferences[PreferencesKeys.KEY_LOGGED_IN_USER_ID] = userId
            } else {
                preferences.remove(PreferencesKeys.KEY_LOGGED_IN_USER_ID)
            }
        }
    }

    suspend fun setSession(userId: Long, sessionToken: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_LOGGED_IN_USER_ID] = userId
            preferences[PreferencesKeys.KEY_SESSION_TOKEN] = sessionToken
        }
    }

    suspend fun setSessionToken(token: String?) {
        context.dataStore.edit { preferences ->
            if (token != null) {
                preferences[PreferencesKeys.KEY_SESSION_TOKEN] = token
            } else {
                preferences.remove(PreferencesKeys.KEY_SESSION_TOKEN)
            }
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.KEY_LOGGED_IN_USER_ID)
            preferences.remove(PreferencesKeys.KEY_SESSION_TOKEN)
        }
    }

    suspend fun setDemoSeeded(seeded: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_IS_DEMO_SEEDED] = seeded
        }
    }

    suspend fun setFcmToken(token: String?) {
        context.dataStore.edit { preferences ->
            if (token != null) {
                preferences[PreferencesKeys.KEY_FCM_TOKEN] = token
            } else {
                preferences.remove(PreferencesKeys.KEY_FCM_TOKEN)
            }
        }
    }

    suspend fun getLoggedInUserIdOnce(): Long? {
        return loggedInUserIdFlow.first()
    }

    suspend fun getFcmTokenOnce(): String? {
        return fcmTokenFlow.first()
    }

    suspend fun isDemoSeededOnce(): Boolean {
        return isDemoSeededFlow.first()
    }
}
