package com.example.cloble.data.local.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.cloble.ui.theme.AppThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "cloble_session_prefs")

data class StoredSession(
    val userId: Long?,
    val sessionToken: String?
)

class SessionPreferencesDataSource(
    private val context: Context,
    private val tokenCipher: SessionTokenCipher = SessionTokenCipher()
) {

    private object PreferencesKeys {
        val KEY_LOGGED_IN_USER_ID = longPreferencesKey("logged_in_user_id")
        val KEY_SESSION_TOKEN = stringPreferencesKey("session_token")
        val KEY_FCM_TOKEN = stringPreferencesKey("fcm_token")
        val KEY_APP_THEME_MODE = stringPreferencesKey("app_theme_mode")
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
        .distinctUntilChanged()
        .map { encrypted ->
            tokenCipher.decrypt(encrypted)
        }
        .flowOn(Dispatchers.IO)

    val sessionFlow: Flow<StoredSession> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[PreferencesKeys.KEY_LOGGED_IN_USER_ID] to preferences[PreferencesKeys.KEY_SESSION_TOKEN]
        }
        .distinctUntilChanged()
        .map { (userId, encrypted) ->
            StoredSession(
                userId = userId,
                sessionToken = if (userId != null) tokenCipher.decrypt(encrypted) else null
            )
        }
        .flowOn(Dispatchers.IO)

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

    val themeModeFlow: Flow<AppThemeMode> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { preferences ->
            AppThemeMode.fromStorageKey(preferences[PreferencesKeys.KEY_APP_THEME_MODE])
        }
        .distinctUntilChanged()

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
        val storedToken = withContext(Dispatchers.IO) {
            tokenCipher.encrypt(sessionToken)
        }
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_LOGGED_IN_USER_ID] = userId
            preferences[PreferencesKeys.KEY_SESSION_TOKEN] = storedToken
        }
    }

    suspend fun setSessionToken(token: String?) {
        val storedToken = token?.let { raw ->
            withContext(Dispatchers.IO) {
                tokenCipher.encrypt(raw)
            }
        }
        context.dataStore.edit { preferences ->
            if (storedToken != null) {
                preferences[PreferencesKeys.KEY_SESSION_TOKEN] = storedToken
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

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.KEY_APP_THEME_MODE] = mode.storageKey
        }
    }
}
