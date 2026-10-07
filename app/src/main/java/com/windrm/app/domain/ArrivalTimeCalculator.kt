package com.windrm.app.domain

import com.windrm.app.model.Route
import com.windrm.app.model.RoutePoint
import java.time.Instant

/**
 * Estimates when the rider will reach each sampled point. The realistic modes spread the ride
 * time by gradient ([TerrainPacing]) on a ride, or follows the CAI trail-time rule ([CaiPacing]) on a
 * trek; [PacingMode.CONSTANT] uses the GPX's own recorded pace when
 * available ([Route.hasTimestamps]), otherwise a constant average speed from the route start.
 * Either way, every point past a planned stop ([Route.stops]) is pushed back by its duration.
 */
object ArrivalTimeCalculator {
    fun arrivalTimes(
        route: Route,
        samples: List<RoutePoint>,
        startTime: Instant,
        avgSpeedKmh: Double,
        pacing: PacingMode = PacingMode.CONSTANT,
        profile: RiderProfile = RiderProfile(),
        activity: ActivityType = ActivityType.RIDE,
        cai: CaiProfile = CaiProfile(),
        /** Forecast wind along the route; used only by a ride in [PacingMode.REALISTIC_WIND]. */
        wind: WindAlongTrack? = null,
    ): List<Instant> {
        val distances = samples.map { it.distanceFromStartM }
        val ridingOffsets: List<Long> = if (pacing.isRealistic) {
            when (activity) {
                ActivityType.RIDE -> TerrainPacing.offsetsSeconds(
                    route.points, distances, avgSpeedKmh, profile,
                    wind = if (pacing == PacingMode.REALISTIC_WIND) wind else null,
                )
                ActivityType.TREK -> CaiPacing.offsetsSeconds(route.points, distances, avgSpeedKmh, cai)
            }
        } else {
            samples.map { point ->
                if (route.hasTimestamps && point.timeOffsetS != null) point.timeOffsetS else estimateOffsetSeconds(point, avgSpeedKmh)
            }
        }
        return samples.mapIndexed { index, point ->
            val stopSeconds = route.stops.filter { it.distanceM < point.distanceFromStartM }.sumOf { it.durationMin * 60L }
            startTime.plusSeconds(ridingOffsets[index] + stopSeconds)
        }
    }

    private fun estimateOffsetSeconds(point: RoutePoint, avgSpeedKmh: Double): Long {
        if (avgSpeedKmh <= 0.0) return 0L
        val hours = (point.distanceFromStartM / 1000.0) / avgSpeedKmh
        return (hours * 3600.0).toLong()
    }
}
