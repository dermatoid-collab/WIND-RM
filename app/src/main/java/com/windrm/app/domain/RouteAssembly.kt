package com.windrm.app.domain

import com.windrm.app.model.BuilderLeg
import com.windrm.app.model.BuilderState
import com.windrm.app.model.LatLon
import com.windrm.app.model.Route
import com.windrm.app.model.RoutePoint
import com.windrm.app.model.RouteSource
import com.windrm.app.model.RouteStop
import com.windrm.app.repository.RoutedPoint
import com.windrm.app.repository.RoutedSegment

/**
 * Joins consecutive routed stretches into one track: the shared junction point is kept once,
 * distances run cumulatively from the start, and the climb is totalled over the whole profile.
 * The route also carries the builder's waypoints and stretches ([Route.builderState]), so it can be edited again.
 */
fun assembleRoute(
    name: String,
    activity: ActivityType,
    segments: List<RoutedSegment>,
    nowEpochMs: Long,
    allowUnpaved: Boolean = false,
    shortest: Boolean = true,
): Route? {
    val joined = ArrayList<RoutedPoint>()
    // Where each waypoint ended up in the joined track: the start, then the end of every stretch.
    val junctions = ArrayList<Int>()
    for (segment in segments) {
        val skipFirst = joined.isNotEmpty() && segment.points.isNotEmpty() && samePlace(joined.last(), segment.points.first())
        joined.addAll(if (skipFirst) segment.points.drop(1) else segment.points)
        if (junctions.isEmpty()) junctions += 0
        junctions += joined.lastIndex
    }
    if (joined.size < 2) return null

    var distanceM = 0.0
    val points = joined.mapIndexed { i, p ->
        if (i > 0) distanceM += haversineMeters(joined[i - 1].lat, joined[i - 1].lon, p.lat, p.lon)
        RoutePoint(lat = p.lat, lon = p.lon, eleM = p.eleM, distanceFromStartM = distanceM)
    }
    return Route(
        name = name,
        source = RouteSource.LOCAL,
        createdAtEpochMs = nowEpochMs,
        points = points,
        distanceKm = distanceM / 1000.0,
        elevationGainM = elevationGainM(points.map { it.eleM }),
        hasTimestamps = false,
        originalDateEpochMs = nowEpochMs,
        activity = activity,
        builderState = BuilderState(
            allowUnpaved = allowUnpaved,
            shortest = shortest,
            junctions = junctions,
            legs = segments.map { s ->
                BuilderLeg(
                    manual = s.manual,
                    pavedM = s.pavedM,
                    unpavedM = s.unpavedM,
                    trailM = s.trailM,
                    surfaceKnown = s.surfaceKnown,
                    roughFromM = s.roughSpans.map { it.first },
                    roughToM = s.roughSpans.map { it.second },
                )
            },
        ),
    )
}

/** A saved route opened in the builder: its waypoints, and the stretches between them (the track cut at the waypoints). */
data class BuilderDraft(val waypoints: List<LatLon>, val segments: List<RoutedSegment>, val allowUnpaved: Boolean, val shortest: Boolean)

/**
 * The builder's waypoints and stretches for [route]. A route drawn in the builder gets back exactly what was saved.
 * Any other (a GPX file, Strava) gets about [MAX_IMPORTED_WAYPOINTS] waypoints at its most characteristic points, and
 * the track between them is kept as it is, as stretches whose surface is not known.
 */
fun draftOf(route: Route): BuilderDraft? {
    val points = route.points
    if (points.size < 2) return null
    val saved = route.builderState?.takeIf { it.isValidFor(points.size) }
    val junctions = saved?.junctions ?: waypointIndices(points, MAX_IMPORTED_WAYPOINTS)
    val segments = (0 until junctions.size - 1).map { i ->
        val leg = saved?.legs?.get(i)
        val slice = points.subList(junctions[i], junctions[i + 1] + 1).map { RoutedPoint(it.lat, it.lon, it.eleM) }
        RoutedSegment(
            // A stretch needs two points even where two waypoints sit on the very same one.
            points = if (slice.size >= 2) slice else listOf(slice.first(), slice.first()),
            pavedM = leg?.pavedM ?: 0.0,
            unpavedM = leg?.unpavedM ?: 0.0,
            trailM = leg?.trailM ?: 0.0,
            surfaceKnown = leg?.surfaceKnown ?: false,
            roughSpans = leg?.let { l -> l.roughFromM.zip(l.roughToM) }.orEmpty(),
            manual = leg?.manual ?: false,
        )
    }
    return BuilderDraft(
        waypoints = junctions.map { LatLon(points[it].lat, points[it].lon) },
        segments = segments,
        allowUnpaved = saved?.allowUnpaved ?: false,
        shortest = saved?.shortest ?: true,
    )
}

/**
 * Planned stops moved onto a new track: each goes to the nearest point of it, keeping its duration, so a route that was
 * edited keeps all its stops (a stop far from the new track lands on its nearest point rather than being dropped).
 */
fun remapStops(stops: List<RouteStop>, track: List<RoutePoint>): List<RouteStop> {
    if (track.isEmpty()) return stops
    return stops.map { stop ->
        val nearest = track.minBy { haversineMeters(stop.lat, stop.lon, it.lat, it.lon) }
        stop.copy(distanceM = nearest.distanceFromStartM, lat = nearest.lat, lon = nearest.lon)
    }.sortedBy { it.distanceM }
}

const val MAX_IMPORTED_WAYPOINTS = 30

private fun samePlace(a: RoutedPoint, b: RoutedPoint): Boolean =
    haversineMeters(a.lat, a.lon, b.lat, b.lon) < 1.0
