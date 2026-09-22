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
data class StravaActivitySummary(
    val id: Long,
    val name: String,
    val distance: Double = 0.0,
    val total_elevation_gain: Double = 0.0,
    val type: String? = null,
    val sport_type: String? = null,
    val start_date: String? = null,
)

@Serializable
data class StravaStreamSet(
    val latlng: StravaStream<List<List<Double>>>? = null,
    val altitude: StravaStream<List<Double>>? = null,
    val time: StravaStream<List<Int>>? = null,
)

@Serializable
data class StravaStream<T>(
    val data: T,
)
