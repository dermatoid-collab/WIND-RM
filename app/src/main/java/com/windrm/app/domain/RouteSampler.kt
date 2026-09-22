package com.windrm.app.domain

import com.windrm.app.model.Route
import com.windrm.app.model.RoutePoint

/**
 * Picks an evenly-distance-spaced subset of a route's points to query the weather API for,
 * keeping request size (and URL length) reasonable for long routes while still resolving
 * short ones point-by-point.
 */
object RouteSampler {
    private const val MIN_SAMPLES = 6
    private const val MAX_SAMPLES = 45
    private const val TARGET_SPACING_M = 3000.0

    fun sample(route: Route): List<RoutePoint> {
        val points = route.points
        if (points.size <= MIN_SAMPLES) return points

        val totalDistanceM = points.last().distanceFromStartM
        if (totalDistanceM <= 0.0) return listOf(points.first(), points.last())

        val estimatedCount = (totalDistanceM / TARGET_SPACING_M).toInt() + 1
        val sampleCount = estimatedCount.coerceIn(MIN_SAMPLES, MAX_SAMPLES)
        val step = totalDistanceM / (sampleCount - 1)

        val result = ArrayList<RoutePoint>(sampleCount)
        var pointIndex = 0
        for (i in 0 until sampleCount) {
            val targetDistance = step * i
            while (pointIndex < points.size - 1 && points[pointIndex + 1].distanceFromStartM < targetDistance) {
                pointIndex++
            }
            result += points[pointIndex]
        }
        result[result.size - 1] = points.last()
        return result
    }
}
