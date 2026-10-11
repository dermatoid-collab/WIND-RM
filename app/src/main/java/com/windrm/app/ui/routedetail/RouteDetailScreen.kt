package com.windrm.app.ui.routedetail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.windrm.app.R
import com.windrm.app.ui.components.SettingsGear
import com.windrm.app.domain.ActivityType
import com.windrm.app.domain.PacingMode
import com.windrm.app.domain.cropped
import com.windrm.app.model.Route
import com.windrm.app.model.RouteStop
import com.windrm.app.ui.components.HorizonDatePickerDialog
import com.windrm.app.ui.components.RouteMapView
import com.windrm.app.ui.routes.FavoriteButton
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteDetailScreen(
    viewModel: RouteDetailViewModel,
    onBack: () -> Unit,
    onForecast: (startEpochS: Long, speedKmh: Double, pacing: PacingMode, cropRangeM: ClosedFloatingPointRange<Double>) -> Unit,
    onLive: (speedKmh: Double, pacing: PacingMode, cropRangeM: ClosedFloatingPointRange<Double>) -> Unit,
    onOpenSettings: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
) {
    val route = viewModel.route
    // The part actually ridden: header stats and the forecast follow the crop slider.
    val ridden = remember(route, viewModel.cropRange) {
        route?.let { r -> viewModel.cropRangeM().let { r.cropped(it.start, it.endInclusive) } }
    }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault()).withZone(ZoneId.systemDefault()) }

    Scaffold(
        topBar = {
            // Hand-built so it can grow to three lines (name, ride summary, date), centred like the original app.
            Surface(color = MaterialTheme.colorScheme.primary, contentColor = Color.White) {
                Row(
                    Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(route?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleLarge)
                        ridden?.let { r ->
                            val movingS = remember(r, viewModel.avgSpeedKmh, viewModel.pacingMode, viewModel.activity, viewModel.caiProfile) {
                                viewModel.movingSeconds(r)
                            }
                            Text(
                                rideSummary(r, movingS),
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 2,
                                textAlign = TextAlign.Center,
                            )
                        }
                        route?.let { r ->
                            // The route's own creation date (the import date when unknown).
                            Text(
                                dateFormatter.format(Instant.ofEpochMilli(r.originalDateEpochMs ?: r.createdAtEpochMs)),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color.White.copy(alpha = 0.8f),
                            )
                        }
                    }
                    if (route != null) {
                        FavoriteButton(isFavorite = route.isFavorite, onClick = viewModel::toggleFavorite, tint = Color.White)
                    } else {
                        Spacer(Modifier.size(48.dp))
                    }
                    SettingsGear(onOpenSettings)
                }
            }
        },
    ) { padding ->
        if (route == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }

        var showTimeDialog by remember { mutableStateOf(false) }
        var placingStop by remember { mutableStateOf(false) }
        var editingStop by remember { mutableStateOf<StopEdit?>(null) }
        var choosingPass by remember { mutableStateOf<List<RouteStop>?>(null) }
        val plannedLabelFormatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy 'at' HH:mm", Locale.getDefault()) }
        var speedText by remember { mutableStateOf(formatSpeedInput(viewModel.avgSpeedKmh)) }
        // The VM may adopt the recorded pace once the route loads; mirror it unless the user is typing.
        LaunchedEffect(viewModel.avgSpeedKmh) {
            if (speedText.replace(',', '.').toDoubleOrNull() != viewModel.avgSpeedKmh) speedText = formatSpeedInput(viewModel.avgSpeedKmh)
        }

        Column(Modifier.fillMaxSize().padding(padding)) {
            SegmentedChoice(
                options = listOf(
                    stringResource(R.string.activity_ride) to Icons.Filled.DirectionsBike,
                    stringResource(R.string.activity_trek) to Icons.Filled.Hiking,
                ),
                selected = viewModel.activity.ordinal,
                onSelect = { viewModel.changeActivity(ActivityType.entries[it]) },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
            )

            FormRow(icon = Icons.Filled.Event, onClick = { showTimeDialog = true }) {
                // Same label/value sizes and start inset as the Average Speed field below, so the two rows line up.
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.starting),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp),
                    )
                    Text(
                        if (viewModel.startsNow) {
                            stringResource(R.string.starting_now)
                        } else {
                            viewModel.plannedDate.atTime(viewModel.plannedHour, viewModel.plannedMinute).format(plannedLabelFormatter)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(start = 16.dp, top = 2.dp),
                    )
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                }
                IconButton(onClick = { showTimeDialog = true }) {
                    Icon(Icons.Filled.Schedule, contentDescription = stringResource(R.string.starting))
                }
            }

            val trek = viewModel.activity == ActivityType.TREK
            val realistic = viewModel.pacingMode.isRealistic
            FormRow(icon = if (trek) Icons.Filled.Hiking else Icons.Filled.Speed) {
                TextField(
                    value = speedText,
                    onValueChange = { text ->
                        speedText = text
                        text.replace(',', '.').toDoubleOrNull()?.let { viewModel.avgSpeedKmh = it.coerceIn(1.0, 80.0) }
                    },
                    label = { Text(stringResource(if (trek && realistic) R.string.flat_speed else R.string.average_speed)) },
                    suffix = { Text(stringResource(R.string.km_h)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f),
                )
                // Keeps the field as wide as the Starting row above, which ends with its clock button.
                Spacer(Modifier.size(48.dp))
            }

            // The speed model, every choice always visible: constant average, terrain-aware pacing (gradient
            // physics on a ride, CAI signpost times on a trek) and, on a ride, terrain-aware pacing with the forecast wind.
            if (trek) {
                SegmentedChoice(
                    options = listOf(
                        stringResource(R.string.pacing_constant) to Icons.Filled.HorizontalRule,
                        stringResource(R.string.pacing_cai) to Icons.Filled.Terrain,
                    ),
                    selected = viewModel.pacingMode.ordinal.coerceAtMost(1),
                    onSelect = { viewModel.pacingMode = PacingMode.entries[it] },
                    modifier = Modifier.padding(start = 60.dp, end = 64.dp, top = 10.dp),
                )
            } else {
                SegmentedChoice(
                    options = listOf(
                        stringResource(R.string.pacing_constant) to Icons.Filled.HorizontalRule,
                        stringResource(R.string.pacing_realistic) to Icons.Filled.Terrain,
                        stringResource(R.string.pacing_realistic_wind) to Icons.Filled.Terrain,
                    ),
                    selected = viewModel.pacingMode.ordinal,
                    onSelect = { viewModel.pacingMode = PacingMode.entries[it] },
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
                    weights = listOf(1f, 1f, 1.6f),
                    showIcons = false,
                )
            }
            Text(
                when {
                    !realistic -> stringResource(R.string.pacing_hint_constant)
                    trek -> stringResource(R.string.pacing_hint_cai, formatSpeedInput(viewModel.caiProfile.flatKmh))
                    viewModel.pacingMode == PacingMode.REALISTIC_WIND ->
                        stringResource(R.string.pacing_hint_wind, "${(viewModel.windHeightFactor * 100).roundToInt()}%")
                    else -> stringResource(R.string.pacing_hint_realistic, formatSpeedInput(viewModel.maxDescentSpeedKmh))
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = if (trek) 60.dp else 16.dp, end = 16.dp, top = 4.dp),
            )

            Row(
                Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = {
                        onForecast(viewModel.computeStartInstant().epochSecond, viewModel.avgSpeedKmh, viewModel.pacingMode, viewModel.cropRangeM())
                    },
                    shape = RoundedCornerShape(4.dp),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                ) {
                    Text(stringResource(R.string.forecast_route).uppercase(), style = MaterialTheme.typography.titleMedium)
                }
                OutlinedButton(
                    onClick = { onLive(viewModel.avgSpeedKmh, viewModel.pacingMode, viewModel.cropRangeM()) },
                    shape = RoundedCornerShape(4.dp),
                ) {
                    Text(stringResource(R.string.live_route), style = MaterialTheme.typography.titleMedium)
                }
            }
            // Open the route in the builder: the route itself (saving replaces it) or a copy (saving makes a new route).
            Row(
                Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(onClick = onEdit, shape = RoundedCornerShape(4.dp)) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.route_edit), modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(onClick = onDuplicate, shape = RoundedCornerShape(4.dp)) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.route_duplicate), modifier = Modifier.padding(start = 8.dp))
                }
            }

            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .clip(RoundedCornerShape(8.dp)),
            ) {
                RouteMapView(
                    points = route.points,
                    mapStyle = viewModel.mapStyle,
                    cropRangeM = viewModel.cropRangeM(),
                    stops = route.stops,
                    onStopTap = { index -> editingStop = StopEdit(index, route.stops[index]) },
                    onMapTap = if (placingStop) {
                        { lat, lon ->
                            placingStop = false
                            val candidates = viewModel.stopCandidates(lat, lon)
                            when (candidates.size) {
                                0 -> Unit
                                1 -> editingStop = StopEdit(null, candidates.first())
                                else -> choosingPass = candidates
                            }
                        }
                    } else {
                        null
                    },
                    height = Dp.Unspecified,
                )
                Surface(
                    onClick = { placingStop = !placingStop },
                    shape = CircleShape,
                    color = if (placingStop) MaterialTheme.colorScheme.primary else Color.White,
                    shadowElevation = 3.dp,
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                ) {
                    Icon(
                        Icons.Filled.AddLocationAlt,
                        contentDescription = stringResource(R.string.add_stop),
                        tint = if (placingStop) Color.White else Color.DarkGray,
                        modifier = Modifier.padding(8.dp).size(20.dp),
                    )
                }
                if (placingStop) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color.Black.copy(alpha = 0.7f),
                        modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                    ) {
                        Text(
                            stringResource(R.string.stop_place_hint),
                            color = Color.White,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }
            }

            // Two thumbs: drag them in to cut the start and/or the end off the ride.
            RangeSlider(
                value = viewModel.cropRange,
                onValueChange = { range ->
                    if (range.endInclusive - range.start >= MIN_CROP_FRACTION) viewModel.cropRange = range
                },
                valueRange = 0f..1f,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        if (showTimeDialog) {
            StartTimeDialog(
                initialDate = viewModel.plannedDate,
                horizonDays = viewModel.forecastHorizonDays,
                initialHour = if (viewModel.startsNow) Instant.now().atZone(java.time.ZoneId.systemDefault()).hour else viewModel.plannedHour,
                initialMinute = if (viewModel.startsNow) Instant.now().atZone(java.time.ZoneId.systemDefault()).minute else viewModel.plannedMinute,
                onDismiss = { showTimeDialog = false },
                onNow = {
                    viewModel.useNow()
                    showTimeDialog = false
                },
                onConfirm = { date, hour, minute ->
                    viewModel.setPlannedTime(date, hour, minute)
                    showTimeDialog = false
                },
            )
        }

        choosingPass?.let { passes ->
            val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()) }
            val start = viewModel.computeStartInstant()
            AlertDialog(
                onDismissRequest = { choosingPass = null },
                title = { Text(stringResource(R.string.stop_which_pass)) },
                text = {
                    Column {
                        passes.forEachIndexed { i, stop ->
                            // Rough passing time at the chosen average speed, counting earlier stops.
                            val earlierStopsMin = route.stops.filter { it.distanceM < stop.distanceM }.sumOf { it.durationMin }
                            val ridingS = if (viewModel.avgSpeedKmh > 0) stop.distanceM / 1000 / viewModel.avgSpeedKmh * 3600 else 0.0
                            val eta = start.plusSeconds(ridingS.toLong() + earlierStopsMin * 60L)
                            TextButton(
                                onClick = {
                                    choosingPass = null
                                    editingStop = StopEdit(null, stop)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    stringResource(R.string.stop_pass_option, i + 1, stop.distanceM / 1000, timeFormatter.format(eta)),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { choosingPass = null }) { Text(stringResource(R.string.cancel)) } },
            )
        }

        editingStop?.let { edit ->
            StopDurationDialog(
                initialMinutes = edit.stop.durationMin,
                onConfirm = { minutes ->
                    if (edit.index == null) viewModel.addStop(edit.stop.copy(durationMin = minutes)) else viewModel.updateStop(edit.index, minutes)
                    editingStop = null
                },
                onDelete = {
                    edit.index?.let(viewModel::removeStop)
                    editingStop = null
                },
                onDismiss = { editingStop = null },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartTimeDialog(
    initialDate: LocalDate,
    horizonDays: Long,
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onNow: () -> Unit,
    onConfirm: (date: LocalDate, hour: Int, minute: Int) -> Unit,
) {
    var date by remember { mutableStateOf(initialDate) }
    var showDatePicker by remember { mutableStateOf(false) }
    val timeState = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = true)
    val dateFieldFormatter = remember { DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.getDefault()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.starting)) },
        text = {
            // Scrolls: the dial is tall and would otherwise be cut off on a small screen or in landscape.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedButton(onClick = { showDatePicker = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(date.format(dateFieldFormatter))
                }
                Box(Modifier.padding(top = 12.dp)) { TimePicker(state = timeState) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(date, timeState.hour, timeState.minute) }) { Text("OK") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onNow) { Text(stringResource(R.string.starting_now)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )

    if (showDatePicker) {
        HorizonDatePickerDialog(
            initialDate = date,
            horizonDays = horizonDays,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                date = it
                showDatePicker = false
            },
        )
    }
}

