package com.windrm.app.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.windrm.app.model.RoutePoint
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import kotlin.math.cos
import kotlin.math.sin

/** A point along the route where a wind-direction arrow should be drawn, in meteorological "from" degrees. */
data class WindArrowPoint(val point: RoutePoint, val windFromDeg: Double)

/**
 * osmdroid map showing the route polyline, and optionally a set of wind-direction arrows
 * (used by the forecast screen's "Wind Direction" section).
 */
@Composable
fun RouteMapView(
    points: List<RoutePoint>,
    modifier: Modifier = Modifier,
    windArrows: List<WindArrowPoint>? = null,
) {
    AndroidView(
        // Without clipToBounds(), osmdroid's MapView can render past its Compose-assigned
        // bounds while the surrounding Column is scrolling, bleeding over the next section's
        // title -- clipToBounds() forces the native view's drawing to stay inside this box.
        modifier = modifier.fillMaxWidth().height(280.dp).clipToBounds(),
        factory = { context ->
            createMapView(context)
        },
        update = { mapView ->
            mapView.overlays.clear()
            // OpenTopoMap's usage policy requires visible attribution; re-added every update()
            // since overlays.clear() above would otherwise drop it too.
            mapView.overlays.add(CopyrightOverlay(mapView.context))
            if (points.isNotEmpty()) {
                val geoPoints = points.map { GeoPoint(it.lat, it.lon) }
                val polyline = Polyline(mapView).apply {
                    setPoints(geoPoints)
                    outlinePaint.color = Color.parseColor("#F4511E")
                    outlinePaint.strokeWidth = 9f
                }
                mapView.overlays.add(polyline)

                if (!windArrows.isNullOrEmpty()) {
                    mapView.overlays.add(WindArrowsOverlay(windArrows))
                }

                val bbox = boundingBoxOf(geoPoints)
                mapView.post { mapView.zoomToBoundingBox(bbox, false, 80) }
            }
            mapView.invalidate()
        },
    )
}

private fun createMapView(context: Context): MapView = MapView(context).apply {
    // OpenTopoMap: contour-line topographic style, closer to the Garmin-style maps the user
    // is used to from other cycling apps, and free/no API key (unlike Garmin's own tiles).
    setTileSource(TileSourceFactory.OpenTopo)
    // Without this, osmdroid renders tiles at their raw 256px size regardless of screen density,
    // so contour lines and labels look small and soft on high-density phones; this scales tiles
    // up to match the device's actual pixel density, making the map read as noticeably sharper.
    isTilesScaledToDpi = true
    setMultiTouchControls(true)
    minZoomLevel = 4.0
    maxZoomLevel = 17.0 // OpenTopoMap doesn't render tiles past z17
    if (context is androidx.lifecycle.LifecycleOwner) {
        context.lifecycle.addObserver(
            object : androidx.lifecycle.DefaultLifecycleObserver {
                override fun onResume(owner: androidx.lifecycle.LifecycleOwner) = onResume()
                override fun onPause(owner: androidx.lifecycle.LifecycleOwner) = onPause()
            },
        )
    }
}

private fun boundingBoxOf(points: List<GeoPoint>): BoundingBox {
    var north = points.first().latitude
    var south = points.first().latitude
    var east = points.first().longitude
    var west = points.first().longitude
    for (p in points) {
        if (p.latitude > north) north = p.latitude
        if (p.latitude < south) south = p.latitude
        if (p.longitude > east) east = p.longitude
        if (p.longitude < west) west = p.longitude
    }
    return BoundingBox(north, east, south, west)
}

/** Draws a rotated arrow at each sample point, pointing in the direction the wind blows towards. */
private class WindArrowsOverlay(private val arrows: List<WindArrowPoint>) : Overlay() {
    // A white halo drawn behind the black arrow keeps it legible over both light and dark
    // terrain (forest greens, contour browns) on the OpenTopoMap tiles.
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val arrowLengthPx = 46f
    private val arrowHeadPx = 18f
    private val lineStrokePx = 7f
    private val haloStrokePx = 12f

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val projection = mapView.projection
        val out = android.graphics.Point()
        for (arrow in arrows) {
            projection.toPixels(GeoPoint(arrow.point.lat, arrow.point.lon), out)
            // Wind blows TOWARDS (from + 180); screen bearing 0deg = up/North in an unrotated map.
            val bearingRad = Math.toRadians((arrow.windFromDeg + 180.0) % 360.0)
            drawArrow(canvas, out.x.toFloat(), out.y.toFloat(), bearingRad.toFloat())
        }
    }

    private fun drawArrow(canvas: Canvas, cx: Float, cy: Float, bearingRad: Float) {
        // bearing 0 = pointing up (north); rotate clockwise for increasing degrees.
        val dx = sin(bearingRad)
        val dy = -cos(bearingRad)
        val tailX = cx - dx * arrowLengthPx / 2
        val tailY = cy - dy * arrowLengthPx / 2
        val tipX = cx + dx * arrowLengthPx / 2
        val tipY = cy + dy * arrowLengthPx / 2

        val leftAngle = bearingRad + Math.toRadians(150.0).toFloat()
        val rightAngle = bearingRad - Math.toRadians(150.0).toFloat()
        val headPath = Path().apply {
            moveTo(tipX, tipY)
            lineTo(tipX + sin(leftAngle) * arrowHeadPx, tipY - cos(leftAngle) * arrowHeadPx)
            lineTo(tipX + sin(rightAngle) * arrowHeadPx, tipY - cos(rightAngle) * arrowHeadPx)
            close()
        }

        // Halo pass first (shaft + head outline), then the solid black shape on top.
        canvas.drawLine(tailX, tailY, tipX, tipY, haloPaint.apply { strokeWidth = haloStrokePx })
        canvas.drawPath(headPath, haloPaint.apply { strokeWidth = haloStrokePx * 0.6f })
        canvas.drawLine(tailX, tailY, tipX, tipY, linePaint.apply { strokeWidth = lineStrokePx })
        canvas.drawPath(headPath, fillPaint)
    }
}
