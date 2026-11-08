package com.example.cloble.data.local.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
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
        val KEY_IS_DEMO_SEEDED = booleanPreferencesKey("is_demo_seeded")
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

    suspend fun clearSession() {
        context.dataStore.edit { preferences ->
            preferences.remove(PreferencesKeys.KEY_LOGGED_IN_USER_ID)
        }
    }

    suspend fun setDemoSeeded(seeded: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_IS_DEMO_SEEDED] = seeded
        }
    }

    suspend fun getLoggedInUserIdOnce(): Long? {
        return loggedInUserIdFlow.first()
    }

    suspend fun isDemoSeededOnce(): Boolean {
        return isDemoSeededFlow.first()
    }
}
