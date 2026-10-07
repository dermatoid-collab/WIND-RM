package com.windrm.app.domain

import com.windrm.app.model.RoutePoint
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** What the route is for: it picks the realistic pacing model and the default speed. */
enum class ActivityType { RIDE, TREK }

/**
 * Walking-time conventions of CAI trail signposts (Settings): on the flat [flatKmh], climbing
 * [upMetresPerHour], descending [downMetresPerHour]; above [highAltitudeM] the thinner air slows
 * both to the high-altitude rates. Times are net, with no breaks.
 */
data class CaiProfile(
    val flatKmh: Double = 4.0,
    val upMetresPerHour: Double = 300.0,
    val downMetresPerHour: Double = 500.0,
    val highAltitudeM: Double = 2800.0,
    val highUpMetresPerHour: Double = 250.0,
    val highDownMetresPerHour: Double = 400.0,
)

/**
 * Trekking times by the CAI rule: for each 50 m step, the time for the horizontal distance and the
 * time for the height gained or lost are worked out separately, then the larger counts in full and
 * the smaller by half. The sum is the signpost time; [avgSpeedKmh] scales it as a personal pace,
 * where [CaiProfile.flatKmh] (4 km/h) is exactly the CAI standard.
 */
object CaiPacing {
    private const val STEP_M = 50.0
    // Same ~200 m gradient smoothing as the cycling model: raw GPS altitude is noisy point to point.
    private const val SMOOTH_HALF_STEPS = 2

    fun offsetsSeconds(
        track: List<RoutePoint>,
        distancesM: List<Double>,
        avgSpeedKmh: Double,
        profile: CaiProfile,
    ): List<Long> {
        val total = track.lastOrNull()?.distanceFromStartM ?: 0.0
        if (track.size < 2 || total <= 0.0 || avgSpeedKmh <= 0.0 || profile.flatKmh <= 0.0) {
            return distancesM.map { TerrainPacing.constantOffset(it, avgSpeedKmh) }
        }
        val paceFactor = profile.flatKmh / avgSpeedKmh

        val steps = ceil(total / STEP_M).toInt().coerceAtLeast(1)
        val gridDistances = DoubleArray(steps + 1) { (it * STEP_M).coerceAtMost(total) }
        val elevations = DoubleArray(steps + 1) { TerrainPacing.elevationAt(track, gridDistances[it]) ?: 0.0 }
        val smoothed = TerrainPacing.smooth(elevations, SMOOTH_HALF_STEPS)

        val cumulative = DoubleArray(steps + 1)
        for (i in 0 until steps) {
            val length = gridDistances[i + 1] - gridDistances[i]
            val climb = smoothed[i + 1] - smoothed[i]
            val high = (smoothed[i] + smoothed[i + 1]) / 2 >= profile.highAltitudeM
            val upRate = if (high) profile.highUpMetresPerHour else profile.upMetresPerHour
            val downRate = if (high) profile.highDownMetresPerHour else profile.downMetresPerHour
            val horizontalS = length / (profile.flatKmh / 3.6)
            val verticalS = when {
                climb > 0 && upRate > 0 -> climb / upRate * 3600.0
                climb < 0 && downRate > 0 -> -climb / downRate * 3600.0
                else -> 0.0
            }
            val stepS = max(horizontalS, verticalS) + min(horizontalS, verticalS) / 2
            cumulative[i + 1] = cumulative[i] + stepS * paceFactor
        }

        return distancesM.map { d ->
            val x = d.coerceIn(0.0, total)
            val i = (x / STEP_M).toInt().coerceAtMost(steps - 1)
            val length = gridDistances[i + 1] - gridDistances[i]
            val within = if (length > 0) (x - gridDistances[i]) / length else 0.0
            (cumulative[i] + within * (cumulative[i + 1] - cumulative[i])).toLong()
        }
    }

    /** Net walking time of the whole [track] in seconds. */
    fun totalSeconds(track: List<RoutePoint>, avgSpeedKmh: Double, profile: CaiProfile): Long {
        val total = track.lastOrNull()?.distanceFromStartM ?: return 0L
        return offsetsSeconds(track, listOf(total), avgSpeedKmh, profile).first()
    }
}
