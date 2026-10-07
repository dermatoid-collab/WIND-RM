package com.windrm.app.domain

import kotlin.math.cos
import kotlin.math.sin

/**
 * The forecast wind at points along a route: at each of [distancesM] (ascending, metres from the
 * start), its speed in m/s ([speedMs]) and the compass direction it blows FROM ([fromDeg], as weather
 * services report it). Between the points the wind is interpolated.
 */
class WindAlongTrack(
    private val distancesM: DoubleArray,
    speedMs: DoubleArray,
    fromDeg: DoubleArray,
) {
    // The velocity the air moves with, as east/north components: they interpolate cleanly across the 0/360 wrap.
    private val east = DoubleArray(distancesM.size) { -speedMs[it] * sin(Math.toRadians(fromDeg[it])) }
    private val north = DoubleArray(distancesM.size) { -speedMs[it] * cos(Math.toRadians(fromDeg[it])) }

    /** Wind against a rider heading [bearingDeg] at [distanceM]: positive = headwind, negative = tailwind (m/s). */
    fun headwindMs(distanceM: Double, bearingDeg: Double): Double {
        if (distancesM.isEmpty()) return 0.0
        var hi = java.util.Arrays.binarySearch(distancesM, distanceM)
        if (hi < 0) hi = -hi - 1
        val e: Double
        val n: Double
        when {
            hi <= 0 -> { e = east.first(); n = north.first() }
            hi >= distancesM.size -> { e = east.last(); n = north.last() }
            else -> {
                val lo = hi - 1
                val span = distancesM[hi] - distancesM[lo]
                val t = if (span <= 0.0) 0.0 else ((distanceM - distancesM[lo]) / span).coerceIn(0.0, 1.0)
                e = east[lo] + (east[hi] - east[lo]) * t
                n = north[lo] + (north[hi] - north[lo]) * t
            }
        }
        val b = Math.toRadians(bearingDeg)
        return -(e * sin(b) + n * cos(b))
    }
}
