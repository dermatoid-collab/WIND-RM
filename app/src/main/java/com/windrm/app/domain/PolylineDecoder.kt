package com.windrm.app.domain

/**
 * Decodes Google's Encoded Polyline Algorithm Format (precision 1e5), the format Strava uses
 * for `map.polyline`/`map.summary_polyline` on activities, routes and segments. Used only for
 * lightweight list-row previews -- it carries no elevation or timestamp data, so real imports
 * use the streams/GPX endpoints instead.
 */
object PolylineDecoder {
    fun decode(encoded: String): List<Pair<Double, Double>> {
        val points = ArrayList<Pair<Double, Double>>()
        var index = 0
        var lat = 0
        var lng = 0

        while (index < encoded.length) {
            var shift = 0
            var result = 0
            var b: Int
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            lat += if (result and 1 != 0) (result shr 1).inv() else result shr 1

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f) shl shift)
                shift += 5
            } while (b >= 0x20)
            lng += if (result and 1 != 0) (result shr 1).inv() else result shr 1

            points += (lat / 1e5) to (lng / 1e5)
        }
        return points
    }
}
