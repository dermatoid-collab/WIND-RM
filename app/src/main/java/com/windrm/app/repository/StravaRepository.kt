package com.windrm.app.repository

import com.windrm.app.domain.haversineMeters
import com.windrm.app.gpx.GpxParser
import com.windrm.app.model.Route
import com.windrm.app.model.RoutePoint
import com.windrm.app.model.RouteSource
import com.windrm.app.remote.strava.StravaActivitySummary
import com.windrm.app.remote.strava.StravaApi
import com.windrm.app.remote.strava.StravaAuthManager
import com.windrm.app.remote.strava.StravaRouteSummary
import com.windrm.app.remote.strava.StravaSegmentSummary
import com.windrm.app.remote.strava.StravaStreamSet

class StravaRepository(
    private val api: StravaApi,
    private val authManager: StravaAuthManager,
) {
    suspend fun listActivities(): List<StravaActivitySummary> {
        val token = authManager.bearerToken() ?: return emptyList()
        return api.listActivities(bearerToken = token)
    }

    /** Routes drawn with Strava's Route Builder (not recorded activities). */
    suspend fun listRoutes(): List<StravaRouteSummary> {
        val token = authManager.bearerToken() ?: return emptyList()
        val athleteId = authManager.athleteId() ?: return emptyList()
        return api.listAthleteRoutes(bearerToken = token, athleteId = athleteId)
    }

    suspend fun listSegments(): List<StravaSegmentSummary> {
        val token = authManager.bearerToken() ?: return emptyList()
        return api.listStarredSegments(bearerToken = token)
    }

    /** Downloads a route's GPX export and parses it with the same [GpxParser] used for local files. */
    suspend fun importRouteAsRoute(route: StravaRouteSummary): Route {
        val token = authManager.bearerToken() ?: error("Strava not authorized")
        val gpx = api.getRouteGpx(bearerToken = token, routeId = route.id)
        val parsed = gpx.byteStream().use { GpxParser.parse(it, route.name) }
        return parsed.copy(
            source = RouteSource.STRAVA,
            distanceKm = if (parsed.distanceKm > 0) parsed.distanceKm else route.distance / 1000.0,
            elevationGainM = if (parsed.elevationGainM > 0) parsed.elevationGainM else route.elevation_gain,
            stravaRouteId = route.id,
        )
    }

    suspend fun importActivityAsRoute(activity: StravaActivitySummary): Route {
        val token = authManager.bearerToken() ?: error("Strava not authorized")
        val streams = api.getActivityStreams(bearerToken = token, activityId = activity.id)
        return buildRouteFromStreams(
            name = activity.name,
            streams = streams,
            fallbackDistanceKm = activity.distance / 1000.0,
            fallbackElevationGainM = activity.total_elevation_gain,
        )
    }

    suspend fun importSegmentAsRoute(segment: StravaSegmentSummary): Route {
        val token = authManager.bearerToken() ?: error("Strava not authorized")
        val streams = api.getSegmentStreams(bearerToken = token, segmentId = segment.id)
        return buildRouteFromStreams(
            name = segment.name,
            streams = streams,
            fallbackDistanceKm = segment.distance / 1000.0,
            fallbackElevationGainM = (segment.elevation_high - segment.elevation_low).coerceAtLeast(0.0),
        )
    }

    /** Shared by activity/segment import: both expose the same lat/lng+altitude(+time) stream shape. */
    private fun buildRouteFromStreams(
        name: String,
        streams: StravaStreamSet,
        fallbackDistanceKm: Double,
        fallbackElevationGainM: Double,
    ): Route {
        val latlng = streams.latlng?.data.orEmpty()
        val altitude = streams.altitude?.data
        val time = streams.time?.data

        require(latlng.size >= 2) { "Not enough GPS points for this item." }

        var cumulativeDistance = 0.0
        var elevationGain = 0.0
        val points = ArrayList<RoutePoint>(latlng.size)
        for ((index, coord) in latlng.withIndex()) {
            val lat = coord.getOrNull(0) ?: continue
            val lon = coord.getOrNull(1) ?: continue
            val ele = altitude?.getOrNull(index)
            if (index > 0) {
                val prev = latlng[index - 1]
                cumulativeDistance += haversineMeters(prev[0], prev[1], lat, lon)
                val prevEle = altitude?.getOrNull(index - 1)
                if (prevEle != null && ele != null) {
                    val delta = ele - prevEle
                    if (delta > 1.0) elevationGain += delta
                }
            }
            points += RoutePoint(
                lat = lat,
                lon = lon,
                eleM = ele,
                distanceFromStartM = cumulativeDistance,
                timeOffsetS = time?.getOrNull(index)?.toLong(),
            )
        }

        return Route(
            name = name,
            source = RouteSource.STRAVA,
            createdAtEpochMs = System.currentTimeMillis(),
            points = points,
            distanceKm = if (cumulativeDistance > 0) cumulativeDistance / 1000.0 else fallbackDistanceKm,
            elevationGainM = if (elevationGain > 0) elevationGain else fallbackElevationGainM,
            hasTimestamps = time != null,
        )
    }
}
