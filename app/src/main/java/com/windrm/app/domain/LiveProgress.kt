package com.windrm.app.domain

import com.windrm.app.model.RoutePoint
import com.windrm.app.model.RouteStop
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Finds where on a track a GPS position is: the point of the track nearest to it, as a distance from
 * the start, plus how far the position is from the track.
 *
 * A route that passes the same road twice (out and back, a loop) has two candidate places for one
 * position, so after the first fix the search stays near the last known place instead of taking the
 * globally nearest point; [passes] lists the candidates for the very first fix.
 */
class TrackMatcher(private val track: List<RoutePoint>) {

    /** [distanceM] along the track of the point nearest to the position, [offTrackM] away from it; [bearingDeg] is the way the track runs there. */
    data class Match(val distanceM: Double, val offTrackM: Double, val lat: Double, val lon: Double, val bearingDeg: Double = 0.0)

    private val distances = DoubleArray(track.size) { track[it].distanceFromStartM }
    val totalM: Double get() = distances.lastOrNull() ?: 0.0

    /** One candidate per distinct pass of the track near the position, nearest first by distance along the track. */
    fun passes(lat: Double, lon: Double): List<Match> {
        if (track.size < 2) return emptyList()
        val all = (0 until track.size - 1).map { project(lat, lon, it) }
        val best = all.minOf { it.offTrackM }
        val near = all.filter { it.offTrackM <= max(best + PASS_TOLERANCE_M, MIN_PASS_RADIUS_M) }.sortedBy { it.distanceM }
        val passes = ArrayList<Match>()
        var group = ArrayList<Match>()
        for (candidate in near) {
            if (group.isNotEmpty() && candidate.distanceM - group.last().distanceM > SAME_PASS_M) {
                passes += group.minBy { it.offTrackM }
                group = ArrayList()
            }
            group += candidate
        }
        if (group.isNotEmpty()) passes += group.minBy { it.offTrackM }
        return passes
    }

    /**
     * The place on the track for a later fix, given the [previousM] place of the one before. Where the track
     * runs over the same road both ways (at a turnaround, say) the rider's [headingDeg] picks the leg whose
     * direction matches; without a heading, the place nearest to [previousM] wins.
     */
    fun match(lat: Double, lon: Double, previousM: Double, headingDeg: Double? = null): Match? {
        if (track.size < 2) return null
        val inWindow = (segmentsFrom(previousM - BACK_M)..segmentsTo(previousM + AHEAD_M)).map { project(lat, lon, it) }
        val windowBest = inWindow.minByOrNull { it.offTrackM }
        if (windowBest != null && windowBest.offTrackM <= KEEP_THREAD_M) {
            val ties = inWindow.filter { it.offTrackM <= windowBest.offTrackM + TIE_M }
            if (ties.size == 1) return windowBest
            return if (headingDeg != null) {
                ties.minWith(compareBy<Match>({ angleBetween(it.bearingDeg, headingDeg) }, { abs(it.distanceM - previousM) }))
            } else {
                ties.minBy { abs(it.distanceM - previousM) }
            }
        }
        // The thread is lost (a long detour, a shortcut, a gap in the signal): look at the whole track and take the
        // nearby place closest to where the rider was.
        val reacquired = passes(lat, lon)
            .filter { it.offTrackM <= REACQUIRE_M }
            .minByOrNull { abs(it.distanceM - previousM) }
        return reacquired ?: windowBest ?: passes(lat, lon).minByOrNull { it.offTrackM }
    }

    /** Smallest angle in degrees (0..180) between two compass bearings. */
    private fun angleBetween(a: Double, b: Double): Double = abs(((a - b + 540.0) % 360.0) - 180.0)

    private fun segmentsFrom(distanceM: Double): Int {
        val index = java.util.Arrays.binarySearch(distances, distanceM).let { if (it >= 0) it else -it - 2 }
        return index.coerceIn(0, track.size - 2)
    }

    private fun segmentsTo(distanceM: Double): Int {
        val index = java.util.Arrays.binarySearch(distances, distanceM).let { if (it >= 0) it else -it - 1 }
        return (index - 1).coerceIn(0, track.size - 2)
    }