/** A stop being created ([index] null) or edited (its position in the route's stop list). */
private data class StopEdit(val index: Int?, val stop: RouteStop)

private const val STOP_STEP_MIN = 5
private const val STOP_MAX_MIN = 600

/** Duration picker in the style of Epic Ride Weather: up / down in 5-minute steps, X removes the stop. */
@Composable
private fun StopDurationDialog(
    initialMinutes: Int,
    onConfirm: (Int) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var minutes by remember { mutableStateOf(initialMinutes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.stop_duration)) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
                    Text(formatStopDuration(minutes), style = MaterialTheme.typography.headlineMedium)
                }
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilledTonalButton(onClick = { minutes = (minutes + STOP_STEP_MIN).coerceAtMost(STOP_MAX_MIN) }) {
                        Icon(Icons.Filled.ArrowUpward, contentDescription = stringResource(R.string.stop_longer))
                    }
                    FilledTonalButton(onClick = { minutes = (minutes - STOP_STEP_MIN).coerceAtLeast(STOP_STEP_MIN) }) {
                        Icon(Icons.Filled.ArrowDownward, contentDescription = stringResource(R.string.stop_shorter))
                    }
                    FilledTonalButton(onClick = onDelete) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.remove_stop))
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(minutes) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun formatStopDuration(minutes: Int): String =
    if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"

