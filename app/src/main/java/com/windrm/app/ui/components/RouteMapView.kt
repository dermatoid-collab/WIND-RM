package com.windrm.app.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.windrm.app.BuildConfig
import com.windrm.app.R
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
    val mapRef = remember { MapViewRef() }
    // Without clipToBounds(), osmdroid's MapView can render past its Compose-assigned bounds
    // while the surrounding Column is scrolling, bleeding over the next section's title.
    Box(modifier.fillMaxWidth().height(280.dp).clipToBounds()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                createMapView(context).also { mapRef.view = it }
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
                        outlinePaint.strokeWidth = 3f * mapView.resources.displayMetrics.density
                        // osmdroid draws each segment separately; with the default BUTT caps every
                        // tiny GPX direction change leaves a notch, which read as a "fuzzy" line.
                        outlinePaint.isAntiAlias = true
                        outlinePaint.strokeCap = Paint.Cap.ROUND
                        outlinePaint.strokeJoin = Paint.Join.ROUND
                    }
                    mapView.overlays.add(polyline)

                    // Start drawn first so the finish flag ends up on top when they coincide (a
                    // loop route), per the "finish must always show in front of start" requirement.
                    mapView.overlays.add(StartFinishOverlay(geoPoints.first(), geoPoints.last(), mapView.resources.displayMetrics.density))

                    if (!windArrows.isNullOrEmpty()) {
                        // One arrow per weather-sample point was too dense to read; halve it.
                        val thinnedArrows = windArrows.filterIndexed { index, _ -> index % 2 == 0 }
                        mapView.overlays.add(WindArrowsOverlay(thinnedArrows, mapView.resources.displayMetrics.density))
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
        if (points.isNotEmpty()) {
            Surface(
                onClick = {
                    mapRef.view?.zoomToBoundingBox(boundingBoxOf(points.map { GeoPoint(it.lat, it.lon) }), true, 80)
                },
                shape = CircleShape,
                color = androidx.compose.ui.graphics.Color.White,
                shadowElevation = 3.dp,
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
            ) {
                Icon(
                    Icons.Filled.ZoomOutMap,
                    contentDescription = stringResource(R.string.fit_route),
                    tint = androidx.compose.ui.graphics.Color.DarkGray,
                    modifier = Modifier.padding(8.dp).size(20.dp),
                )
            }
        }
    }
}

private class MapViewRef {
    var view: MapView? = null
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

/** Green dot at the route's start, checkered flag at its end (end always drawn on top when they coincide, e.g. a loop). */
private class StartFinishOverlay(
    private val start: GeoPoint,
    private val finish: GeoPoint,
    private val density: Float,
) : Overlay() {
    private val startRadius = 7f * density
    private val flagSize = 12f * density
    private val startFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#43A047"); style = Paint.Style.FILL }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 2f * density }
    private val blackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.FILL }
    private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val out = android.graphics.Point()

        mapView.projection.toPixels(start, out)
        canvas.drawCircle(out.x.toFloat(), out.y.toFloat(), startRadius, startFillPaint)
        canvas.drawCircle(out.x.toFloat(), out.y.toFloat(), startRadius, ringPaint)

        mapView.projection.toPixels(finish, out)
        drawCheckeredFlag(canvas, out.x.toFloat(), out.y.toFloat())
    }

    private fun drawCheckeredFlag(canvas: Canvas, cx: Float, cy: Float) {
        val half = flagSize / 2
        canvas.drawCircle(cx, cy, half + 2f * density, whitePaint)
        val cells = 4
        val cellSize = flagSize / cells
        for (row in 0 until cells) {
            for (col in 0 until cells) {
                if ((row + col) % 2 == 0) {
                    val left = cx - half + col * cellSize
                    val top = cy - half + row * cellSize
                    canvas.drawRect(left, top, left + cellSize, top + cellSize, blackPaint)
                }
            }
        }
        canvas.drawRect(cx - half, cy - half, cx + half, cy + half, ringPaint)
    }
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

