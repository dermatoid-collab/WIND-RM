package com.windrm.app.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.windrm.app.BuildConfig
import com.windrm.app.R
import com.windrm.app.domain.cropPoints
import com.windrm.app.model.RoutePoint
import com.windrm.app.model.RouteStop
import com.windrm.app.settings.MapKeyProvider
import com.windrm.app.settings.MapStyle
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay

/** A point along the route where a wind-direction arrow should be drawn, in meteorological "from" degrees. */
data class WindArrowPoint(val point: RoutePoint, val windFromDeg: Double, val windSpeedKmh: Double)

/** An extra dot on the route, e.g. where a gauge's min or max occurs. */
data class MapMarker(val point: RoutePoint, val argb: Int)

/** A label on the route at [point], like a distance marker: a dark pill with a small tail pointing at the line. */
data class KmMarker(val point: RoutePoint, val label: String)

/** A small direction arrow on the route at [point], pointing along [bearingDeg] (compass degrees, 0 = north). */
data class MapArrow(val point: RoutePoint, val bearingDeg: Double)

/** What was last applied to a MapView, stashed in its tag so update() can skip redundant work. */
private data class MapViewFitState(val style: MapStyle, val points: List<RoutePoint>, val keys: String)

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
    /** Scrub position (0..1 of the route); the arrow nearest to it gets a thicker white edge. */
    scrubFraction: Float? = null,
    markers: List<MapMarker> = emptyList(),
    /** Only this part of the track (metres from the start) is drawn; the map stays fitted to the whole route. */
    cropRangeM: ClosedFloatingPointRange<Double>? = null,
    /** Planned stops, drawn as clock pins. */
    stops: List<RouteStop> = emptyList(),
    /** Tap on a stop pin (its index in [stops]); null = pins aren't tappable. */
    onStopTap: ((Int) -> Unit)? = null,
    /** Tap anywhere else on the map; null = taps are ignored (normal panning still works). */
    onMapTap: ((lat: Double, lon: Double) -> Unit)? = null,
    /** Dp.Unspecified = fill the height the parent gives it (e.g. a weighted Column slot). */
    height: Dp = 280.dp,
    /** False keeps the view where the user left it when [points] change (the route builder grows the track under their finger). */
    autoFit: Boolean = true,
    /** (latitude, longitude) the map opens on while there is no route to fit; null = osmdroid's default. */
    initialCenter: Pair<Double, Double>? = null,
    initialZoom: Double = 14.0,
    /** Points the user can pick up and move with a finger (the route builder's waypoints); needs [onPointDragged]. */
    draggablePoints: List<RoutePoint> = emptyList(),
    /** A dragged point was dropped: its index in [draggablePoints] and the new place. */
    onPointDragged: ((index: Int, lat: Double, lon: Double) -> Unit)? = null,
    /** Draws the route line like Strava's: red with a thin darker red edge. */
    casedLine: Boolean = false,
    /** Stretches of [points] drawn as a dashed red line on white (unpaved ways), edged like [casedLine]. */
    roughRuns: List<List<RoutePoint>> = emptyList(),
    /** Stretches of [points] drawn by hand, straight and off any road: a dotted red line on white, edged like [casedLine]. */
    manualRuns: List<List<RoutePoint>> = emptyList(),
    kmMarkers: List<KmMarker> = emptyList(),
    arrows: List<MapArrow> = emptyList(),
    /** A tap on the line itself: the index of the line segment of [points] (point i to i + 1) and the place tapped, snapped onto it. */
    onTrackTap: ((segmentIndex: Int, lat: Double, lon: Double) -> Unit)? = null,
) {
    val mapRef = remember { MapViewRef() }
    val drag = remember { DragState() }
    val labels = remember { LabelsLayer() }
    // Read here so that typing a key in Settings makes this map fetch its tiles again.
    val keys = MapApiKeys.fingerprint()
    DisposableEffect(Unit) { onDispose { labels.release() } }
    // Without clipToBounds(), osmdroid's MapView can render past its Compose-assigned bounds
    // while the surrounding Column is scrolling, bleeding over the next section's title.
    val sized = if (height == Dp.Unspecified) modifier.fillMaxWidth().fillMaxHeight() else modifier.fillMaxWidth().height(height)
    Box(sized.clipToBounds()) {
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
                if (lastState == null && points.isEmpty() && initialCenter != null) {
                    mapView.controller.setZoom(initialZoom)
                    mapView.controller.setCenter(GeoPoint(initialCenter.first, initialCenter.second))
                }
                if (lastState?.style != mapStyle || lastState?.keys != keys) {
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
                // Bottom of the stack, so the stop pins above it get first pick of a tap.
                onMapTap?.let { callback ->
                    mapView.overlays.add(
                        MapEventsOverlay(object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                                callback(p.latitude, p.longitude)
                                return true
                            }

                            override fun longPressHelper(p: GeoPoint): Boolean = false
                        }),
                    )
                }
                if (points.isNotEmpty()) {
                    val geoPoints = points.map { GeoPoint(it.lat, it.lon) }
                    val shownPoints = cropRangeM?.let { cropPoints(points, it.start, it.endInclusive) } ?: points
                    val shownGeoPoints = shownPoints.map { GeoPoint(it.lat, it.lon) }
                    val density = mapView.resources.displayMetrics.density
                    if (casedLine) mapView.overlays.add(routeLine(mapView, shownGeoPoints, CASING_ARGB, 5.6f * density))
                    mapView.overlays.add(routeLine(mapView, shownGeoPoints, ROUTE_ARGB, (if (casedLine) 3.6f else 3f) * density))
                    if (casedLine) {
                        roughRuns.forEach { run ->
                            val geo = run.map { GeoPoint(it.lat, it.lon) }
                            mapView.overlays.add(routeLine(mapView, geo, CASING_ARGB, 5.6f * density))
                            mapView.overlays.add(routeLine(mapView, geo, Color.WHITE, 3.6f * density))
                            mapView.overlays.add(routeLine(mapView, geo, ROUTE_ARGB, 2f * density, dashDp = floatArrayOf(5f, 4f)))
                        }
                        manualRuns.forEach { run ->
                            val geo = run.map { GeoPoint(it.lat, it.lon) }
                            mapView.overlays.add(routeLine(mapView, geo, CASING_ARGB, 5.6f * density))
                            mapView.overlays.add(routeLine(mapView, geo, Color.WHITE, 3.6f * density))
                            // Dots: a dash of almost nothing with round caps.
                            mapView.overlays.add(routeLine(mapView, geo, ROUTE_ARGB, 2.6f * density, dashDp = floatArrayOf(0.1f, 5f)))
                        }
                    }

                    // Place names (for the styles that draw them as a separate layer) go above the line.
                    labels.overlayFor(mapStyle, mapView.context)?.let { mapView.overlays.add(it) }

                    // Start drawn first so the finish flag ends up on top when they coincide (a
                    // loop route), per the "finish must always show in front of start" requirement.
                    mapView.overlays.add(StartFinishOverlay(shownGeoPoints.first(), shownGeoPoints.last(), mapView.resources.displayMetrics.density))

                    if (!windArrows.isNullOrEmpty()) {
                        // One arrow per weather-sample point was too dense to read; halve it.
                        val thinnedArrows = windArrows.filterIndexed { index, _ -> index % 2 == 0 }
                        // Weather samples are evenly spaced by distance, so sample i sits at i / (n - 1)
                        // of the route; on an out-and-back road this picks the outbound or return arrow
                        // by where the finger is, not by which one happens to be closer on the map.
                        val active = scrubFraction?.let { f ->
                            val last = (windArrows.size - 1).coerceAtLeast(1)
                            windArrows.indices.filter { it % 2 == 0 }
                                .minByOrNull { kotlin.math.abs(it.toFloat() / last - f) }
                                ?.let { windArrows[it] }
                        }
                        mapView.overlays.add(WindArrowsOverlay(thinnedArrows, mapView.resources.displayMetrics.density, active))
                    }

                    highlightPoint?.let { mapView.overlays.add(PointMarkerOverlay(it, HIGHLIGHT_ARGB, density)) }
                    markers.forEach { mapView.overlays.add(PointMarkerOverlay(it.point, it.argb, density)) }
                    if (stops.isNotEmpty()) mapView.overlays.add(StopsOverlay(stops, density, onStopTap, cropRangeM))
                    if (arrows.isNotEmpty()) mapView.overlays.add(DirectionArrowsOverlay(arrows, density))
                    if (kmMarkers.isNotEmpty()) mapView.overlays.add(KmMarkersOverlay(kmMarkers, density))
                    // A tap on the line is handled before the tap on the bare map below it.
                    onTrackTap?.let { mapView.overlays.add(TrackTapOverlay(points, density, it)) }

                    if (autoFit && lastState?.points != points) {
                        val bbox = boundingBoxOf(geoPoints)
                        mapView.post { mapView.zoomToBoundingBox(bbox, false, 80) }
                    }
                }
                // With no track yet (a route being started) the markers still need drawing.
                if (points.isEmpty()) {
                    labels.overlayFor(mapStyle, mapView.context)?.let { mapView.overlays.add(it) }
                    val density = mapView.resources.displayMetrics.density
                    markers.forEach { mapView.overlays.add(PointMarkerOverlay(it.point, it.argb, density)) }
                }
                // On top of everything else so it gets first pick of a touch.
                if (onPointDragged != null && draggablePoints.isNotEmpty()) {
                    mapView.overlays.add(DragHandlesOverlay(draggablePoints, drag, mapView.resources.displayMetrics.density, onPointDragged))
                }
                mapView.tag = MapViewFitState(mapStyle, points, keys)
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

private const val ROUTE_ARGB = 0xFFE53935.toInt()
private const val CASING_ARGB = 0xFF9B1C18.toInt()

/** One polyline of the route: [widthPx] wide, optionally dashed ([dashDp] = on/off lengths in dp). */
private fun routeLine(mapView: MapView, geoPoints: List<GeoPoint>, argb: Int, widthPx: Float, dashDp: FloatArray? = null): Polyline {
    val density = mapView.resources.displayMetrics.density
    return Polyline(mapView).apply {
        setPoints(geoPoints)
        outlinePaint.color = argb
        outlinePaint.strokeWidth = widthPx
        // osmdroid draws each segment separately; with the default BUTT caps every
        // tiny GPX direction change leaves a notch, which read as a "fuzzy" line.
        outlinePaint.isAntiAlias = true
        outlinePaint.strokeCap = if (dashDp == null || dashDp[0] < 1f) Paint.Cap.ROUND else Paint.Cap.BUTT
        outlinePaint.strokeJoin = Paint.Join.ROUND
        if (dashDp != null) outlinePaint.pathEffect = DashPathEffect(floatArrayOf(dashDp[0] * density, dashDp[1] * density), 0f)
        // A tap on the line must reach the map-tap overlay below (placing a stop is
        // exactly a tap on the route): by default osmdroid swallows it and pops up an
        // empty info bubble instead.
        infoWindow = null
        setOnClickListener { _, _, _ -> false }
    }
}

/** The point being dragged, kept outside the overlay because the overlays are rebuilt on every recomposition. */
private class DragState {
    var index: Int = -1
    var lat: Double = 0.0
    var lon: Double = 0.0
}

/**
 * Lets a finger pick up any of [points] (within ~28 dp) and move it; the map does not pan during the
 * drag. While it lasts a larger blue dot follows the finger, and on release [onDropped] gets the new place.
 */
private class DragHandlesOverlay(
    private val points: List<RoutePoint>,
    private val drag: DragState,
    private val density: Float,
    private val onDropped: (Int, Double, Double) -> Unit,
) : Overlay() {
    private val ringPaint = markerRingPaint(density)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = HIGHLIGHT_ARGB; style = Paint.Style.FILL }
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = HIGHLIGHT_ARGB; alpha = 70; style = Paint.Style.FILL }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow || drag.index < 0) return
        val out = android.graphics.Point()
        mapView.projection.toPixels(GeoPoint(drag.lat, drag.lon), out)
        canvas.drawCircle(out.x.toFloat(), out.y.toFloat(), 21.6f * density, haloPaint)
        canvas.drawCircle(out.x.toFloat(), out.y.toFloat(), MARKER_DIAMETER_DP / 2 * density, dotPaint)
        canvas.drawCircle(out.x.toFloat(), out.y.toFloat(), MARKER_DIAMETER_DP / 2 * density, ringPaint)
    }

    override fun onTouchEvent(event: MotionEvent, mapView: MapView): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val out = android.graphics.Point()
                val hitRadius = 28f * density
                var best = -1
                var bestDistance = hitRadius
                points.forEachIndexed { i, p ->
                    mapView.projection.toPixels(GeoPoint(p.lat, p.lon), out)
                    val d = kotlin.math.hypot(out.x - event.x, out.y - event.y)
                    if (d <= bestDistance) {
                        best = i
                        bestDistance = d
                    }
                }
                if (best < 0) return false
                drag.index = best
                drag.lat = points[best].lat
                drag.lon = points[best].lon
                mapView.parent?.requestDisallowInterceptTouchEvent(true)
                mapView.invalidate()
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (drag.index < 0) return false
                val p = mapView.projection.fromPixels(event.x.toInt(), event.y.toInt())
                drag.lat = p.latitude
                drag.lon = p.longitude
                mapView.invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (drag.index < 0) return false
                val index = drag.index
                val p = mapView.projection.fromPixels(event.x.toInt(), event.y.toInt())
                val moved = kotlin.math.hypot(
                    (p.latitude - points[index].lat) * 111_320.0,
                    (p.longitude - points[index].lon) * 111_320.0 * kotlin.math.cos(Math.toRadians(p.latitude)),
                )
                drag.index = -1
                mapView.invalidate()
                // A touch that never left the point is a tap on it, not a move.
                if (moved > MIN_DRAG_M) onDropped(index, p.latitude, p.longitude)
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (drag.index < 0) return false
                drag.index = -1
                mapView.invalidate()
                return true
            }
        }
        return false
    }
}

