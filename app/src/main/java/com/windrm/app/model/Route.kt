package com.windrm.app.model

import kotlinx.serialization.Serializable

/** Where a [Route] was imported from. */
@Serializable
enum class RouteSource { LOCAL, STRAVA }

/** A single point of a route's polyline, with cumulative distance from the start. */
@Serializable
data class RoutePoint(
    val lat: Double,
    val lon: Double,
    val eleM: Double? = null,
    val distanceFromStartM: Double = 0.0,
    /** Seconds since the route's start, only present when the source GPX carried real timestamps. */
    val timeOffsetS: Long? = null,
)

/** A saved route: an imported GPX track or a Strava activity, ready to be forecast. */
data class Route(
    val id: Long = 0,
    val name: String,
    val source: RouteSource,
    val createdAtEpochMs: Long,
    val points: List<RoutePoint>,
    val distanceKm: Double,
    val elevationGainM: Double,
    /** True when [RoutePoint.timeOffsetS] reflects a real recorded pace rather than a constant speed. */
    val hasTimestamps: Boolean = false,
    /** Strava activity id this route was imported from, if any. */
    val stravaActivityId: Long? = null,
) {
    val startPoint: RoutePoint? get() = points.firstOrNull()

    /** Average speed in km/h implied by the recorded timestamps, if available. */
    val recordedAvgSpeedKmh: Double?
        get() {
            val last = points.lastOrNull()?.timeOffsetS ?: return null
            if (last <= 0) return null
            return (distanceKm) / (last / 3600.0)
        }
}
