package com.windrm.app.domain

import com.windrm.app.model.RouteForecastPoint
import com.windrm.app.model.RoutePoint
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** What kind of wind a rider heading along the track feels. */
enum class WindKind { HEAD, CROSS, TAIL }

/**
 * The forecast wind seen from a rider moving along the track: [headKmh] along it (positive = headwind,
 * negative = tailwind), [crossKmh] across it (positive = from the right, negative = from the left) and the
 * wind's own [speedKmh]. [relDeg] is the angle it comes from, clockwise from the direction of travel
 * (0 = straight in the face, 90 = from the right, 180 = from behind, negative = from the left).
 */
data class RelativeWind(val headKmh: Double, val crossKmh: Double, val speedKmh: Double) {
    val relDeg: Double get() = Math.toDegrees(atan2(crossKmh, headKmh))

    val kind: WindKind
        get() = when {
            abs(relDeg) <= HEAD_HALF_ANGLE_DEG -> WindKind.HEAD
            abs(relDeg) >= 180.0 - HEAD_HALF_ANGLE_DEG -> WindKind.TAIL
            else -> WindKind.CROSS
        }

    val fromRight: Boolean get() = crossKmh >= 0.0

    /** Where the arrow of the air's flow points, with the rider's heading drawn upward: 180 = a headwind blowing down at the rider. */
    val flowRotationDeg: Double get() = relDeg + 180.0

    companion object {
        /** Within this angle of the direction of travel the wind counts as head or tail, beyond it as a crosswind. */
        const val HEAD_HALF_ANGLE_DEG = 50.0
    }
}

/**
 * The forecast wind relative to the track, sampled finely along it so a chart can plot the head/tail
 * component and a scrub position can read the exact kind of wind. The forecast points are spread evenly
 * over the track (as the forecast charts do) and the wind is interpolated between them.
 *
 * Every sample is the average over a stretch of track around it (a few hundred metres to ~1.5 km, longer on
 * longer routes), each part of the stretch seen along its own local direction. A single GPS point or a hairpin
 * would otherwise swing the component from head to tail and back within a few metres, which is noise in the chart
 * and not something a rider feels: the average keeps real turns into and out of the wind and drops the jitter.
 */
class RelativeWindProfile(points: List<RouteForecastPoint>, track: List<RoutePoint>, val samples: Int = DEFAULT_SAMPLES) {
    private val winds: List<RelativeWind>

    init {
        val total = track.lastOrNull()?.distanceFromStartM ?: 0.0
        if (points.isEmpty() || track.size < 2 || total <= 0.0) {
            winds = emptyList()
        } else {
            // The velocity the air moves with, as east/north components: they interpolate cleanly across the 0/360 wrap.
            val east = DoubleArray(points.size) { -points[it].weather.windSpeedKmh * sin(Math.toRadians(points[it].weather.windDirectionDeg)) }
            val north = DoubleArray(points.size) { -points[it].weather.windSpeedKmh * cos(Math.toRadians(points[it].weather.windDirectionDeg)) }
            val halfWindowM = (total / 120.0).coerceIn(150.0, 750.0)
            winds = (0 until samples).map { k ->
                var head = 0.0
                var cross = 0.0
                var speed = 0.0
                var weightSum = 0.0
                for (j in -WINDOW_STEPS..WINDOW_STEPS) {
                    val d = (k.toDouble() / (samples - 1) * total + j * halfWindowM / WINDOW_STEPS).coerceIn(0.0, total)
                    val pos = d / total * (points.size - 1)
                    val i = pos.toInt().coerceAtMost((points.size - 2).coerceAtLeast(0))
                    val t = if (points.size < 2) 0.0 else pos - i
                    val next = (i + 1).coerceAtMost(points.lastIndex)
                    val e = east[i] + (east[next] - east[i]) * t
                    val n = north[i] + (north[next] - north[i]) * t
                    val b = Math.toRadians(trackBearingDeg(track, d))
                    // Triangular weights: the sample's own spot counts most.
                    val w = (WINDOW_STEPS + 1 - abs(j)).toDouble()
                    head += w * -(e * sin(b) + n * cos(b))
                    cross += w * (n * sin(b) - e * cos(b))
                    speed += w * kotlin.math.hypot(e, n)
                    weightSum += w
                }
                RelativeWind(head / weightSum, cross / weightSum, speed / weightSum)
            }
        }
    }

    val isEmpty: Boolean get() = winds.isEmpty()

    /** The wind at [fraction] (0..1) of the way along the track. */
    fun at(fraction: Float): RelativeWind? {
        if (winds.isEmpty()) return null
        return winds[(fraction.coerceIn(0f, 1f) * (winds.size - 1)).roundToInt()]
    }

    /** Headwind (positive) / tailwind (negative) in km/h at every sample, ready to plot. */
    val headSeries: List<Float> get() = winds.map { it.headKmh.toFloat() }

    /** Share of the track, 0..1, in each kind of wind. */
    fun share(kind: WindKind): Double = if (winds.isEmpty()) 0.0 else winds.count { it.kind == kind }.toDouble() / winds.size

    companion object {
        const val DEFAULT_SAMPLES = 200
        private const val WINDOW_STEPS = 6
        private const val BEARING_SPAN_M = 150.0

        /** Compass bearing of the track at [distanceM], taken over [BEARING_SPAN_M] either side so GPS jitter doesn't swing it. */
        fun trackBearingDeg(track: List<RoutePoint>, distanceM: Double): Double {
            val total = track.last().distanceFromStartM
            var from = distanceM - BEARING_SPAN_M
            var to = distanceM + BEARING_SPAN_M
            if (total > 2 * BEARING_SPAN_M) {
                // At either end the window is one-sided; keep it as wide as in the middle.
                if (from < 0.0) { from = 0.0; to = 2 * BEARING_SPAN_M }
                if (to > total) { to = total; from = total - 2 * BEARING_SPAN_M }
            } else {
                from = 0.0
                to = total
            }
            val a = pointAt(track, from)
            val b = pointAt(track, to)
            val phi1 = Math.toRadians(a.first)
            val phi2 = Math.toRadians(b.first)
            val dLon = Math.toRadians(b.second - a.second)
            val y = sin(dLon) * cos(phi2)
            val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLon)
            return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
        }

        /** Latitude and longitude [distanceM] from the track's start, interpolated between its neighbouring points. */
        private fun pointAt(track: List<RoutePoint>, distanceM: Double): Pair<Double, Double> {
            var lo = 0
            var hi = track.lastIndex
            while (hi - lo > 1) {
                val mid = (lo + hi) / 2
                if (track[mid].distanceFromStartM <= distanceM) lo = mid else hi = mid
            }
            val a = track[lo]
            val b = track[hi]
            val span = b.distanceFromStartM - a.distanceFromStartM
            val t = if (span <= 0.0) 0.0 else ((distanceM - a.distanceFromStartM) / span).coerceIn(0.0, 1.0)
            return (a.lat + (b.lat - a.lat) * t) to (a.lon + (b.lon - a.lon) * t)
        }

        /** An angle in degrees brought into -180..180. */
        fun wrap180(deg: Double): Double = ((deg % 360.0) + 540.0) % 360.0 - 180.0
    }
}
