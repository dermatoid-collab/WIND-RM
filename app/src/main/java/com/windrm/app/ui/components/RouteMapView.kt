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

/** What was last applied to a MapView, stashed in its tag so update() can skip redundant work. */
private data class MapViewFitState(val style: MapStyle, val points: List<RoutePoint>)

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
    highlightPoint: RoutePoint? = null,
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
            // update() re-runs on every recomposition (e.g. each time the scrub cursor moves),
            // but calling setTileSource()/zoomToBoundingBox() unconditionally made the map
            // visibly flicker/reset every single time, even though neither the style nor the
            // route had actually changed. A tag on the view remembers what was last applied so
            // each is only redone when it genuinely changes.
            val lastState = mapView.tag as? MapViewFitState
            if (lastState?.style != mapStyle) {
                val tileSource = tileSourceFor(mapStyle)
                mapView.setTileSource(tileSource)
                // Each provider renders tiles up to a different zoom (OpenTopoMap stops at z17,
                // others go further); capping the view to a fixed level regardless of source
                // would either waste zoom range or request levels the source doesn't have tiles for.
                mapView.minZoomLevel = tileSource.minimumZoomLevel.toDouble()
                mapView.maxZoomLevel = tileSource.maximumZoomLevel.toDouble()
            }
            mapView.overlays.clear()
            // OSM's tile usage policy requires visible attribution; re-added every update()
            // since overlays.clear() above would otherwise drop it too.
            mapView.overlays.add(CopyrightOverlay(mapView.context))
            if (points.isNotEmpty()) {
                val geoPoints = points.map { GeoPoint(it.lat, it.lon) }
                val polyline = Polyline(mapView).apply {
                    setPoints(geoPoints)
                    outlinePaint.color = Color.parseColor("#E53935")
                    outlinePaint.strokeWidth = 9f
                }
                mapView.overlays.add(polyline)

                if (!windArrows.isNullOrEmpty()) {
                    // One arrow per weather-sample point was too dense to read; halve it.
                    val thinnedArrows = windArrows.filterIndexed { index, _ -> index % 2 == 0 }
                    mapView.overlays.add(WindArrowsOverlay(thinnedArrows))
                }

                highlightPoint?.let { mapView.overlays.add(HighlightOverlay(it)) }

                if (lastState?.points != points) {
                    val bbox = boundingBoxOf(geoPoints)
                    mapView.post { mapView.zoomToBoundingBox(bbox, false, 80) }
                }
            }
            mapView.tag = MapViewFitState(mapStyle, points)
            mapView.invalidate()
        },
    )
}

/**
 * Resolves the user's chosen [MapStyle] to an osmdroid tile source. Mapbox, CARTO and
 * Thunderforest each need their own API key (set via local.properties / a GitHub Actions
 * secret, same pattern as Strava's credentials -- see SettingsRepository.MapStyle.requiresApiKey
 * and the README); without one, requests to those services simply won't return tiles, so callers
 * should only offer them once BuildConfig actually carries a non-blank key (SettingsScreen
 * already gates the picker on this).
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
    // Same provider Strava's own app uses (confirmed via its "Map Data Sources" panel: Mapbox,
    // Maxar, Intermap -- the usual Mapbox "Outdoors" style stack). The classic v4 raster tile API
    // returned nothing for newer accounts (it's been retired); this is Mapbox's current Static
    // Tiles API, which renders a v1 style to plain raster tiles any z/x/y client can consume.
    MapStyle.MAPBOX_OUTDOORS -> xyzTileSource(
        "MapboxOutdoors", 20, "https://api.mapbox.com/styles/v1/mapbox/outdoors-v12/tiles/256/", "?access_token=${BuildConfig.MAPBOX_ACCESS_TOKEN}",
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

/** A marker at the route point currently under a finger on any chart below the map. */
private class HighlightOverlay(private val point: RoutePoint) : Overlay() {
    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val out = android.graphics.Point()
        mapView.projection.toPixels(GeoPoint(point.lat, point.lon), out)
        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1E88E5"); style = Paint.Style.FILL }
        canvas.drawCircle(out.x.toFloat(), out.y.toFloat(), 14f, dotPaint)
        canvas.drawCircle(out.x.toFloat(), out.y.toFloat(), 14f, ringPaint)
    }
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
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }
    // The tip always sits this many pixels from the route point -- calm or strong, it never
    // moves -- while the tail is what extends further away as wind speed increases, so the
    // shaft (not the tip's distance from the route) is what grows with intensity. Sized up from
    // the first pass, which turned out too small/thin to read on a real device, with the shaft
    // range narrowed to a realistic riding wind-speed span so the length difference is visible.
    private val tipOffsetPx = 20f
    private val minShaftPx = 12f
    private val maxShaftPx = 70f
    private val maxSpeedForScaleKmh = 40.0
    private val lineStrokePx = 6f
    private val haloStrokePx = 10f

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
        val headLengthPx = 16f
        val headHalfWidthPx = 11f

        // bearing 0 = pointing up (north); rotate clockwise for increasing degrees. The tip
        // sits at a fixed distance from the route point; only the tail moves further out.
        val fx = sin(bearingRad)
        val fy = -cos(bearingRad)
        val tipX = cx + fx * tipOffsetPx
        val tipY = cy + fy * tipOffsetPx
        val tailX = cx + fx * lengthPx
        val tailY = cy + fy * lengthPx

        // Classic arrowhead: apex at the tip, base a fixed distance behind it along the shaft,
        // perpendicular corners symmetric around that base -- an unambiguous "normal" arrow
        // shape, unlike the angle-from-tip construction tried before, which read as inverted.
        val px = -fy
        val py = fx
        val baseX = tipX - fx * headLengthPx
        val baseY = tipY - fy * headLengthPx
        val headPath = Path().apply {
            moveTo(tipX, tipY)
            lineTo(baseX + px * headHalfWidthPx, baseY + py * headHalfWidthPx)
            lineTo(baseX - px * headHalfWidthPx, baseY - py * headHalfWidthPx)
            close()
        }

        // Halo pass first (shaft + head outline), then the solid black shape on top.
        canvas.drawLine(tailX, tailY, tipX, tipY, haloPaint.apply { strokeWidth = haloStrokePx })
        canvas.drawPath(headPath, haloPaint.apply { strokeWidth = haloStrokePx * 0.6f })
        canvas.drawLine(tailX, tailY, tipX, tipY, linePaint.apply { strokeWidth = lineStrokePx })
        canvas.drawPath(headPath, fillPaint)
    }
}
