package com.windrm.app.ui.builder

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.windrm.app.R
import com.windrm.app.domain.ActivityType
import com.windrm.app.model.Route
import com.windrm.app.model.RoutePoint
import com.windrm.app.repository.RoutingError
import com.windrm.app.ui.components.AppBar
import com.windrm.app.ui.components.KmMarker
import com.windrm.app.ui.components.MapArrow
import com.windrm.app.ui.components.MapMarker
import com.windrm.app.ui.components.RouteMapView
import com.windrm.app.ui.routedetail.SegmentedChoice
import kotlin.math.max
import kotlin.math.roundToInt

private val PavedColor = Color(0xFF6B7280)
private val UnpavedColor = Color(0xFFA8793F)
private val ManualColor = Color(0xFFD7263D)
private val StartMarkerArgb = 0xFF2E9E44.toInt()
private val MidMarkerArgb = 0xFFE53935.toInt()
private const val SPARKLINE_MAX_POINTS = 240

/**
 * Draws a route by tapping the map: each tap adds a point, and the stretch from the previous one is
 * routed along roads (or trails on a trek). Shows distance, climb, surface mix and profile, and
 * saves the result under a name, optionally also as a GPX file.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteBuilderScreen(viewModel: RouteBuilderViewModel, onBack: () -> Unit, onOpenSettings: () -> Unit, onSaved: (Route) -> Unit) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var showSave by remember { mutableStateOf(false) }
    var trackTap by remember { mutableStateOf<TrackTap?>(null) }
    var waypointMenu by remember { mutableStateOf<Int?>(null) }

    val errorTexts = mapOf(
        RoutingError.NO_ROUTE to stringResource(R.string.builder_error_no_route),
        RoutingError.NO_CONNECTION to stringResource(R.string.builder_error_connection),
        RoutingError.FAILED to stringResource(R.string.builder_error_failed),
    )
    LaunchedEffect(viewModel.error) {
        viewModel.error?.let { error ->
            snackbar.showSnackbar(errorTexts.getValue(error))
            viewModel.clearError()
        }
    }

    val draft = remember(viewModel.segments, viewModel.activity) { viewModel.draft }
    val mapPoints = remember(viewModel.segments) {
        viewModel.segments.flatMap { s -> s.points.map { RoutePoint(lat = it.lat, lon = it.lon, eleM = it.eleM) } }
    }
    val markers = remember(viewModel.waypoints, viewModel.segments) {
        val started = viewModel.segments.isNotEmpty()
        val shown = if (started) viewModel.waypoints.drop(1).dropLast(1) else viewModel.waypoints
        shown.map { MapMarker(RoutePoint(lat = it.lat, lon = it.lon), if (started) MidMarkerArgb else StartMarkerArgb) }
    }
    val dragHandles = remember(viewModel.waypoints) { viewModel.waypoints.map { RoutePoint(lat = it.lat, lon = it.lon) } }
    val profileValues = remember(viewModel.segments) { sparklineValues(mapPoints) }
    val roughRuns = remember(viewModel.segments) {
        viewModel.segments.flatMap { it.roughRuns() }.map { run -> run.map { RoutePoint(lat = it.lat, lon = it.lon) } }
    }
    val manualRuns = remember(viewModel.segments) {
        viewModel.segments.filter { it.manual }.map { s -> s.points.map { RoutePoint(lat = it.lat, lon = it.lon) } }
    }
    val kmMarkers = remember(draft, viewModel.activity) {
        kmMarkersOf(draft?.points.orEmpty(), if (viewModel.activity == ActivityType.TREK) TREK_KM_STEP else RIDE_KM_STEP)
    }
    val arrows = remember(viewModel.segments) { viewModel.segments.mapNotNull(::arrowOf) }

    Scaffold(
        topBar = {
            AppBar(
                title = stringResource(
                    when (viewModel.mode) {
                        BuilderMode.NEW -> R.string.builder_title
                        BuilderMode.EDIT -> R.string.builder_title_edit
                        BuilderMode.DUPLICATE -> R.string.builder_title_duplicate
                    },
                ),
                onBack = onBack,
                onOpenSettings = onOpenSettings,
                actions = {
                    TextButton(
                        onClick = { showSave = true },
                        enabled = draft != null && !viewModel.busy,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color.White,
                            disabledContentColor = Color.White.copy(alpha = 0.5f),
                        ),
                    ) { Text(stringResource(R.string.builder_save).uppercase()) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SegmentedChoice(
                    options = listOf(
                        stringResource(R.string.activity_ride) to Icons.Filled.DirectionsBike,
                        stringResource(R.string.activity_trek) to Icons.Filled.Hiking,
                    ),
                    selected = viewModel.activity.ordinal,
                    onSelect = { viewModel.changeActivity(ActivityType.entries[it]) },
                )
                // Both switches on one row: paved roads only (a ride's choice) and manual mode (straight lines, off the roads).
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (viewModel.activity == ActivityType.RIDE) {
                        ToggleItem(
                            stringResource(R.string.builder_paved_only),
                            checked = !viewModel.allowUnpaved,
                            onChange = { viewModel.changeAllowUnpaved(!it) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    ToggleItem(
                        stringResource(R.string.builder_manual_mode),
                        checked = viewModel.manualMode,
                        onChange = viewModel::changeManualMode,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, top = 10.dp)
                    .clip(RoundedCornerShape(8.dp)),
            ) {
                val center = viewModel.mapCenter
                if (center == null) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                } else {
                    RouteMapView(
                        points = mapPoints,
                        mapStyle = viewModel.mapStyle,
                        markers = markers,
                        onMapTap = { lat, lon -> viewModel.onMapTap(lat, lon) },
                        casedLine = true,
                        roughRuns = roughRuns,
                        manualRuns = manualRuns,
                        kmMarkers = kmMarkers,
                        arrows = arrows,
                        onTrackTap = { index, lat, lon -> if (!viewModel.busy) trackTap = TrackTap(index, lat, lon) },
                        draggablePoints = dragHandles,
                        onPointDragged = { index, lat, lon -> viewModel.moveWaypoint(index, lat, lon) },
                        onPointTapped = { index -> if (!viewModel.busy) waypointMenu = index },
                        height = Dp.Unspecified,
                        autoFit = false,
                        initialCenter = center,
                        initialZoom = viewModel.mapZoom,
                    )
                }
                if (viewModel.busy) {
                    Surface(shape = CircleShape, color = Color.White, shadowElevation = 3.dp, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                        CircularProgressIndicator(Modifier.padding(8.dp).size(20.dp), strokeWidth = 2.dp)
                    }
                }
                val hint = when (viewModel.waypoints.size) {
                    0 -> R.string.builder_tap_start
                    1 -> R.string.builder_tap_next
                    else -> null
                }
                if (hint != null && center != null) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xE6282828),
                        contentColor = Color.White,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                    ) {
                        Text(stringResource(hint), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
                    }
                }
            }

            RouteStats(viewModel, draft, profileValues)

            // Undo and Clear are as wide as each other; the middle one is wider so "Back to start" stays on one line.
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionPill(stringResource(R.string.builder_undo), viewModel::undo, viewModel.waypoints.isNotEmpty() && !viewModel.busy, Modifier.weight(1f))
                ActionPill(stringResource(R.string.builder_back_to_start), viewModel::returnToStart, viewModel.canReturnToStart() && !viewModel.busy, Modifier.weight(1.5f))
                ActionPill(stringResource(R.string.builder_clear), viewModel::clear, viewModel.waypoints.isNotEmpty(), Modifier.weight(1f))
            }
        }
    }

    waypointMenu?.let { index ->
        AlertDialog(
            onDismissRequest = { waypointMenu = null },
            title = { Text(stringResource(R.string.builder_waypoint_title, index + 1)) },
            text = {
                Column {
                    // The last waypoint already is the end.
                    if (index != viewModel.waypoints.lastIndex) {
                        TextButton(
                            onClick = {
                                viewModel.endRouteAtWaypoint(index)
                                waypointMenu = null
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.builder_end_here), modifier = Modifier.fillMaxWidth()) }
                    }
                    TextButton(
                        onClick = {
                            viewModel.deleteWaypoint(index)
                            waypointMenu = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.builder_delete_waypoint), modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { waypointMenu = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    trackTap?.let { tap ->
        AlertDialog(
            onDismissRequest = { trackTap = null },
            title = { Text(stringResource(R.string.builder_track_title)) },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            viewModel.insertWaypointOnTrack(tap.segmentIndex, tap.lat, tap.lon)
                            trackTap = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.builder_insert_waypoint), modifier = Modifier.fillMaxWidth()) }
                    TextButton(
                        onClick = {
                            viewModel.endRouteAt(tap.lat, tap.lon)
                            trackTap = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(stringResource(R.string.builder_end_here), modifier = Modifier.fillMaxWidth()) }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { trackTap = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (showSave) {
        SaveRouteDialog(
            initialName = remember { viewModel.defaultName() },
            folderName = viewModel.gpxFolderName,
            // A changed route would only add a second, different file to the folder: left to the user to ask for.
            folderCopyByDefault = viewModel.mode != BuilderMode.EDIT,
            mode = viewModel.mode,
            onDismiss = { showSave = false },
            onConfirm = { name, toFolder, share ->
                showSave = false
                viewModel.save(name, toFolder) { saved ->
                    if (share) shareGpx(context, saved)
                    onSaved(saved)
                }
            },
        )
    }
}

/** Distance, climb, how many points, the surface mix and the elevation profile of the route so far. */
@Composable
private fun RouteStats(viewModel: RouteBuilderViewModel, draft: Route?, profileValues: List<Float>) {
    Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val duration = remember(draft, viewModel.activity, viewModel.rideSpeedKmh) { draft?.let(viewModel::estimateSeconds) }
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Bottom) {
            Stat("%.1f".format(draft?.distanceKm ?: 0.0), "km")
            Stat("${(draft?.elevationGainM ?: 0.0).roundToInt()}", "m↑")
            Stat(duration?.let(::formatDuration) ?: "–", "")
            Stat("${viewModel.waypoints.size}", stringResource(R.string.builder_points))
        }

        val rough = viewModel.unpavedM + viewModel.trailM
        val manual = viewModel.manualM
        val total = viewModel.pavedM + rough
        val known = viewModel.surfaceKnown && total > 0
        // The bar splits the route by surface: paved, unpaved and drawn by hand.
        val barParts = if (known) listOf(viewModel.pavedM to PavedColor, rough to UnpavedColor, manual to ManualColor) else listOf(manual to ManualColor)
        Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.outlineVariant)) {
            barParts.filter { it.first > 0 }.forEach { (meters, color) -> Box(Modifier.weight(meters.toFloat()).fillMaxHeight().background(color)) }
        }
        if (known || manual > 0) {
            // On a paved-only ride the unpaved part is a shortfall: its length is shown in the warning color.
            val roughColor = if (viewModel.leftoverRoughM > 1.0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            val plain = MaterialTheme.colorScheme.onSurfaceVariant
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (known) {
                    LegendItem(PavedColor, "${stringResource(R.string.builder_paved)} ${(viewModel.pavedM / total * 100).roundToInt()}%", plain)
                    if (rough > 0) {
                        LegendItem(
                            UnpavedColor,
                            "${stringResource(R.string.builder_unpaved)} ${(rough / total * 100).roundToInt()}% · ${formatMeters(rough)}",
                            roughColor,
                        )
                    }
                }
                if (manual > 0) LegendItem(ManualColor, "${stringResource(R.string.builder_manual)} · ${formatMeters(manual)}", plain)
            }
        }
        if (!known && viewModel.segments.any { !it.manual }) {
            Text(stringResource(R.string.builder_unknown_surface), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        ElevationSparkline(profileValues, Modifier.fillMaxWidth().height(56.dp))
    }
}

