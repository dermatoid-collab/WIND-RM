package com.windrm.app.remote.strava

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.windrm.app.BuildConfig
import java.time.Instant

/**
 * Drives the Strava OAuth2 "Authorization Code" flow: opens Strava's mobile authorize page in a
 * Custom Tab, and once the app is re-opened via the `windrm://strava-callback?code=...` redirect
 * (see AndroidManifest's intent-filter on MainActivity), exchanges that code for tokens and
 * keeps them fresh via the refresh token.
 */
class StravaAuthManager(
    private val context: Context,
    private val api: StravaApi,
    private val tokenStore: StravaTokenStore,
) {
    val isConfigured: Boolean get() = BuildConfig.STRAVA_CLIENT_ID.isNotBlank() && BuildConfig.STRAVA_CLIENT_SECRET.isNotBlank()

    private val redirectUri: String
        get() = "${BuildConfig.STRAVA_REDIRECT_SCHEME}://${BuildConfig.STRAVA_REDIRECT_HOST}"

    fun launchAuthorization() {
        val uri = Uri.parse(StravaApi.AUTHORIZE_URL).buildUpon()
            .appendQueryParameter("client_id", BuildConfig.STRAVA_CLIENT_ID)
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("approval_prompt", "auto")
            .appendQueryParameter("scope", StravaApi.SCOPE)
            .build()
        runCatching {
            CustomTabsIntent.Builder().build().launchUrl(context, uri)
        }.onFailure {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** Call from MainActivity when a `windrm://strava-callback` intent is received. Returns true if it was handled. */
    suspend fun handleRedirect(uri: Uri): Boolean {
        if (uri.scheme != BuildConfig.STRAVA_REDIRECT_SCHEME || uri.host != BuildConfig.STRAVA_REDIRECT_HOST) return false
        val code = uri.getQueryParameter("code") ?: return false
        val response = api.exchangeCodeForToken(BuildConfig.STRAVA_CLIENT_ID, BuildConfig.STRAVA_CLIENT_SECRET, code)
        tokenStore.save(StravaTokens(response.access_token, response.refresh_token, response.expires_at))
        return true
    }

    suspend fun isAuthorized(): Boolean = tokenStore.load() != null

    suspend fun signOut() = tokenStore.clear()

    /** Returns a currently-valid `Bearer ...` header value, refreshing the token first if it's expiring. */
    suspend fun bearerToken(): String? {
        val tokens = tokenStore.load() ?: return null
        val expiresSoon = tokens.expiresAtEpochS <= Instant.now().epochSecond + 60
        val fresh = if (expiresSoon) {
            val response = api.refreshToken(BuildConfig.STRAVA_CLIENT_ID, BuildConfig.STRAVA_CLIENT_SECRET, tokens.refreshToken)
            val newTokens = StravaTokens(response.access_token, response.refresh_token, response.expires_at)
            tokenStore.save(newTokens)
            newTokens
        } else {
            tokens
        }
        return "Bearer ${fresh.accessToken}"
    }
}
