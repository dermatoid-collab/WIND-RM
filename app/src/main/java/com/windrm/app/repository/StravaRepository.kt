package com.windrm.app.repository

import com.windrm.app.gpx.GpxParser
import com.windrm.app.model.Route
import com.windrm.app.model.RouteSource
import com.windrm.app.remote.strava.StravaApi
import com.windrm.app.remote.strava.StravaAuthManager
import com.windrm.app.remote.strava.StravaRouteSummary

class StravaRepository(
    private val api: StravaApi,
    private val authManager: StravaAuthManager,
) {
    /** Routes drawn with Strava's Route Builder (not recorded activities). */
    suspend fun listRoutes(): List<StravaRouteSummary> {
        val token = authManager.bearerToken() ?: return emptyList()
        val athleteId = authManager.athleteId() ?: return emptyList()
        return api.listAthleteRoutes(bearerToken = token, athleteId = athleteId)
    }

    /** Downloads a route's GPX export and parses it with the same [GpxParser] used for local files. */
    suspend fun importRouteAsRoute(route: StravaRouteSummary): Route {
        val token = authManager.bearerToken() ?: error("Strava non autorizzato")
        val gpx = api.getRouteGpx(bearerToken = token, routeId = route.id)
        val parsed = gpx.byteStream().use { GpxParser.parse(it, route.name) }
        return parsed.copy(
            source = RouteSource.STRAVA,
            distanceKm = if (parsed.distanceKm > 0) parsed.distanceKm else route.distance / 1000.0,
            elevationGainM = if (parsed.elevationGainM > 0) parsed.elevationGainM else route.elevation_gain,
            stravaRouteId = route.id,
        )
    }
}
