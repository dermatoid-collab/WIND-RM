package com.windrm.app.domain

import com.windrm.app.model.RoutePoint
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.atan

/**
 * How arrival times along the route are estimated: the same speed everywhere, terrain-aware, or
 * terrain-aware with the forecast wind (rides only; the order is the order of the choice on screen).
 */
enum class PacingMode {
    CONSTANT,
    REALISTIC,
    REALISTIC_WIND,
    ;

    val isRealistic: Boolean get() = this != CONSTANT
}

/** Rider-specific inputs of the realistic pacing model (Settings). */
data class RiderProfile(
    val riderMassKg: Double = 62.0,
    val bikeMassKg: Double = 10.0,
    val maxDescentSpeedKmh: Double = 40.0,
    /**
     * Share of the 10 m forecast wind a rider feels. From the log wind profile u(z) = u10 * ln(z/z0) / ln(10/z0)
     * at z = 1.2 m (centre of a rider's frontal area) and z0 = 0.10 m (open farmland with low crops and
     * scattered obstacles; Davenport-Wieringa): 0.54, rounded to 0.55. Open plains are nearer 0.65-0.75,
     * towns and wooded valleys 0.3-0.4.
     */
    val windHeightFactor: Double = 0.55,
)

/**
 * Terrain-aware pacing: the rider holds a constant power, so speed follows the gradient
 * (power = v * (gravity + rolling resistance) + aerodynamic drag), capped on descents at
 * [RiderProfile.maxDescentSpeedKmh]. The power is calibrated so the whole route takes exactly as
 * long as it would at the requested average speed -- the total ride time is unchanged, only how
 * it is spread between climbs and descents.
 *
 * With [WindAlongTrack] the power is still calibrated in still air, then the wind (scaled by
 * [RiderProfile.windHeightFactor]) is added to the air speed that drag acts on: a headwind slows the
 * ride, a tailwind speeds it up, and the total time is no longer the one of the requested average.
 */
object TerrainPacing {
    private const val STEP_M = 50.0
    // Gradient is taken over ~200 m (±2 steps): raw GPS altitude is too noisy point to point.
    private const val SMOOTH_HALF_STEPS = 2
    private const val MAX_GRADIENT = 0.25
    private const val G = 9.81
    private const val CRR = 0.005           // road tyres on asphalt
    private const val CDA = 0.32            // m², riding on the hoods
    private const val DRIVETRAIN_EFFICIENCY = 0.97
    private const val SEA_LEVEL_AIR_DENSITY = 1.225
    private const val MIN_SPEED_MS = 1.0    // ~3.6 km/h floor on very steep ramps

    /**
     * Seconds from the start needed to reach each of [distancesM] (metres from the start, as in
     * [RoutePoint.distanceFromStartM]) along [track].
     */
    fun offsetsSeconds(
        track: List<RoutePoint>,
        distancesM: List<Double>,
        avgSpeedKmh: Double,
        profile: RiderProfile,
        wind: WindAlongTrack? = null,
    ): List<Long> {
        val total = track.lastOrNull()?.distanceFromStartM ?: 0.0
        if (track.size < 2 || total <= 0.0 || avgSpeedKmh <= 0.0) {
            return distancesM.map { constantOffset(it, avgSpeedKmh) }
        }

        val steps = ceil(total / STEP_M).toInt().coerceAtLeast(1)
        val gridDistances = DoubleArray(steps + 1) { (it * STEP_M).coerceAtMost(total) }
        val elevations = DoubleArray(steps + 1) { elevationAt(track, gridDistances[it]) ?: 0.0 }
        val smoothed = smooth(elevations, SMOOTH_HALF_STEPS)

        val stepLength = DoubleArray(steps) { gridDistances[it + 1] - gridDistances[it] }
        val gradient = DoubleArray(steps) {
            if (stepLength[it] <= 0.0) 0.0
            else ((smoothed[it + 1] - smoothed[it]) / stepLength[it]).coerceIn(-MAX_GRADIENT, MAX_GRADIENT)
        }
        val massKg = profile.riderMassKg + profile.bikeMassKg
        val maxSpeedMs = (profile.maxDescentSpeedKmh / 3.6).coerceAtLeast(MIN_SPEED_MS)
        // Per-step forces don't depend on power: compute them once, outside the calibration loop.
        val resistN = DoubleArray(steps) {
            val angle = atan(gradient[it])
            massKg * G * (sin(angle) + CRR * cos(angle))
        }
        val dragFactor = DoubleArray(steps) {
            val altitude = (elevations[it] + elevations[it + 1]) / 2
            0.5 * SEA_LEVEL_AIR_DENSITY * exp(-altitude / 8500.0) * CDA
        }

        // Wind against the rider on each step (m/s at rider height); still air when no wind is given.
        val stillAir = DoubleArray(steps)
        val headwindMs = if (wind == null) stillAir else DoubleArray(steps) {
            val bearing = bearingDeg(track, gridDistances[it], gridDistances[it + 1])
            wind.headwindMs((gridDistances[it] + gridDistances[it + 1]) / 2, bearing) * profile.windHeightFactor
        }

        fun stepSpeeds(powerW: Double, headwind: DoubleArray) = DoubleArray(steps) {
            speedFor(powerW * DRIVETRAIN_EFFICIENCY, resistN[it], dragFactor[it], headwind[it], maxSpeedMs)
        }
        fun totalTime(powerW: Double): Double {
            val speeds = stepSpeeds(powerW, stillAir)
            var t = 0.0
            for (i in 0 until steps) t += stepLength[i] / speeds[i]
            return t
        }

        // Total time falls as power rises, so bisect for the power matching the requested average.
        val targetTime = total / (avgSpeedKmh / 3.6)
        var low = 1.0
        var high = 2000.0
        repeat(50) {
            val mid = (low + high) / 2
            if (totalTime(mid) > targetTime) low = mid else high = mid
        }
        val speeds = stepSpeeds((low + high) / 2, headwindMs)

        val cumulative = DoubleArray(steps + 1)
        for (i in 0 until steps) cumulative[i + 1] = cumulative[i] + stepLength[i] / speeds[i]

        return distancesM.map { d ->
            val x = d.coerceIn(0.0, total)
            val i = (x / STEP_M).toInt().coerceAtMost(steps - 1)
            val within = if (stepLength[i] > 0) (x - gridDistances[i]) / stepLength[i] else 0.0
            (cumulative[i] + within * (cumulative[i + 1] - cumulative[i])).toLong()
        }
    }

