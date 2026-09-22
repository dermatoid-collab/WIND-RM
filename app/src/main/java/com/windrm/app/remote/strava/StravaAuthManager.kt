package com.windrm.app.remote.strava

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.windrm.app.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.time.Instant

/** Result of processing a `windrm://strava-callback` redirect, published on [StravaAuthManager.authEvents]. */
sealed interface StravaAuthEvent {
    data object Authorized : StravaAuthEvent
    data class Failed(val message: String) : StravaAuthEvent
}

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

    // replay = 1 so a collector that starts a moment after handleRedirect() finishes (e.g. the
    // Activity's onResume/Compose recomposition racing the async token exchange, or the process
    // being recreated while the Custom Tab was open) still observes the outcome.
    private val _authEvents = MutableSharedFlow<StravaAuthEvent>(replay = 1, extraBufferCapacity = 1)
    val authEvents: SharedFlow<StravaAuthEvent> = _authEvents.asSharedFlow()

    private val redirectUri: String
        get() = "${BuildConfig.STRAVA_REDIRECT_SCHEME}://${BuildConfig.STRAVA_REDIRECT_HOST}"

    @Volatile
    private var cachedAthleteId: Long? = null

    /** The authenticated athlete's numeric id, needed to list their routes. Cached after the first call. */
    suspend fun athleteId(): Long? {
        cachedAthleteId?.let { return it }
        val token = bearerToken() ?: return null
        val id = api.getAuthenticatedAthlete(token).id
        cachedAthleteId = id
        return id
    }

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

    /**
     * Call from MainActivity when a `windrm://strava-callback` intent is received. Returns true
     * if the uri was ours to handle (regardless of whether the token exchange then succeeded);
     * the actual outcome is published on [authEvents] once the (asynchronous) exchange completes.
     */
    suspend fun handleRedirect(uri: Uri): Boolean {
        if (uri.scheme != BuildConfig.STRAVA_REDIRECT_SCHEME || uri.host != BuildConfig.STRAVA_REDIRECT_HOST) return false
        val code = uri.getQueryParameter("code")
        if (code == null) {
            _authEvents.emit(StravaAuthEvent.Failed(uri.getQueryParameter("error") ?: "Autorizzazione Strava annullata."))
            return true
        }
        try {
            val response = api.exchangeCodeForToken(BuildConfig.STRAVA_CLIENT_ID, BuildConfig.STRAVA_CLIENT_SECRET, code)
            tokenStore.save(StravaTokens(response.access_token, response.refresh_token, response.expires_at))
            _authEvents.emit(StravaAuthEvent.Authorized)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _authEvents.emit(StravaAuthEvent.Failed(e.message ?: "Scambio del token Strava non riuscito."))
        }
        return true
    }

    suspend fun isAuthorized(): Boolean = tokenStore.load() != null

    suspend fun signOut() {
        cachedAthleteId = null
        tokenStore.clear()
    }

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
