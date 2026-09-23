package com.windrm.app.gpx

import android.util.Xml
import com.windrm.app.domain.haversineMeters
import com.windrm.app.model.Route
import com.windrm.app.model.RoutePoint
import com.windrm.app.model.RouteSource
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.time.Instant
import java.time.format.DateTimeParseException

class GpxParseException(message: String) : Exception(message)

/**
 * Minimal GPX 1.1 parser: reads track points from `<trk><trkseg><trkpt>`, falling back to
 * `<rte><rtept>` for route-only files. Computes cumulative distance and elevation gain, and
 * reports whether the file carried real per-point timestamps (used for a realistic pace
 * profile instead of a constant average speed).
 */
object GpxParser {

    fun parse(input: InputStream, fallbackName: String): Route {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)

        var routeName: String? = null
        val rawPoints = mutableListOf<RawPoint>()
        var currentLat = 0.0
        var currentLon = 0.0
        var currentEle: Double? = null
        var currentTime: Instant? = null
        var inTrkOrRtePoint = false
        var textBuffer = StringBuilder()
        var nameDepth = -1
        var depth = 0

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    depth++
                    textBuffer = StringBuilder()
                    when (parser.name) {
                        "trkpt", "rtept" -> {
                            inTrkOrRtePoint = true
                            currentLat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull() ?: 0.0
                            currentLon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull() ?: 0.0
                            currentEle = null
                            currentTime = null
                        }
                        "name" -> if (routeName == null) nameDepth = depth
                    }
                }
                XmlPullParser.TEXT -> textBuffer.append(parser.text)
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "ele" -> if (inTrkOrRtePoint) currentEle = textBuffer.toString().trim().toDoubleOrNull()
                        "time" -> if (inTrkOrRtePoint) currentTime = parseTime(textBuffer.toString().trim())
                        "trkpt", "rtept" -> {
                            rawPoints += RawPoint(currentLat, currentLon, currentEle, currentTime)
                            inTrkOrRtePoint = false
                        }
                        "name" -> if (depth == nameDepth) routeName = textBuffer.toString().trim().ifEmpty { null }
                    }
                    depth--
                }
            }
            eventType = parser.next()
        }

        if (rawPoints.size < 2) {
            throw GpxParseException("The GPX file doesn't contain a valid route (at least 2 points are required).")
        }

        val hasTimestamps = rawPoints.all { it.time != null }
        val startTime = if (hasTimestamps) rawPoints.first().time else null

        var cumulativeDistance = 0.0
        var elevationGain = 0.0
        val points = ArrayList<RoutePoint>(rawPoints.size)
        for ((index, raw) in rawPoints.withIndex()) {
            if (index > 0) {
                val prev = rawPoints[index - 1]
                cumulativeDistance += haversineMeters(prev.lat, prev.lon, raw.lat, raw.lon)
                val prevEle = prev.ele
                if (prevEle != null && raw.ele != null) {
                    val delta = raw.ele - prevEle
                    if (delta > 1.0) elevationGain += delta
                }
            }
            val timeOffsetS = if (hasTimestamps && startTime != null && raw.time != null) {
                raw.time.epochSecond - startTime.epochSecond
            } else null
            points += RoutePoint(
                lat = raw.lat,
                lon = raw.lon,
                eleM = raw.ele,
                distanceFromStartM = cumulativeDistance,
                timeOffsetS = timeOffsetS,
            )
        }

        return Route(
            name = routeName ?: fallbackName,
            source = RouteSource.LOCAL,
            createdAtEpochMs = System.currentTimeMillis(),
            points = points,
            distanceKm = cumulativeDistance / 1000.0,
            elevationGainM = elevationGain,
            hasTimestamps = hasTimestamps,
        )
    }

    private fun parseTime(raw: String): Instant? = try {
        if (raw.isEmpty()) null else Instant.parse(raw)
    } catch (e: DateTimeParseException) {
        null
    }

    private data class RawPoint(val lat: Double, val lon: Double, val ele: Double?, val time: Instant?)
}