private const val MIN_DRAG_M = 5.0

/** Distance labels along the route: black pills with white text and a tail pointing at the line, like Strava's. */
private class KmMarkersOverlay(private val markers: List<KmMarker>, private val density: Float) : Overlay() {
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 12f * density
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1C1C1C.toInt(); style = Paint.Style.FILL }
    private val tail = Path()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val out = android.graphics.Point()
        val padX = 6f * density
        val padY = 3f * density
        val tailH = 5f * density
        val fm = textPaint.fontMetrics
        val textH = fm.descent - fm.ascent
        for (m in markers) {
            mapView.projection.toPixels(GeoPoint(m.point.lat, m.point.lon), out)
            val w = textPaint.measureText(m.label) + 2 * padX
            val h = textH + 2 * padY
            val cx = out.x.toFloat()
            val bottom = out.y - tailH - 1.5f * density
            val rect = android.graphics.RectF(cx - w / 2, bottom - h, cx + w / 2, bottom)
            canvas.drawRoundRect(rect, 5f * density, 5f * density, fillPaint)
            tail.reset()
            tail.moveTo(cx - 4f * density, bottom - 0.5f)
            tail.lineTo(cx + 4f * density, bottom - 0.5f)
            tail.lineTo(cx, out.y - 1.5f * density)
            tail.close()
            canvas.drawPath(tail, fillPaint)
            canvas.drawText(m.label, cx, rect.centerY() - (fm.ascent + fm.descent) / 2, textPaint)
        }
    }
}

