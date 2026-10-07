package com.windrm.app.domain

import com.windrm.app.model.Route
import com.windrm.app.model.RoutePoint
import com.windrm.app.model.RouteSource
import com.windrm.app.repository.RoutedPoint
import com.windrm.app.repository.RoutedSegment

/**
 * Joins consecutive routed stretches into one track: the shared junction point is kept once,
 * distances run cumulatively from the start, and the climb is totalled over the whole profile.
 */
fun assembleRoute(name: String, activity: ActivityType, segments: List<RoutedSegment>, nowEpochMs: Long): Route? {
    val joined = ArrayList<RoutedPoint>()
    for (segment in segments) {
        val skipFirst = joined.isNotEmpty() && segment.points.isNotEmpty() && samePlace(joined.last(), segment.points.first())
        joined.addAll(if (skipFirst) segment.points.drop(1) else segment.points)
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
    )
}

private fun samePlace(a: RoutedPoint, b: RoutedPoint): Boolean =
    haversineMeters(a.lat, a.lon, b.lat, b.lon) < 1.0