/**
 * Wind arrows built from ONE fixed template -- a solid triangular head with its tip at the origin
 * pointing straight up, shaft hanging below from the centre of its base -- only translated onto the route point and
 * rotated with the canvas. The tip sits exactly on the track; the arrow lies upwind of it.
 *
 * Shaft length is relative to this route's own wind range (all sizes in dp):
 * shaft = 4 + 18 * (v - vMin) / (vMax - vMin), so the calmest point gets the shortest arrow and the
 * windiest the longest, whatever the absolute speeds. Colour is absolute instead, one shade per
 * 10 km/h band (see [windBandColor]), so it stays comparable between rides.
 */
private class WindArrowsOverlay(private val arrows: List<WindArrowPoint>, density: Float) : Overlay() {
    private val headLength = 8f * density
    private val headHalfWidth = 5f * density
    private val minShaft = 4f * density
    private val maxShaft = 22f * density
    private val outline = 1f * density
    private val minSpeed = arrows.minOfOrNull { it.windSpeedKmh } ?: 0.0
    private val maxSpeed = arrows.maxOfOrNull { it.windSpeedKmh } ?: 0.0

    // Thin white edge so the arrow stays legible over busy terrain without the heavy halo of
    // earlier versions, which competed with the map.
    private val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
        strokeJoin = Paint.Join.ROUND
    }
    private val shaftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.BUTT
        strokeWidth = 2.5f * density
    }
    private val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        style = Paint.Style.FILL
    }
    private val headPath = Path().apply {
        moveTo(0f, 0f)
        lineTo(headHalfWidth, headLength)
        lineTo(-headHalfWidth, headLength)
        close()
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val out = android.graphics.Point()
        for (arrow in arrows) {
            mapView.projection.toPixels(GeoPoint(arrow.point.lat, arrow.point.lon), out)
            // Template points up (north); wind blows TOWARDS from + 180, and canvas.rotate() is
            // clockwise on screen -- the same sense as compass bearings.
            val bearingDeg = ((arrow.windFromDeg + 180.0) % 360.0).toFloat()
            canvas.save()
            canvas.translate(out.x.toFloat(), out.y.toFloat())
            canvas.rotate(bearingDeg)
            drawTemplate(canvas, shaftFor(arrow.windSpeedKmh), windBandColor(arrow.windSpeedKmh))
            canvas.restore()
        }
    }

    private fun shaftFor(windSpeedKmh: Double): Float {
        val range = maxSpeed - minSpeed
        val t = if (range <= 0.0) 1f else ((windSpeedKmh - minSpeed) / range).coerceIn(0.0, 1.0).toFloat()
        return minShaft + (maxShaft - minShaft) * t
    }

    private fun drawTemplate(canvas: Canvas, shaft: Float, color: Int) {
        shaftPaint.color = color
        headPaint.color = color
        val shaftEnd = headLength + shaft
        canvas.drawLine(0f, headLength, 0f, shaftEnd + outline, outlinePaint.apply { strokeWidth = shaftPaint.strokeWidth + 2 * outline })
        canvas.drawPath(headPath, outlinePaint.apply { strokeWidth = 2 * outline })
        // Starts half a pixel inside the head so no gap shows between shaft and head.
        canvas.drawLine(0f, headLength - 0.5f, 0f, shaftEnd, shaftPaint)
        canvas.drawPath(headPath, headPaint)
    }
}

/** Darker shades of green / blue / orange / red so they read over terrain greens and the red track. */
private fun windBandColor(windSpeedKmh: Double): Int = when {
    windSpeedKmh < 10.0 -> Color.parseColor("#1B5E20")
    windSpeedKmh < 20.0 -> Color.parseColor("#0277BD")
    windSpeedKmh < 30.0 -> Color.parseColor("#E65100")
    windSpeedKmh < 40.0 -> Color.parseColor("#8E0000")
    else -> Color.BLACK
}