/** A white disc with a dark red edge and an arrow along the direction of travel, on the route. */
private class DirectionArrowsOverlay(private val arrows: List<MapArrow>, private val density: Float) : Overlay() {
    private val radius = 8f * density
    private val discPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CASING_ARGB; style = Paint.Style.STROKE; strokeWidth = 1.5f * density }
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CASING_ARGB; style = Paint.Style.FILL }
    private val arrow = Path().apply {
        // Points up (north): tip, then the two back corners, with a notch in the middle.
        moveTo(0f, -radius * 0.62f)
        lineTo(radius * 0.46f, radius * 0.5f)
        lineTo(0f, radius * 0.18f)
        lineTo(-radius * 0.46f, radius * 0.5f)
        close()
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val out = android.graphics.Point()
        for (a in arrows) {
            mapView.projection.toPixels(GeoPoint(a.point.lat, a.point.lon), out)
            canvas.save()
            canvas.translate(out.x.toFloat(), out.y.toFloat())
            canvas.drawCircle(0f, 0f, radius, discPaint)
            canvas.drawCircle(0f, 0f, radius, edgePaint)
            canvas.rotate(a.bearingDeg.toFloat())
            canvas.drawPath(arrow, arrowPaint)
            canvas.restore()
        }
    }
}

/**
 * Reports a tap that lands on the route line (within ~22 dp): which line segment of [points] and where on it,
 * snapped onto the line. Taps elsewhere pass on to the overlays below.
 */
