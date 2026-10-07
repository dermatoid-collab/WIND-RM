package com.windrm.app.gpx

import android.util.Xml
import com.windrm.app.model.Route
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.time.Instant

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
        return parseWith(parser, fallbackName)
    }

    /** The parsing itself, on an already set-up pull parser (also what the JVM tests drive). */
    internal fun parseWith(parser: XmlPullParser, fallbackName: String): Route {
        var routeName: String? = null
        val rawPoints = mutableListOf<RawTrackPoint>()
        var currentLat = 0.0
        var currentLon = 0.0
        var currentEle: Double? = null
        var currentTime: Instant? = null
        // A <time> outside any point: the file's own creation time (<metadata><time>).
        var fileTime: Instant? = null
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
                        "time" -> if (inTrkOrRtePoint) {
                            currentTime = parseInstantOrNull(textBuffer.toString().trim())
                        } else if (fileTime == null) {
                            fileTime = parseInstantOrNull(textBuffer.toString().trim())
                        }
                        "trkpt", "rtept" -> {
                            rawPoints += RawTrackPoint(currentLat, currentLon, currentEle, currentTime)
                            inTrkOrRtePoint = false
                        }
                        "name" -> if (depth == nameDepth) routeName = textBuffer.toString().trim().ifEmpty { null }
                    }
                    depth--
                }
            }
            eventType = parser.next()
        }

        return buildTrackRoute(rawPoints, routeName ?: fallbackName, fileTime, "GPX")
    }
}
