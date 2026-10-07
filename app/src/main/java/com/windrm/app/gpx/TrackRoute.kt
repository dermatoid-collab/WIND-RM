package com.windrm.app.gpx

import com.windrm.app.domain.elevationGainM
import com.windrm.app.domain.haversineMeters
import com.windrm.app.model.Route
import com.windrm.app.model.RoutePoint
import com.windrm.app.model.RouteSource
import java.time.Instant
import java.time.format.DateTimeParseException

/** One position read from a track file, before distances and time offsets are worked out. */
internal data class RawTrackPoint(val lat: Double, val lon: Double, val ele: Double?, val time: Instant?)

/**
 * Turns the points of any track file (GPX, TCX...) into a [Route]: cumulative distance, total climb,
 * and -- only when every point carries a time -- the recorded pace. [label] names the format in the error.
 */
internal fun buildTrackRoute(rawPoints: List<RawTrackPoint>, name: String, fileTime: Instant?, label: String): Route {
    if (rawPoints.size < 2) {
        throw GpxParseException("The $label file doesn't contain a valid route (at least 2 points are required).")
    }

    val hasTimestamps = rawPoints.all { it.time != null }
    val startTime = if (hasTimestamps) rawPoints.first().time else null

    var cumulativeDistance = 0.0
    val points = ArrayList<RoutePoint>(rawPoints.size)
    for ((index, raw) in rawPoints.withIndex()) {
        if (index > 0) {
            val prev = rawPoints[index - 1]
            cumulativeDistance += haversineMeters(prev.lat, prev.lon, raw.lat, raw.lon)
        }
        val timeOffsetS = if (hasTimestamps && startTime != null && raw.time != null) {
            raw.time.epochSecond - startTime.epochSecond
        } else null
        points += RoutePoint(
            lat = raw.lat,
            lon = raw.lon,
            eleM = raw.ele,
            distanceFromStartM = cumulativeDistance,
            timeOffsetS = timeOffsetS,
        )
    }

    return Route(
        name = name,
        source = RouteSource.LOCAL,
        createdAtEpochMs = System.currentTimeMillis(),
        points = points,
        distanceKm = cumulativeDistance / 1000.0,
        elevationGainM = elevationGainM(rawPoints.map { it.ele }),
        hasTimestamps = hasTimestamps,
        originalDateEpochMs = (fileTime ?: rawPoints.firstNotNullOfOrNull { it.time })?.toEpochMilli(),
    )
}

internal fun parseInstantOrNull(raw: String): Instant? = try {
    if (raw.isEmpty()) null else Instant.parse(raw)
} catch (e: DateTimeParseException) {
    null
}