private class TrackTapOverlay(
    private val points: List<RoutePoint>,
    private val density: Float,
    private val onTap: (Int, Double, Double) -> Unit,
) : Overlay() {
    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) = Unit

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        if (points.size < 2) return false
        val projection = mapView.projection
        val a = android.graphics.Point()
        val b = android.graphics.Point()
        var bestIndex = -1
        var bestDistance = 22f * density
        var bestT = 0f
        projection.toPixels(GeoPoint(points[0].lat, points[0].lon), a)
        for (i in 0 until points.size - 1) {
            projection.toPixels(GeoPoint(points[i + 1].lat, points[i + 1].lon), b)
            val dx = (b.x - a.x).toFloat()
            val dy = (b.y - a.y).toFloat()
            val len2 = dx * dx + dy * dy
            val t = if (len2 <= 0f) 0f else (((e.x - a.x) * dx + (e.y - a.y) * dy) / len2).coerceIn(0f, 1f)
            val d = kotlin.math.hypot(a.x + t * dx - e.x, a.y + t * dy - e.y)
            if (d < bestDistance) {
                bestDistance = d
                bestIndex = i
                bestT = t
            }
            a.set(b.x, b.y)
        }
        if (bestIndex < 0) return false
        val p = points[bestIndex]
        val q = points[bestIndex + 1]
        onTap(bestIndex, p.lat + (q.lat - p.lat) * bestT, p.lon + (q.lon - p.lon) * bestT)
        return true
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
        "CartoPositron", 20, "https://{s}.basemaps.cartocdn.com/light_all/", CARTO_SUFFIX, CARTO_COPYRIGHT, subdomains = CARTO_SUBDOMAINS,
    )
    MapStyle.THUNDERFOREST_OUTDOORS -> xyzTileSource(
        "ThunderforestOutdoors", 22, "https://tile.thunderforest.com/outdoors/", ".png?apikey=${MapApiKeys.keyFor(MapKeyProvider.THUNDERFOREST)}",
    )
    // Same provider Strava's own app uses (confirmed via its "Map Data Sources" panel: Mapbox,
    // Maxar, Intermap -- the usual Mapbox "Outdoors" style stack). The classic v4 raster tile API
    // returned nothing for newer accounts (it's been retired); this is Mapbox's current Static
    // Tiles API, which renders a v1 style to plain raster tiles any z/x/y client can consume.
    MapStyle.MAPBOX_OUTDOORS -> xyzTileSource(
        "MapboxOutdoors", 20, "https://api.mapbox.com/styles/v1/mapbox/outdoors-v12/tiles/256/", "?access_token=${MapApiKeys.keyFor(MapKeyProvider.MAPBOX)}",
    )
    MapStyle.CARTO_VOYAGER -> xyzTileSource(
        "CartoVoyagerNoLabels", 20, "https://{s}.basemaps.cartocdn.com/rastertiles/voyager_nolabels/", CARTO_SUFFIX, CARTO_COPYRIGHT, subdomains = CARTO_SUBDOMAINS,
    )
    MapStyle.CARTO_DARK -> xyzTileSource(
        "CartoDarkNoLabels", 20, "https://{s}.basemaps.cartocdn.com/dark_nolabels/", CARTO_SUFFIX, CARTO_COPYRIGHT, subdomains = CARTO_SUBDOMAINS,
    )
    MapStyle.ESRI_LIGHT_GRAY -> xyzTileSource(
        "EsriLightGrayBase", 16, "$ESRI_TILES/Canvas/World_Light_Gray_Base/MapServer/tile/", "", ESRI_COPYRIGHT, yBeforeX = true,
    )
    MapStyle.ESRI_DARK_GRAY -> xyzTileSource(
        "EsriDarkGrayBase", 16, "$ESRI_TILES/Canvas/World_Dark_Gray_Base/MapServer/tile/", "", ESRI_COPYRIGHT, yBeforeX = true,
    )
}

