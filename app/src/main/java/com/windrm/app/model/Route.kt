package com.windrm.app.model

import com.windrm.app.domain.ActivityType
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

/** A planned break along the route: the ride resumes [durationMin] minutes later from the same point. */
@Serializable
data class RouteStop(
    val distanceM: Double,
    val lat: Double,
    val lon: Double,
    val durationMin: Int,
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
    /** Strava route id (from the Route Builder) this was imported from, if any. */
    val stravaRouteId: Long? = null,
    /** Starred by the user; listed under Favorites. */
    val isFavorite: Boolean = false,
    /**
     * When the route itself was created or recorded (Strava created_at / start_date, or the GPX's
     * own time), as opposed to [createdAtEpochMs], when it was imported into the app. Null if unknown.
     */
    val originalDateEpochMs: Long? = null,
    /** Planned breaks, in route order; they push back every arrival time after them. */
    val stops: List<RouteStop> = emptyList(),
    /** Ride or trek: picks the realistic pacing model. */
    val activity: ActivityType = ActivityType.RIDE,
    /** Waypoints and stretches as the route builder drew them; null for a route that came from a GPX file or Strava. */
    val builderState: BuilderState? = null,
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