    /**
     * Steady ground speed (m/s) at which [wheelPower] balances gravity + rolling ([resist]) and air
     * drag, which acts on the speed relative to the air: ground speed plus [headwind] (negative = tailwind).
     */
    private fun speedFor(wheelPower: Double, resist: Double, dragFactor: Double, headwind: Double, maxSpeedMs: Double): Double {
        // f(v) = v * (resist + drag * (v + headwind) * |v + headwind|) - P: negative at 0, one root above any dip.
        var low = 0.0
        var high = 40.0
        repeat(50) {
            val mid = (low + high) / 2
            val air = mid + headwind
            if (mid * (resist + dragFactor * air * kotlin.math.abs(air)) - wheelPower < 0) low = mid else high = mid
        }
        return ((low + high) / 2).coerceIn(MIN_SPEED_MS, maxSpeedMs)
    }

    /** Compass bearing (degrees) of the track between two distances from its start. */
    private fun bearingDeg(track: List<RoutePoint>, fromM: Double, toM: Double): Double {
        val a = pointAt(track, fromM)
        val b = pointAt(track, toM)
        val phi1 = Math.toRadians(a.lat)
        val phi2 = Math.toRadians(b.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val y = sin(dLon) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
        return (Math.toDegrees(kotlin.math.atan2(y, x)) + 360.0) % 360.0
    }

    internal fun smooth(values: DoubleArray, halfWindow: Int): DoubleArray {
        val n = values.size
        val prefix = DoubleArray(n + 1)
        for (i in 0 until n) prefix[i + 1] = prefix[i] + values[i]
        return DoubleArray(n) {
            val from = (it - halfWindow).coerceAtLeast(0)
            val to = (it + halfWindow).coerceAtMost(n - 1)
            (prefix[to + 1] - prefix[from]) / (to - from + 1)
        }
    }

    internal fun elevationAt(track: List<RoutePoint>, distanceM: Double): Double? {
        var lo = 0
        var hi = track.lastIndex
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if (track[mid].distanceFromStartM <= distanceM) lo = mid else hi = mid
        }
        val a = track[lo]
        val b = track[hi]
        val ea = a.eleM
        val eb = b.eleM
        if (ea == null || eb == null) return ea ?: eb
        val span = b.distanceFromStartM - a.distanceFromStartM
        val t = if (span <= 0.0) 0.0 else ((distanceM - a.distanceFromStartM) / span).coerceIn(0.0, 1.0)
        return ea + (eb - ea) * t
    }

    internal fun constantOffset(distanceM: Double, avgSpeedKmh: Double): Long =
        if (avgSpeedKmh <= 0.0) 0L else ((distanceM / 1000.0) / avgSpeedKmh * 3600.0).toLong()
}
