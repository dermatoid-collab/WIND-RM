package com.windrm.app.domain

import com.windrm.app.model.RoutePoint
import kotlin.math.cos
import kotlin.math.hypot

/**
 * Indices into [points] of at most [maxCount] points that keep the shape of the track: the start, the end, and the
 * points that stick out most from the straight line between their neighbours (Douglas-Peucker, ranked by how far
 * each one sticks out, so that the first [maxCount] are the best ones). Ascending; always includes both ends.
 */
fun waypointIndices(points: List<RoutePoint>, maxCount: Int): List<Int> {
    val n = points.size
    if (n <= 2 || maxCount <= 2) return if (n <= 1) listOf(0) else listOf(0, n - 1)
    if (n <= maxCount) return points.indices.toList()

    // On a very long track the search runs over a thinned copy of it, which is plenty to pick corners from.
    val step = ((n - 1) / MAX_CANDIDATES) + 1
    val candidates = (0 until n step step).toMutableList()
    if (candidates.last() != n - 1) candidates += n - 1

    val lat0 = Math.toRadians(points[candidates.first()].lat)
    val x = DoubleArray(candidates.size) { points[candidates[it]].lon * METRES_PER_DEG * cos(lat0) }
    val y = DoubleArray(candidates.size) { points[candidates[it]].lat * METRES_PER_DEG }

    // How far each candidate sticks out when it is picked as the farthest of its range.
    val prominence = DoubleArray(candidates.size)
    val stack = ArrayDeque<IntArray>()
    stack.addLast(intArrayOf(0, candidates.size - 1))
    while (stack.isNotEmpty()) {
        val (lo, hi) = stack.removeLast()
        if (hi - lo < 2) continue
        var best = -1
        var bestDist = -1.0
        for (i in lo + 1 until hi) {
            val d = distanceToSegment(x[i], y[i], x[lo], y[lo], x[hi], y[hi])
            if (d > bestDist) { bestDist = d; best = i }
        }
        prominence[best] = bestDist
        stack.addLast(intArrayOf(lo, best))
        stack.addLast(intArrayOf(best, hi))
    }
    // Points on a straight stretch (no more than a metre off it) would only add waypoints that shape nothing.
    val chosen = (1 until candidates.size - 1).filter { prominence[it] > MIN_PROMINENCE_M }.sortedByDescending { prominence[it] }.take(maxCount - 2)
    return (listOf(0, candidates.size - 1) + chosen).sorted().map { candidates[it] }
}

private fun distanceToSegment(px: Double, py: Double, ax: Double, ay: Double, bx: Double, by: Double): Double {
    val dx = bx - ax
    val dy = by - ay
    val len2 = dx * dx + dy * dy
    if (len2 <= 0.0) return hypot(px - ax, py - ay)
    val t = (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0.0, 1.0)
    return hypot(px - (ax + t * dx), py - (ay + t * dy))
}

private const val METRES_PER_DEG = 111_320.0
private const val MAX_CANDIDATES = 4000
private const val MIN_PROMINENCE_M = 1.0