/** A label with its switch, for the row of switches under Ride / Trekking. */
@Composable
private fun ToggleItem(label: String, checked: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(end = 8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun ActionPill(text: String, onClick: () -> Unit, enabled: Boolean, modifier: Modifier) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp)) {
        Text(text, style = MaterialTheme.typography.titleMedium, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun LegendItem(dot: Color, text: String, textColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
        Text(text, style = MaterialTheme.typography.bodySmall, color = textColor, modifier = Modifier.padding(start = 5.dp))
    }
}

/** "510 m" / "1.2 km". */
private fun formatMeters(meters: Double): String =
    if (meters < 1000) "${meters.roundToInt()} m" else "%.1f km".format(meters / 1000)

/** A tap on the drawn line: where, and which line segment of the track it is on. */
private data class TrackTap(val segmentIndex: Int, val lat: Double, val lon: Double)

/** A distance label at every [stepKm] kilometres along [points] (which carry their distance from the start). */
private fun kmMarkersOf(points: List<RoutePoint>, stepKm: Int): List<KmMarker> {
    if (points.size < 2) return emptyList()
    val total = points.last().distanceFromStartM
    val out = ArrayList<KmMarker>()
    var index = 0
    var km = stepKm
    while (km * 1000.0 <= total) {
        val d = km * 1000.0
        while (index < points.size - 2 && points[index + 1].distanceFromStartM < d) index++
        val a = points[index]
        val b = points[index + 1]
        val span = b.distanceFromStartM - a.distanceFromStartM
        val t = if (span <= 0.0) 0.0 else ((d - a.distanceFromStartM) / span).coerceIn(0.0, 1.0)
        out += KmMarker(RoutePoint(lat = a.lat + (b.lat - a.lat) * t, lon = a.lon + (b.lon - a.lon) * t), km.toString())
        km += stepKm
    }
    return out
}

/** A direction arrow in the middle of a stretch, pointing the way the route goes there. */
private fun arrowOf(segment: com.windrm.app.repository.RoutedSegment): MapArrow? {
    val pts = segment.points
    if (pts.size < 2) return null
    val mid = pts.size / 2
    val from = pts[(mid - 1).coerceAtLeast(0)]
    val to = pts[(mid + 1).coerceAtMost(pts.size - 1)]
    val phi1 = Math.toRadians(from.lat)
    val phi2 = Math.toRadians(to.lat)
    val dLon = Math.toRadians(to.lon - from.lon)
    val y = kotlin.math.sin(dLon) * kotlin.math.cos(phi2)
    val x = kotlin.math.cos(phi1) * kotlin.math.sin(phi2) - kotlin.math.sin(phi1) * kotlin.math.cos(phi2) * kotlin.math.cos(dLon)
    val bearing = (Math.toDegrees(kotlin.math.atan2(y, x)) + 360.0) % 360.0
    return MapArrow(RoutePoint(lat = pts[mid].lat, lon = pts[mid].lon), bearing)
}

private const val RIDE_KM_STEP = 10
private const val TREK_KM_STEP = 2

/** "1h 05m" / "42 min". */
private fun formatDuration(seconds: Long): String {
    val minutes = (seconds / 60.0).roundToInt()
    return if (minutes >= 60) "%dh %02dm".format(minutes / 60, minutes % 60) else "$minutes min"
}

@Composable
private fun Stat(value: String, unit: String) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(unit, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 3.dp, bottom = 3.dp))
    }
}

