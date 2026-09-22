package com.windrm.app.domain

import com.windrm.app.model.Route
import com.windrm.app.model.RoutePoint
import java.time.Instant

/**
 * Estimates when the rider will reach each sampled point: it uses the GPX's own recorded pace
 * when available ([Route.hasTimestamps]), otherwise a constant average speed from the route start.
 */
object ArrivalTimeCalculator {
    fun arrivalTimes(route: Route, samples: List<RoutePoint>, startTime: Instant, avgSpeedKmh: Double): List<Instant> =
        samples.map { point ->
            val offsetSeconds = if (route.hasTimestamps && point.timeOffsetS != null) {
                point.timeOffsetS
            } else {
                estimateOffsetSeconds(point, avgSpeedKmh)
            }
            startTime.plusSeconds(offsetSeconds)
        }

    private fun estimateOffsetSeconds(point: RoutePoint, avgSpeedKmh: Double): Long {
        if (avgSpeedKmh <= 0.0) return 0L
        val hours = (point.distanceFromStartM / 1000.0) / avgSpeedKmh
        return (hours * 3600.0).toLong()
    }
}
