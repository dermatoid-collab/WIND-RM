package com.windrm.app.model

import kotlinx.serialization.Serializable

/**
 * What the route builder needs, besides the saved track itself, to open a route again for editing: where its
 * waypoints are on the track ([junctions], indices into [Route.points], one more than [legs]) and how each
 * stretch between two of them was made. The points of the stretches are the track's own, cut at the junctions.
 */
@Serializable
data class BuilderState(
    /** The "paved roads only" switch was off when the route was drawn. */
    val allowUnpaved: Boolean,
    val junctions: List<Int>,
    val legs: List<BuilderLeg>,
) {
    /** The state fits a track of [pointCount] points: ascending junctions inside it, one more than the legs. */
    fun isValidFor(pointCount: Int): Boolean =
        legs.isNotEmpty() && junctions.size == legs.size + 1 &&
            junctions.first() == 0 && junctions.last() == pointCount - 1 &&
            junctions.zipWithNext().all { (a, b) -> a <= b } && junctions.all { it in 0 until pointCount }
}

/** One stretch between two waypoints: its surface mix and whether it was drawn by hand (a straight line off the roads). */
@Serializable
data class BuilderLeg(
    val manual: Boolean,
    val pavedM: Double,
    val unpavedM: Double,
    val trailM: Double,
    val surfaceKnown: Boolean,
    val roughFromM: List<Double> = emptyList(),
    val roughToM: List<Double> = emptyList(),
)