@Composable
private fun ElevationSparkline(values: List<Float>, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    val baseline = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier) {
        if (values.size < 2) {
            drawLine(baseline, Offset(0f, size.height - 2f), Offset(size.width, size.height - 2f), strokeWidth = 2f)
            return@Canvas
        }
        val low = values.min()
        val span = max(values.max() - low, 40f)
        val line = Path()
        val area = Path()
        values.forEachIndexed { i, v ->
            val x = i.toFloat() / (values.size - 1) * size.width
            val y = size.height - 4f - (v - low) / span * (size.height - 8f)
            if (i == 0) {
                line.moveTo(x, y)
                area.moveTo(x, size.height)
                area.lineTo(x, y)
            } else {
                line.lineTo(x, y)
                area.lineTo(x, y)
            }
        }
        area.lineTo(size.width, size.height)
        area.close()
        drawPath(area, color.copy(alpha = 0.18f))
        drawPath(line, color, style = Stroke(width = 2.dp.toPx()))
    }
}

/** The route's elevations, thinned to a few hundred values: enough for a profile, cheap to draw. */
private fun sparklineValues(points: List<RoutePoint>): List<Float> {
    val elevations = points.mapNotNull { it.eleM?.toFloat() }
    if (elevations.size <= SPARKLINE_MAX_POINTS) return elevations
    val step = elevations.size.toFloat() / SPARKLINE_MAX_POINTS
    return List(SPARKLINE_MAX_POINTS) { elevations[(it * step).toInt()] } + elevations.last()
}