// CARTO asks for the key as "?key=" (its documentation; "api_key" is ignored and the tiles come with an "API key required" watermark).
private val CARTO_SUFFIX get() = ".png?key=${MapApiKeys.keyFor(MapKeyProvider.CARTO)}"
private const val CARTO_SUBDOMAINS = "abcd"
private const val CARTO_COPYRIGHT = "© OpenStreetMap contributors © CARTO"
private const val ESRI_TILES = "https://server.arcgisonline.com/ArcGIS/rest/services"
private const val ESRI_COPYRIGHT = "Tiles © Esri, HERE, Garmin, © OpenStreetMap contributors"

/** The transparent layer of place names that goes above the route for the styles that draw their names separately. */
private fun labelsSourceFor(style: MapStyle): ITileSource? = when (style) {
    MapStyle.CARTO_VOYAGER -> xyzTileSource(
        "CartoVoyagerLabels", 20, "https://{s}.basemaps.cartocdn.com/rastertiles/voyager_only_labels/", CARTO_SUFFIX, CARTO_COPYRIGHT, subdomains = CARTO_SUBDOMAINS,
    )
    MapStyle.CARTO_DARK -> xyzTileSource(
        "CartoDarkLabels", 20, "https://{s}.basemaps.cartocdn.com/dark_only_labels/", CARTO_SUFFIX, CARTO_COPYRIGHT, subdomains = CARTO_SUBDOMAINS,
    )
    MapStyle.ESRI_LIGHT_GRAY -> xyzTileSource(
        "EsriLightGrayLabels", 16, "$ESRI_TILES/Canvas/World_Light_Gray_Reference/MapServer/tile/", "", ESRI_COPYRIGHT, yBeforeX = true,
    )
    MapStyle.ESRI_DARK_GRAY -> xyzTileSource(
        "EsriDarkGrayLabels", 16, "$ESRI_TILES/Canvas/World_Dark_Gray_Reference/MapServer/tile/", "", ESRI_COPYRIGHT, yBeforeX = true,
    )
    else -> null
}

