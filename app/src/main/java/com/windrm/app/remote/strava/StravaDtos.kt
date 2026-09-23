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

/** Shared shape of the `map` field on activities, routes and segments alike. */
@Serializable
data class StravaMapSummary(
    val polyline: String? = null,
    val summary_polyline: String? = null,
) {
    /** Whichever encoded polyline is present -- used only for list-row previews. */
    val anyPolyline: String? get() = polyline ?: summary_polyline
}

/** A recorded ride, as opposed to a planned [StravaRouteSummary]. */
@Serializable
data class StravaActivitySummary(
    val id: Long,
    val name: String,
    val distance: Double = 0.0,
    val moving_time: Long = 0,
    val total_elevation_gain: Double = 0.0,
    val type: String? = null,
    val start_date: String? = null,
    /** Meters per second. */
    val average_speed: Double = 0.0,
    val map: StravaMapSummary? = null,
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
    val created_at: String? = null,
    val map: StravaMapSummary? = null,
)

/** A segment the athlete has starred (Strava's API only exposes starred segments, not all of them). */
@Serializable
data class StravaSegmentSummary(
    val id: Long,
    val name: String,
    val distance: Double = 0.0,
    val elevation_high: Double = 0.0,
    val elevation_low: Double = 0.0,
    val average_grade: Double = 0.0,
    val map: StravaMapSummary? = null,
)

/** Shared shape of `/activities/{id}/streams` and `/segments/{id}/streams` (segments have no `time`). */
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
