package com.windrm.app.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.windrm.app.BuildConfig
import com.windrm.app.model.RoutePoint
import com.windrm.app.settings.MapStyle
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import kotlin.math.cos
import kotlin.math.sin

/** A point along the route where a wind-direction arrow should be drawn, in meteorological "from" degrees. */
data class WindArrowPoint(val point: RoutePoint, val windFromDeg: Double, val windSpeedKmh: Double)

/**
 * osmdroid map showing the route polyline, and optionally a set of wind-direction arrows
 * (used by the forecast screen's "Wind Direction" section).
 */
@Composable
fun RouteMapView(
    points: List<RoutePoint>,
    modifier: Modifier = Modifier,
    windArrows: List<WindArrowPoint>? = null,
    mapStyle: MapStyle = MapStyle.OSM_STANDARD,
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
            val tileSource = tileSourceFor(mapStyle)
            mapView.setTileSource(tileSource)
            // Each provider renders tiles up to a different zoom (OpenTopoMap stops at z17,
            // others go further); capping the view to a fixed level regardless of source would
            // either waste zoom range or request levels the source doesn't have tiles for.
            mapView.minZoomLevel = tileSource.minimumZoomLevel.toDouble()
            mapView.maxZoomLevel = tileSource.maximumZoomLevel.toDouble()
            mapView.overlays.clear()
            // OSM's tile usage policy requires visible attribution; re-added every update()
            // since overlays.clear() above would otherwise drop it too.
            mapView.overlays.add(CopyrightOverlay(mapView.context))
            if (points.isNotEmpty()) {
                val geoPoints = points.map { GeoPoint(it.lat, it.lon) }
                val polyline = Polyline(mapView).apply {
                    setPoints(geoPoints)
                    outlinePaint.color = Color.parseColor("#B71C1C")
                    outlinePaint.strokeWidth = 9f
                }
                mapView.overlays.add(polyline)

                if (!windArrows.isNullOrEmpty()) {
                    // One arrow per weather-sample point was too dense to read; halve it.
                    val thinnedArrows = windArrows.filterIndexed { index, _ -> index % 2 == 0 }
                    mapView.overlays.add(WindArrowsOverlay(thinnedArrows))
                }

                val bbox = boundingBoxOf(geoPoints)
                mapView.post { mapView.zoomToBoundingBox(bbox, false, 80) }
            }
            mapView.invalidate()
        },
    )
}

/**
 * Resolves the user's chosen [MapStyle] to an osmdroid tile source. CARTO and Thunderforest
 * need an API key (set via local.properties / a GitHub Actions secret, same pattern as Strava's
 * credentials -- see SettingsRepository.MapStyle.requiresApiKey and the README); without one,
 * requests to those services simply won't return tiles, so callers should only offer them once
 * BuildConfig actually carries a non-blank key (SettingsScreen already gates the picker on this).
 */
private fun tileSourceFor(style: MapStyle): ITileSource = when (style) {
    MapStyle.OSM_STANDARD -> TileSourceFactory.MAPNIK
    MapStyle.OPEN_TOPO -> TileSourceFactory.OpenTopo
    MapStyle.CARTO_POSITRON -> xyzTileSource(
        "CartoPositron", 20, "https://basemaps.cartocdn.com/light_all/", ".png?api_key=${BuildConfig.CARTO_API_KEY}",
    )
    MapStyle.THUNDERFOREST_OUTDOORS -> xyzTileSource(
        "ThunderforestOutdoors", 22, "https://tile.thunderforest.com/outdoors/", ".png?apikey=${BuildConfig.THUNDERFOREST_API_KEY}",
    )
}

/** A simple z/x/y raster tile source, built directly on osmdroid's base class for reliability
 * across osmdroid versions (unlike the XYZTileSource convenience class, which isn't available
 * in every release). */
private fun xyzTileSource(name: String, maxZoom: Int, baseUrl: String, urlSuffix: String): OnlineTileSourceBase =
    object : OnlineTileSourceBase(name, 0, maxZoom, 256, urlSuffix, arrayOf(baseUrl)) {
        override fun getTileURLString(pMapTileIndex: Long): String =
            baseUrl + MapTileIndex.getZoom(pMapTileIndex) + "/" + MapTileIndex.getX(pMapTileIndex) + "/" + MapTileIndex.getY(pMapTileIndex) + urlSuffix
    }