private const val MIN_CROP_FRACTION = 0.02f

/** A form line in the style of the original app: leading icon, content, optional trailing action. */
@Composable
private fun FormRow(icon: ImageVector, onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 16.dp).size(28.dp))
        content()
    }
}

/** Two or more mutually exclusive choices in one pill, the selected one filled with the accent colour. */
@Composable
internal fun SegmentedChoice(
    options: List<Pair<String, ImageVector>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** Relative width of each segment (equal when null): a long label gets more room. */
    weights: List<Float>? = null,
    showIcons: Boolean = true,
) {
    val outline = MaterialTheme.colorScheme.outline
    Row(
        modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(50))
            .border(1.dp, outline, RoundedCornerShape(50)),
    ) {
        options.forEachIndexed { index, (label, icon) ->
            if (index > 0) Box(Modifier.fillMaxHeight().width(1.dp).background(outline))
            val isSelected = index == selected
            val content = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            Row(
                Modifier
                    .weight(weights?.getOrNull(index) ?: 1f)
                    .fillMaxHeight()
                    .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                    .selectable(selected = isSelected, role = Role.Tab, onClick = { onSelect(index) }),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (showIcons) Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
                Text(
                    label,
                    color = content,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    modifier = Modifier.padding(start = if (showIcons) 6.dp else 0.dp),
                )
            }
        }
    }
}

/**
 * "133 km  3,371 m↑  25 km/h  5h 19m (incl. 40m stops)": the ridden part, its average moving speed and its
 * time including the planned stops, which are named when there are any.
 */
private fun rideSummary(route: Route, movingSeconds: Long): String {
    val avgKmh = if (movingSeconds > 0) route.distanceKm / (movingSeconds / 3600.0) else 0.0
    val stopsMin = route.stops.sumOf { it.durationMin }
    val totalMin = (movingSeconds / 60.0 + stopsMin).roundToInt()
    val base = "%.0f km  %,d m↑  %s km/h  %dh %02dm".format(
        route.distanceKm, route.elevationGainM.roundToInt(), formatSpeedInput(avgKmh), totalMin / 60, totalMin % 60,
    )
    return if (stopsMin > 0) "$base (incl. ${formatStopDuration(stopsMin)} stops)" else base
}

private fun formatSpeedInput(speedKmh: Double): String =
    if (speedKmh == speedKmh.toInt().toDouble()) speedKmh.toInt().toString() else "%.1f".format(speedKmh)
