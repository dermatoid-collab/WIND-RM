package com.windrm.app.repository

import com.windrm.app.domain.haversineMeters
import com.windrm.app.model.Route
import com.windrm.app.model.RoutePoint
import com.windrm.app.model.RouteSource
import com.windrm.app.remote.strava.StravaActivitySummary
import com.windrm.app.remote.strava.StravaApi
import com.windrm.app.remote.strava.StravaAuthManager

class StravaRepository(
    private val api: StravaApi,
    private val authManager: StravaAuthManager,
) {
    suspend fun listRecentActivities(): List<StravaActivitySummary> {
        val token = authManager.bearerToken() ?: return emptyList()
        return api.listActivities(bearerToken = token)
    }

    /** Downloads an activity's lat/lng, altitude and time streams and turns them into an importable [Route]. */
    suspend fun importActivityAsRoute(activity: StravaActivitySummary): Route {
        val token = authManager.bearerToken() ?: error("Strava non autorizzato")
        val streams = api.getActivityStreams(bearerToken = token, activityId = activity.id)
        val latlng = streams.latlng?.data.orEmpty()
        val altitude = streams.altitude?.data
        val time = streams.time?.data

        require(latlng.size >= 2) { "L'attività Strava non contiene abbastanza punti GPS." }

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
            name = activity.name,
            source = RouteSource.STRAVA,
            createdAtEpochMs = System.currentTimeMillis(),
            points = points,
            distanceKm = if (cumulativeDistance > 0) cumulativeDistance / 1000.0 else activity.distance / 1000.0,
            elevationGainM = if (elevationGain > 0) elevationGain else activity.total_elevation_gain,
            hasTimestamps = time != null,
            stravaActivityId = activity.id,
        )
    }
}