/**
 * The names layer of the current style: a second tile provider and its overlay, made once per style and kept
 * across the many rebuilds of the overlay list. [release] stops the provider's threads.
 */
private class LabelsLayer {
    private var style: MapStyle? = null
    private var keys: String? = null
    private var provider: MapTileProviderBasic? = null
    private var overlay: TilesOverlay? = null

    fun overlayFor(style: MapStyle, context: Context): TilesOverlay? {
        val source = labelsSourceFor(style)
        if (source == null) {
            release()
            return null
        }
        if (this.style != style || this.keys != MapApiKeys.fingerprint()) {
            release()
            val tileProvider = MapTileProviderBasic(context.applicationContext, source)
            overlay = TilesOverlay(tileProvider, context).apply {
                loadingBackgroundColor = Color.TRANSPARENT
                loadingLineColor = Color.TRANSPARENT
            }
            provider = tileProvider
            this.style = style
            this.keys = MapApiKeys.fingerprint()
        }
        return overlay
    }

    fun release() {
        provider?.detach()
        provider = null
        overlay = null
        style = null
        keys = null
    }
}

/** A simple z/x/y raster tile source, built directly on osmdroid's base class for reliability
 * across osmdroid versions (unlike the XYZTileSource convenience class, which isn't available
 * in every release). */
