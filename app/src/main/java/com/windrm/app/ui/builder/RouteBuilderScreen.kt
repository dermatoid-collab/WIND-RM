package com.windrm.app.ui.builder

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.windrm.app.ui.components.MapMarker
import com.windrm.app.ui.components.RouteMapView
import com.windrm.app.ui.routedetail.SegmentedChoice
import kotlin.math.max
import kotlin.math.roundToInt

private val PavedColor = Color(0xFF6B7280)
private val UnpavedColor = Color(0xFFA8793F)
private val TrailColor = Color(0xFF4F8A3A)
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
fun RouteBuilderScreen(viewModel: RouteBuilderViewModel, onBack: () -> Unit, onSaved: (Route) -> Unit) {
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var showSave by remember { mutableStateOf(false) }

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
    val profileValues = remember(viewModel.segments) { sparklineValues(mapPoints) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.builder_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                    }
                },
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                ),
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
                    onSelect = { viewModel.setActivity(ActivityType.entries[it]) },
                )
                if (viewModel.activity == ActivityType.RIDE) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.builder_paved_only), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                stringResource(if (viewModel.allowUnpaved) R.string.builder_unpaved_allowed else R.string.builder_paved_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(checked = !viewModel.allowUnpaved, onCheckedChange = { viewModel.setAllowUnpaved(!it) })
                    }
                }
                Text(
                    stringResource(R.string.builder_routing_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = viewModel::undo, enabled = viewModel.waypoints.isNotEmpty() && !viewModel.busy, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.builder_undo))
                }
                OutlinedButton(onClick = viewModel::returnToStart, enabled = viewModel.canReturnToStart() && !viewModel.busy, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.builder_back_to_start))
                }
                OutlinedButton(onClick = viewModel::clear, enabled = viewModel.waypoints.isNotEmpty(), modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.builder_clear))
                }
            }
        }
    }

    viewModel.pendingUnpaved?.let { pending ->
        AlertDialog(
            onDismissRequest = viewModel::dismissUnpaved,
            title = { Text(stringResource(R.string.builder_unpaved_title)) },
            text = { Text(stringResource(R.string.builder_unpaved_message, pending.roughM.roundToInt())) },
            confirmButton = { TextButton(onClick = viewModel::acceptUnpaved) { Text(stringResource(R.string.builder_use_stretch)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissUnpaved) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (showSave) {
        SaveRouteDialog(
            initialName = remember { viewModel.defaultName() },
            folderName = viewModel.gpxFolderName,
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
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.Bottom) {
            Stat("%.1f".format(draft?.distanceKm ?: 0.0), "km")
            Stat("${(draft?.elevationGainM ?: 0.0).roundToInt()}", "m↑")
            Stat("${viewModel.waypoints.size}", stringResource(R.string.builder_points))
        }

        val total = viewModel.pavedM + viewModel.unpavedM + viewModel.trailM
        val known = viewModel.surfaceKnown && total > 0
        Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.outlineVariant)) {
            if (known) {
                listOf(viewModel.pavedM to PavedColor, viewModel.unpavedM to UnpavedColor, viewModel.trailM to TrailColor)
                    .filter { it.first > 0 }
                    .forEach { (meters, color) -> Box(Modifier.weight(meters.toFloat()).fillMaxHeight().background(color)) }
            }
        }
        if (known) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                listOf(
                    Triple(R.string.builder_paved, viewModel.pavedM, PavedColor),
                    Triple(R.string.builder_unpaved, viewModel.unpavedM, UnpavedColor),
                    Triple(R.string.builder_trail, viewModel.trailM, TrailColor),
                ).filter { it.second > 0 || it.first == R.string.builder_paved }.forEach { (label, meters, color) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                        Text(
                            "${stringResource(label)} ${(meters / total * 100).roundToInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 5.dp),
                        )
                    }
                }
            }
        } else if (viewModel.segments.isNotEmpty()) {
            Text(stringResource(R.string.builder_unknown_surface), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        val leftover = viewModel.leftoverRoughM
        if (leftover > 1.0) {
            Text(
                stringResource(R.string.builder_leftover_warning, leftover.roundToInt()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        ElevationSparkline(profileValues, Modifier.fillMaxWidth().height(56.dp))
    }
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

/** Name for the route, plus what to do with its GPX: a copy in the chosen folder (on by default) and/or the share sheet. */
@Composable
private fun SaveRouteDialog(
    initialName: String,
    folderName: String?,
    onDismiss: () -> Unit,
    onConfirm: (name: String, toFolder: Boolean, share: Boolean) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    var toFolder by remember { mutableStateOf(folderName != null) }
    var share by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.builder_name_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.builder_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (folderName != null) {
                    CheckRow(toFolder, { toFolder = it }, stringResource(R.string.builder_gpx_to_folder, folderName))
                } else {
                    Text(
                        stringResource(R.string.builder_no_folder_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
                CheckRow(share, { share = it }, stringResource(R.string.builder_export_gpx))
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, toFolder && folderName != null, share) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.builder_save))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun CheckRow(checked: Boolean, onChange: (Boolean) -> Unit, label: String) {
    Row(
        Modifier.fillMaxWidth().padding(top = 4.dp).clickable { onChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