private fun createMapView(context: Context): MapView = MapView(context).apply {
    setMultiTouchControls(true)
    // The enclosing screen is a scrollable Compose Column, which otherwise steals a one-finger
    // drag as a page scroll before osmdroid's own touch handling ever sees it. Telling the
    // parent not to intercept while a finger is down on the map lets a single-finger drag pan
    // the map itself, matching what setMultiTouchControls(true) already does for pinch-zoom.
    setOnTouchListener { view, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> view.parent?.requestDisallowInterceptTouchEvent(true)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> view.parent?.requestDisallowInterceptTouchEvent(false)
        }
        false
    }
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
    // map features (forest greens, water blues, road whites).
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    // The tip always sits this many pixels from the route point -- calm or strong, it never
    // moves -- while the tail is what extends further away as wind speed increases, so the
    // shaft (not the tip's distance from the route) is what grows with intensity.
    private val tipOffsetPx = 14f
    private val minShaftPx = 6f
    private val maxShaftPx = 50f
    private val maxSpeedForScaleKmh = 50.0

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val projection = mapView.projection
        val out = android.graphics.Point()
        for (arrow in arrows) {
            projection.toPixels(GeoPoint(arrow.point.lat, arrow.point.lon), out)
            // Wind blows TOWARDS (from + 180); screen bearing 0deg = up/North in an unrotated map.
            val bearingRad = Math.toRadians((arrow.windFromDeg + 180.0) % 360.0)
            val shaftPx = shaftForSpeed(arrow.windSpeedKmh)
            drawArrow(canvas, out.x.toFloat(), out.y.toFloat(), bearingRad.toFloat(), shaftPx)
        }
    }

    private fun shaftForSpeed(windSpeedKmh: Double): Float {
        val t = (windSpeedKmh / maxSpeedForScaleKmh).coerceIn(0.0, 1.0)
        return (minShaftPx + (maxShaftPx - minShaftPx) * t).toFloat()
    }

    private fun drawArrow(canvas: Canvas, cx: Float, cy: Float, bearingRad: Float, shaftPx: Float) {
        val lengthPx = tipOffsetPx + shaftPx
        val headPx = (tipOffsetPx + shaftPx * 0.35f).coerceAtMost(20f)
        val lineStrokePx = (lengthPx * 0.14f).coerceAtLeast(4f)
        val haloStrokePx = lineStrokePx + 5f

        // bearing 0 = pointing up (north); rotate clockwise for increasing degrees. The tip
        // sits at a fixed distance from the route point; only the tail moves further out.
        val dx = sin(bearingRad)
        val dy = -cos(bearingRad)
        val tipX = cx + dx * tipOffsetPx
        val tipY = cy + dy * tipOffsetPx
        val tailX = cx + dx * lengthPx
        val tailY = cy + dy * lengthPx

        // An open chevron (two strokes meeting at the tip, no fill or closing edge) rather
        // than a solid filled triangle, matching Epic Ride Weather's minimal arrowhead.
        val leftAngle = bearingRad + Math.toRadians(150.0).toFloat()
        val rightAngle = bearingRad - Math.toRadians(150.0).toFloat()
        val leftX = tipX + sin(leftAngle) * headPx
        val leftY = tipY - cos(leftAngle) * headPx
        val rightX = tipX + sin(rightAngle) * headPx
        val rightY = tipY - cos(rightAngle) * headPx
        val headPath = Path().apply {
            moveTo(leftX, leftY)
            lineTo(tipX, tipY)
            lineTo(rightX, rightY)
        }

        // Halo pass first (shaft + chevron outline), then the solid black stroke on top.
        canvas.drawLine(tailX, tailY, tipX, tipY, haloPaint.apply { strokeWidth = haloStrokePx })
        canvas.drawPath(headPath, haloPaint.apply { strokeWidth = haloStrokePx * 0.7f })
        canvas.drawLine(tailX, tailY, tipX, tipY, linePaint.apply { strokeWidth = lineStrokePx })
        canvas.drawPath(headPath, linePaint.apply { strokeWidth = lineStrokePx })
    }
}
