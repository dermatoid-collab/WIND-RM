package com.windrm.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

private val PREVIEW_BACKGROUND = Color(0xFFE7ECE4)
private val PREVIEW_LINE = Color(0xFFF4511E)
private const val MAX_PREVIEW_POINTS = 150

/**
 * Small route-shape thumbnail for a list row: an auto-fit outline of the track, like the mini
 * maps in Strava's own activity list. Takes plain (lat, lon) pairs so it works both for local
 * routes (already-parsed GPX points) and for Strava list items, whose summary/starred endpoints
 * only expose an encoded polyline -- see [com.windrm.app.domain.PolylineDecoder].
 */
@Composable
fun RoutePolylinePreview(points: List<Pair<Double, Double>>, modifier: Modifier = Modifier) {
    Box(modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(PREVIEW_BACKGROUND)) {
        if (points.size < 2) return@Box

        val sampled = downsample(points, MAX_PREVIEW_POINTS)
        var minLat = sampled[0].first
        var maxLat = minLat
        var minLon = sampled[0].second
        var maxLon = minLon
        for ((lat, lon) in sampled) {
            if (lat < minLat) minLat = lat
            if (lat > maxLat) maxLat = lat
            if (lon < minLon) minLon = lon
            if (lon > maxLon) maxLon = lon
        }
        val latSpan = (maxLat - minLat).coerceAtLeast(1e-6)
        val lonSpan = (maxLon - minLon).coerceAtLeast(1e-6)

        Canvas(Modifier.size(56.dp).padding(6.dp)) {
            val scale = minOf(size.width / lonSpan.toFloat(), size.height / latSpan.toFloat())
            val drawnWidth = lonSpan.toFloat() * scale
            val drawnHeight = latSpan.toFloat() * scale
            val offsetX = (size.width - drawnWidth) / 2f
            val offsetY = (size.height - drawnHeight) / 2f

            val path = Path()
            sampled.forEachIndexed { index, (lat, lon) ->
                val x = offsetX + ((lon - minLon).toFloat() / lonSpan.toFloat()) * drawnWidth
                // Latitude increases northward (up), but canvas y increases downward -- flip it.
                val y = offsetY + ((maxLat - lat).toFloat() / latSpan.toFloat()) * drawnHeight
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path, color = PREVIEW_LINE, style = Stroke(width = 2.5.dp.toPx()))
        }
    }
}

private fun downsample(points: List<Pair<Double, Double>>, maxPoints: Int): List<Pair<Double, Double>> {
    if (points.size <= maxPoints) return points
    val step = points.size.toDouble() / maxPoints
    return (0 until maxPoints).map { points[(it * step).toInt().coerceAtMost(points.lastIndex)] }
}