    /** The position projected onto segment [i] of the track, in a flat local frame (fine over a few hundred metres). */
    private fun project(lat: Double, lon: Double, i: Int): Match {
        val a = track[i]
        val b = track[i + 1]
        val mPerDegLon = METRES_PER_DEG_LAT * cos(Math.toRadians(lat))
        val ax = (a.lon - lon) * mPerDegLon
        val ay = (a.lat - lat) * METRES_PER_DEG_LAT
        val dx = (b.lon - a.lon) * mPerDegLon
        val dy = (b.lat - a.lat) * METRES_PER_DEG_LAT
        val len2 = dx * dx + dy * dy
        val t = if (len2 <= 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
        val off = hypot(ax + t * dx, ay + t * dy)
        return Match(
            distanceM = distances[i] + t * (distances[i + 1] - distances[i]),
            offTrackM = off,
            lat = a.lat + (b.lat - a.lat) * t,
            lon = a.lon + (b.lon - a.lon) * t,
            bearingDeg = (Math.toDegrees(kotlin.math.atan2(dx, dy)) + 360.0) % 360.0,
        )
    }

    private companion object {
        const val METRES_PER_DEG_LAT = 111_320.0
        const val BACK_M = 150.0
        const val AHEAD_M = 800.0
        const val KEEP_THREAD_M = 120.0
        const val REACQUIRE_M = 60.0
        const val PASS_TOLERANCE_M = 20.0
        const val MIN_PASS_RADIUS_M = 40.0
        const val SAME_PASS_M = 500.0
        const val TIE_M = 15.0
    }
}

/**
 * Planned time along the route: seconds spent riding up to each of [distancesM] (ascending), plus
 * the planned [stops]. Gives what is left from any point, and the pace actually ridden against the plan.
 */
class TimeProfile(
    private val distancesM: DoubleArray,
    private val ridingS: DoubleArray,
    private val stops: List<RouteStop>,
) {
    /** Planned riding seconds from the start to [distanceM]. */
    fun ridingAt(distanceM: Double): Double {
        if (distancesM.isEmpty()) return 0.0
        var hi = java.util.Arrays.binarySearch(distancesM, distanceM)
        if (hi < 0) hi = -hi - 1
        return when {
            hi <= 0 -> ridingS.first()
            hi >= distancesM.size -> ridingS.last()
            else -> {
                val lo = hi - 1
                val span = distancesM[hi] - distancesM[lo]
                val t = if (span <= 0.0) 0.0 else ((distanceM - distancesM[lo]) / span).coerceIn(0.0, 1.0)
                ridingS[lo] + (ridingS[hi] - ridingS[lo]) * t
            }
        }
    }

    val totalRidingS: Double get() = ridingS.lastOrNull() ?: 0.0

    /** Planned riding seconds still to go from [distanceM]. */
    fun ridingLeft(distanceM: Double): Double = max(0.0, totalRidingS - ridingAt(distanceM))

    /** Planned stop seconds still to come from [distanceM] on. */
    fun stopsLeft(distanceM: Double): Double = stops.filter { it.distanceM >= distanceM }.sumOf { it.durationMin * 60.0 }
}

/**
 * The route's elevation every [stepM] metres, to read what is left of the climb and descent and the
 * profile ahead from any point. Without elevation data in the track, [hasElevation] is false.
 */
class ElevationProfile(track: List<RoutePoint>, private val stepM: Double = 20.0) {
    private val totalM = track.lastOrNull()?.distanceFromStartM ?: 0.0
    val hasElevation: Boolean = track.any { it.eleM != null }
    private val values: DoubleArray

    init {
        val n = (totalM / stepM).toInt() + 1
        var last = track.firstNotNullOfOrNull { it.eleM } ?: 0.0
        values = DoubleArray(n + 1) { i ->
            val ele = if (track.size < 2) null else TerrainPacing.elevationAt(track, min(i * stepM, totalM))
            if (ele != null) last = ele
            last
        }
    }

    /** Elevations from [distanceM] to the end: the interpolated current one first, then every step after it. */
    fun ahead(distanceM: Double): List<Double> {
        val d = distanceM.coerceIn(0.0, totalM)
        val position = d / stepM
        val first = position.toInt()
        val here = values[first] + (values[min(first + 1, values.lastIndex)] - values[first]) * (position - first)
        return listOf(here) + (first + 1..values.lastIndex).map { values[it] }
    }

    /** Climb and descent still to come from [distanceM], with the same noise threshold as the route's own total. */
    fun remainingClimb(distanceM: Double): Pair<Double, Double> {
        val ahead = ahead(distanceM)
        return elevationGainM(ahead) to elevationGainM(ahead.map { -it })
    }
}
