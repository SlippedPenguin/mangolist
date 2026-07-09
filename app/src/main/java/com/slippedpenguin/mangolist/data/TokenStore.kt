package com.slippedpenguin.mangolist.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/*
 * DataStore wrapper for the AniList OAuth access token + viewer metadata.
 * Small key/value payload — Preferences flavor (not Proto) is the right
 * call; the schema isn't shaped for a serialized dataclass.
 */
private val Context.tokenDataStore by preferencesDataStore(name = "token_prefs")

class TokenStore(private val context: Context) {

    private val tokenKey   = stringPreferencesKey("anilist_access_token")
    private val userIdKey  = stringPreferencesKey("anilist_user_id")
    private val userNameKey = stringPreferencesKey("anilist_user_name")

    val accessToken: Flow<String?> = context.tokenDataStore.data.map { it[tokenKey] }
    val userId:      Flow<String?> = context.tokenDataStore.data.map { it[userIdKey] }
    val userName:    Flow<String?> = context.tokenDataStore.data.map { it[userNameKey] }

    suspend fun saveToken(token: String, userId: Int, userName: String?) {
        context.tokenDataStore.edit { prefs ->
            prefs[tokenKey]  = token
            prefs[userIdKey] = userId.toString()
            if (userName != null) prefs[userNameKey] = userName
        }
    }

    suspend fun clear() {
        context.tokenDataStore.edit { it.clear() }
    }
}