private fun xyzTileSource(
    name: String,
    maxZoom: Int,
    baseUrl: String,
    urlSuffix: String,
    copyright: String = "",
    /** Esri's servers number tiles zoom/row/column, i.e. y before x. */
    yBeforeX: Boolean = false,
    /** Letters to pick from for a "{s}" in [baseUrl] (CARTO spreads its tiles over a, b, c and d). */
    subdomains: String = "",
): OnlineTileSourceBase =
    object : OnlineTileSourceBase(name, 0, maxZoom, 256, urlSuffix, arrayOf(baseUrl), copyright) {
        override fun getTileURLString(pMapTileIndex: Long): String {
            val x = MapTileIndex.getX(pMapTileIndex)
            val y = MapTileIndex.getY(pMapTileIndex)
            val (first, second) = if (yBeforeX) y to x else x to y
            val host = if (subdomains.isEmpty()) baseUrl else baseUrl.replace("{s}", subdomains.random().toString())
            return host + MapTileIndex.getZoom(pMapTileIndex) + "/" + first + "/" + second + urlSuffix
        }
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

/** Green dot at the route's start, checkered dot at its end (end always drawn on top when they coincide, e.g. a loop). */
private class StartFinishOverlay(
    private val start: GeoPoint,
    private val finish: GeoPoint,
    private val density: Float,
) : Overlay() {
    private val radius = MARKER_DIAMETER_DP / 2 * density
    private val startFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#43A047"); style = Paint.Style.FILL }
    private val ringPaint = markerRingPaint(density)
    private val blackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; style = Paint.Style.FILL }
    private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }
    private val clip = Path()

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val out = android.graphics.Point()

        mapView.projection.toPixels(start, out)
        canvas.drawCircle(out.x.toFloat(), out.y.toFloat(), radius, startFillPaint)
        canvas.drawCircle(out.x.toFloat(), out.y.toFloat(), radius, ringPaint)

        mapView.projection.toPixels(finish, out)
        drawCheckeredDot(canvas, out.x.toFloat(), out.y.toFloat())
    }

    private fun drawCheckeredDot(canvas: Canvas, cx: Float, cy: Float) {
        canvas.save()
        clip.reset()
        clip.addCircle(cx, cy, radius, Path.Direction.CW)
        canvas.clipPath(clip)
        canvas.drawCircle(cx, cy, radius, whitePaint)
        val cells = 4
        val cellSize = 2 * radius / cells
        for (row in 0 until cells) {
            for (col in 0 until cells) {
                if ((row + col) % 2 == 0) {
                    val left = cx - radius + col * cellSize
                    val top = cy - radius + row * cellSize
                    canvas.drawRect(left, top, left + cellSize, top + cellSize, blackPaint)
                }
            }
        }
        canvas.restore()
        canvas.drawCircle(cx, cy, radius, ringPaint)
    }
}

/** A 12 dp dot with a white ring: the scrub position (blue) or a gauge's min/max location. */
private class PointMarkerOverlay(private val point: RoutePoint, argb: Int, density: Float) : Overlay() {
    private val radius = MARKER_DIAMETER_DP / 2 * density
    private val ringPaint = markerRingPaint(density)
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = argb; style = Paint.Style.FILL }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val out = android.graphics.Point()
        mapView.projection.toPixels(GeoPoint(point.lat, point.lon), out)
        canvas.drawCircle(out.x.toFloat(), out.y.toFloat(), radius, dotPaint)
        canvas.drawCircle(out.x.toFloat(), out.y.toFloat(), radius, ringPaint)
    }
}

/** Clock pins for planned stops; a tap within ~24 dp of one reports its index. */
private class StopsOverlay(
    private val stops: List<RouteStop>,
    private val density: Float,
    private val onTap: ((Int) -> Unit)?,
    /** Stops outside it are cropped away from the ride: neither drawn nor tappable. */
    private val cropRangeM: ClosedFloatingPointRange<Double>? = null,
) : Overlay() {
    private fun visible(stop: RouteStop) = cropRangeM == null || stop.distanceM in cropRangeM

    private val radius = 10f * density
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = STOP_ARGB; style = Paint.Style.FILL }
    private val ringPaint = markerRingPaint(density)
    private val handPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 1.8f * density
        strokeCap = Paint.Cap.ROUND
    }

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val out = android.graphics.Point()
        for (stop in stops) {
            if (!visible(stop)) continue
            mapView.projection.toPixels(GeoPoint(stop.lat, stop.lon), out)
            val x = out.x.toFloat()
            val y = out.y.toFloat()
            canvas.drawCircle(x, y, radius, fillPaint)
            canvas.drawCircle(x, y, radius, ringPaint)
            // Clock hands at ten past twelve.
            canvas.drawLine(x, y, x, y - radius * 0.6f, handPaint)
            canvas.drawLine(x, y, x + radius * 0.45f, y + radius * 0.2f, handPaint)
        }
    }

    override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
        val callback = onTap ?: return false
        val out = android.graphics.Point()
        val hitRadius = 24f * density
        val hit = stops.indices.filter { visible(stops[it]) }.minByOrNull { i ->
            mapView.projection.toPixels(GeoPoint(stops[i].lat, stops[i].lon), out)
            kotlin.math.hypot(out.x - e.x, out.y - e.y)
        } ?: return false
        mapView.projection.toPixels(GeoPoint(stops[hit].lat, stops[hit].lon), out)
        if (kotlin.math.hypot(out.x - e.x, out.y - e.y) > hitRadius) return false
        callback(hit)
        return true
    }
}

