package com.windrm.app.remote.strava

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.stravaDataStore by preferencesDataStore(name = "strava_tokens")

data class StravaTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochS: Long,
)

/** Persists Strava OAuth tokens locally so the user doesn't have to re-authorize on every launch. */
class StravaTokenStore(private val context: Context) {
    private val keyAccess = stringPreferencesKey("access_token")
    private val keyRefresh = stringPreferencesKey("refresh_token")
    private val keyExpiry = longPreferencesKey("expires_at")

    suspend fun save(tokens: StravaTokens) {
        context.stravaDataStore.edit { prefs ->
            prefs[keyAccess] = tokens.accessToken
            prefs[keyRefresh] = tokens.refreshToken
            prefs[keyExpiry] = tokens.expiresAtEpochS
        }
    }

    suspend fun load(): StravaTokens? {
        val prefs = context.stravaDataStore.data.first()
        val access = prefs[keyAccess] ?: return null
        val refresh = prefs[keyRefresh] ?: return null
        val expiry = prefs[keyExpiry] ?: return null
        return StravaTokens(access, refresh, expiry)
    }

    suspend fun clear() {
        context.stravaDataStore.edit { it.clear() }
    }
}
