package com.windrm.app.remote.strava

import kotlinx.serialization.Serializable

@Serializable
data class StravaTokenResponse(
    val access_token: String,
    val refresh_token: String,
    /** Epoch seconds at which [access_token] expires. */
    val expires_at: Long,
    val token_type: String? = null,
)

@Serializable
data class StravaAthlete(
    val id: Long,
)

/** A route created with Strava's Route Builder (not a recorded activity). */
@Serializable
data class StravaRouteSummary(
    val id: Long,
    val name: String,
    val distance: Double = 0.0,
    val elevation_gain: Double = 0.0,
    val private: Boolean = false,
    val starred: Boolean = false,
)
