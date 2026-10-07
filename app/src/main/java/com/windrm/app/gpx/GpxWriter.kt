package com.windrm.app.gpx

import com.windrm.app.domain.ActivityType
import com.windrm.app.model.Route
import java.time.Instant
import java.util.Locale

/** Writes a [Route] as a GPX 1.1 track (one segment), readable by [GpxParser], Strava, Garmin and the rest. */
object GpxWriter {

    fun write(route: Route, now: Instant = Instant.now()): String = buildString {
        appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
        appendLine(
            """<gpx version="1.1" creator="WIND-RM" xmlns="http://www.topografix.com/GPX/1/1" """ +
                """xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" """ +
                """xsi:schemaLocation="http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd">""",
        )
        appendLine("  <metadata>")
        appendLine("    <name>${escape(route.name)}</name>")
        appendLine("    <time>$now</time>")
        appendLine("  </metadata>")
        appendLine("  <trk>")
        appendLine("    <name>${escape(route.name)}</name>")
        appendLine("    <type>${if (route.activity == ActivityType.TREK) "hiking" else "cycling"}</type>")
        appendLine("    <trkseg>")
        for (p in route.points) {
            // Locale.US: a decimal comma would make the file unreadable.
            val position = "lat=\"%.6f\" lon=\"%.6f\"".format(Locale.US, p.lat, p.lon)
            val ele = p.eleM
            if (ele == null) {
                appendLine("      <trkpt $position/>")
            } else {
                appendLine("      <trkpt $position><ele>${"%.1f".format(Locale.US, ele)}</ele></trkpt>")
            }
        }
        appendLine("    </trkseg>")
        appendLine("  </trk>")
        appendLine("</gpx>")
    }

    /** A file name that is safe on every file system and keeps the route's name readable. */
    fun fileName(routeName: String): String {
        val cleaned = routeName.trim().replace(Regex("[^\\p{L}\\p{N} _.-]"), "").replace(Regex("\\s+"), "-").trim('-', '.')
        return (cleaned.ifEmpty { "route" }).take(60) + ".gpx"
    }

    private fun escape(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
