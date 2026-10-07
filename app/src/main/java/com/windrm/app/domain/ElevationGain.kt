package com.windrm.app.domain

/**
 * Total climb of an elevation profile. A rise only counts once it reaches [thresholdM] above the
 * lowest point since the last counted climb, so GPS/barometer jitter is ignored but slow, steady
 * climbs are not: per-point deltas on a 1-second Strava stream are usually well under a metre,
 * which is why summing only deltas above 1 m reported a few metres for a whole mountain ride.
 */
fun elevationGainM(elevations: List<Double?>, thresholdM: Double = 3.0): Double {
    var gain = 0.0
    var reference: Double? = null
    for (ele in elevations) {
        if (ele == null) continue
        val ref = reference
        if (ref == null || ele < ref) {
            reference = ele
        } else if (ele - ref >= thresholdM) {
            gain += ele - ref
            reference = ele
        }
    }
    return gain
}
