package com.windrm.app.domain

import com.windrm.app.model.Route
import com.windrm.app.model.RoutePoint

/**
 * The part of [points] between [startM] and [endM] metres from the start, with interpolated end
 * points so the cut falls exactly there. Distances and time offsets stay as in the full track.
 */
fun cropPoints(points: List<RoutePoint>, startM: Double, endM: Double): List<RoutePoint> {
    if (points.size < 2 || endM <= startM) return points
    val inside = points.filter { it.distanceFromStartM > startM && it.distanceFromStartM < endM }
    return listOf(pointAt(points, startM)) + inside + pointAt(points, endM)
}

/**
 * The route ridden only from [startM] to [endM]: distances (and recorded time offsets) restart
 * from zero at the new start, gain is recomputed for the kept part, stops outside it are dropped.
 * The full route is returned unchanged when the range covers all of it.
 */
fun Route.cropped(startM: Double, endM: Double): Route {
    val total = points.lastOrNull()?.distanceFromStartM ?: return this
    val from = startM.coerceIn(0.0, total)
    val to = endM.coerceIn(from, total)
    // 1 m slack: the range travels through navigation as floats, which can't hold the exact total.
    if (from <= 1.0 && to >= total - 1.0) return this
    val kept = cropPoints(points, from, to)
    val startOffset = kept.first().timeOffsetS
    val rebased = kept.map { p ->
        p.copy(
            distanceFromStartM = p.distanceFromStartM - from,
            timeOffsetS = if (p.timeOffsetS != null && startOffset != null) p.timeOffsetS - startOffset else null,
        )
    }
    return copy(
        points = rebased,
        distanceKm = (to - from) / 1000.0,
        elevationGainM = elevationGainM(rebased.map { it.eleM }),
        stops = stops.filter { it.distanceM in from..to }.map { it.copy(distanceM = it.distanceM - from) },
    )
}

/** Position on the track [distanceM] from the start, linearly interpolated between its neighbours. */
fun pointAt(points: List<RoutePoint>, distanceM: Double): RoutePoint {
    if (points.size < 2) return points.first()
    var lo = 0
    var hi = points.lastIndex
    while (hi - lo > 1) {
        val mid = (lo + hi) / 2
        if (points[mid].distanceFromStartM <= distanceM) lo = mid else hi = mid
    }
    val a = points[lo]
    val b = points[hi]
    val span = b.distanceFromStartM - a.distanceFromStartM
    val t = if (span <= 0.0) 0.0 else ((distanceM - a.distanceFromStartM) / span).coerceIn(0.0, 1.0)
    val ele = if (a.eleM != null && b.eleM != null) a.eleM + (b.eleM - a.eleM) * t else a.eleM ?: b.eleM
    val time = if (a.timeOffsetS != null && b.timeOffsetS != null) {
        a.timeOffsetS + ((b.timeOffsetS - a.timeOffsetS) * t).toLong()
    } else {
        a.timeOffsetS ?: b.timeOffsetS
    }
    return RoutePoint(
        lat = a.lat + (b.lat - a.lat) * t,
        lon = a.lon + (b.lon - a.lon) * t,
        eleM = ele,
        distanceFromStartM = distanceM,
        timeOffsetS = time,
    )
}