/** Start, finish and position markers all share one size: 12 dp across with a 2 dp white ring. */
private const val MARKER_DIAMETER_DP = 14.4f
private const val HIGHLIGHT_ARGB = 0xFF1E88E5.toInt()
private const val STOP_ARGB = 0xFFE5532D.toInt()

private fun markerRingPaint(density: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    color = Color.WHITE
    style = Paint.Style.STROKE
    strokeWidth = 2.4f * density
}

/**
 * Wind arrows built from ONE fixed template -- a solid triangular head with its tip at the origin
 * pointing straight up, shaft hanging below from the centre of its base -- only translated onto the route point and
 * rotated with the canvas. The tip sits exactly on the track; the arrow lies upwind of it.
 *
 * Shaft length is relative to this route's own wind range (all sizes in dp):
 * shaft = 4 + 18 * (v - vMin) / (vMax - vMin), so the calmest point gets the shortest arrow and the
 * windiest the longest, whatever the absolute speeds. Colour is absolute instead, one shade per
 * 10 km/h band (see [windBandColor]), so it stays comparable between rides. The [active] arrow (the
 * one matching the scrub position) gets a 2 dp white edge instead of 1 dp and is drawn last, on top.
 */
private class WindArrowsOverlay(
    private val arrows: List<WindArrowPoint>,
    density: Float,
    private val active: WindArrowPoint? = null,
) : Overlay() {
    private val headLength = 8f * density
    private val headHalfWidth = 5f * density
    private val minShaft = 4f * density
    private val maxShaft = 22f * density
    private val outline = 1f * density
    private val activeOutline = 2f * density
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
        for (arrow in arrows) {
            if (arrow !== active) drawArrow(canvas, mapView, arrow, outline)
        }
        active?.let { drawArrow(canvas, mapView, it, activeOutline) }
    }

    private fun drawArrow(canvas: Canvas, mapView: MapView, arrow: WindArrowPoint, edge: Float) {
        val out = android.graphics.Point()
        mapView.projection.toPixels(GeoPoint(arrow.point.lat, arrow.point.lon), out)
        // Template points up (north); wind blows TOWARDS from + 180, and canvas.rotate() is
        // clockwise on screen -- the same sense as compass bearings.
        val bearingDeg = ((arrow.windFromDeg + 180.0) % 360.0).toFloat()
        canvas.save()
        canvas.translate(out.x.toFloat(), out.y.toFloat())
        canvas.rotate(bearingDeg)
        drawTemplate(canvas, shaftFor(arrow.windSpeedKmh), windBandColor(arrow.windSpeedKmh), edge)
        canvas.restore()
    }

    private fun shaftFor(windSpeedKmh: Double): Float {
        val range = maxSpeed - minSpeed
        val t = if (range <= 0.0) 1f else ((windSpeedKmh - minSpeed) / range).coerceIn(0.0, 1.0).toFloat()
        return minShaft + (maxShaft - minShaft) * t
    }

    private fun drawTemplate(canvas: Canvas, shaft: Float, color: Int, edge: Float) {
        shaftPaint.color = color
        headPaint.color = color
        val shaftEnd = headLength + shaft
        canvas.drawLine(0f, headLength, 0f, shaftEnd + edge, outlinePaint.apply { strokeWidth = shaftPaint.strokeWidth + 2 * edge })
        canvas.drawPath(headPath, outlinePaint.apply { strokeWidth = 2 * edge })
        // Starts half a pixel inside the head so no gap shows between shaft and head.
        canvas.drawLine(0f, headLength - 0.5f, 0f, shaftEnd, shaftPaint)
        canvas.drawPath(headPath, headPaint)
    }
}
