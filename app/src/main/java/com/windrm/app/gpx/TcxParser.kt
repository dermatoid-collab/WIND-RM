package com.windrm.app.gpx

import android.util.Xml
import com.windrm.app.model.Route
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.time.Instant

/**
 * Reads Garmin Training Center (TCX) files, as exported by Garmin Connect, Strava, Polar or Wahoo:
 * every `<Trackpoint>` with a position (laps and tracks in file order), with its altitude and time.
 * A course's own name is used when the file has one.
 */
object TcxParser {

    fun parse(input: InputStream, fallbackName: String): Route {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(input, null)
        return parseWith(parser, fallbackName)
    }

    /** The parsing itself, on an already set-up pull parser (also what the JVM tests drive). */
    internal fun parseWith(parser: XmlPullParser, fallbackName: String): Route {
        val rawPoints = mutableListOf<RawTrackPoint>()
        var routeName: String? = null
        // An activity's <Id> is its start time.
        var fileTime: Instant? = null
        var inCourse = false
        var inPoint = false
        var lat: Double? = null
        var lon: Double? = null
        var ele: Double? = null
        var time: Instant? = null
        var text = StringBuilder()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    text = StringBuilder()
                    when (parser.name) {
                        "Trackpoint" -> {
                            inPoint = true
                            lat = null
                            lon = null
                            ele = null
                            time = null
                        }
                        "Course" -> inCourse = true
                    }
                }
                XmlPullParser.TEXT -> text.append(parser.text)
                XmlPullParser.END_TAG -> {
                    val value = text.toString().trim()
                    when (parser.name) {
                        "LatitudeDegrees" -> if (inPoint) lat = value.toDoubleOrNull()
                        "LongitudeDegrees" -> if (inPoint) lon = value.toDoubleOrNull()
                        "AltitudeMeters" -> if (inPoint) ele = value.toDoubleOrNull()
                        "Time" -> if (inPoint) time = parseInstantOrNull(value)
                        "Id" -> if (!inPoint && fileTime == null) fileTime = parseInstantOrNull(value)
                        // The course's own name comes first; its turn-by-turn points have names too.
                        "Name" -> if (inCourse && routeName == null) routeName = value.ifEmpty { null }
                        "Trackpoint" -> {
                            // Points without a position (indoor, paused, sensor-only) are skipped.
                            val la = lat
                            val lo = lon
                            if (la != null && lo != null) rawPoints += RawTrackPoint(la, lo, ele, time)
                            inPoint = false
                        }
                        "Course" -> inCourse = false
                    }
                }
            }
            eventType = parser.next()
        }

        return buildTrackRoute(rawPoints, routeName ?: fallbackName, fileTime, "TCX")
    }
}
