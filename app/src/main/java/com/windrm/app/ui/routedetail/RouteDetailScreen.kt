package com.windrm.app.ui.routedetail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import com.windrm.app.domain.cropped
import com.windrm.app.model.Route
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddLocationAlt
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.windrm.app.R
import com.windrm.app.domain.PacingMode
import com.windrm.app.model.RouteStop
import com.windrm.app.ui.routes.FavoriteButton
import com.windrm.app.ui.components.RouteMapView
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteDetailScreen(
    viewModel: RouteDetailViewModel,
    onBack: () -> Unit,
    onForecast: (startEpochS: Long, speedKmh: Double, pacing: PacingMode, cropRangeM: ClosedFloatingPointRange<Double>) -> Unit,
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
                            Text(rideSummary(r, viewModel.avgSpeedKmh), style = MaterialTheme.typography.bodyMedium, maxLines = 1)
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
        val plannedLabelFormatter = remember { DateTimeFormatter.ofPattern("MMM d, yyyy 'at' HH:mm", Locale.getDefault()) }
        var speedText by remember { mutableStateOf(formatSpeedInput(viewModel.avgSpeedKmh)) }
        // The VM may adopt the recorded pace once the route loads; mirror it unless the user is typing.
        LaunchedEffect(viewModel.avgSpeedKmh) {
            if (speedText.replace(',', '.').toDoubleOrNull() != viewModel.avgSpeedKmh) speedText = formatSpeedInput(viewModel.avgSpeedKmh)
        }

        Column(Modifier.fillMaxSize().padding(padding)) {
            FormRow(icon = Icons.Filled.Event, onClick = { showTimeDialog = true }) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.starting), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        if (viewModel.startsNow) {
                            stringResource(R.string.starting_now)
                        } else {
                            viewModel.plannedDate.atTime(viewModel.plannedHour, viewModel.plannedMinute).format(plannedLabelFormatter)
                        },
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                }
                IconButton(onClick = { showTimeDialog = true }) {
                    Icon(Icons.Filled.Schedule, contentDescription = stringResource(R.string.starting))
                }
            }

            FormRow(icon = Icons.Filled.Speed) {
                TextField(
                    value = speedText,
                    onValueChange = { text ->
                        speedText = text
                        text.replace(',', '.').toDoubleOrNull()?.let { viewModel.avgSpeedKmh = it.coerceIn(1.0, 80.0) }
                    },
                    label = { Text(stringResource(R.string.average_speed)) },
                    suffix = {
                        val mode = stringResource(if (viewModel.pacingMode == PacingMode.REALISTIC) R.string.pacing_realistic else R.string.pacing_constant)
                        Text("${stringResource(R.string.km_h)} · $mode")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineSmall,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                    ),
                    modifier = Modifier.weight(1f),
                )
                // Swaps the speed model: constant average vs. realistic (gradient-aware) pacing.
                IconButton(onClick = {
                    viewModel.pacingMode = if (viewModel.pacingMode == PacingMode.CONSTANT) PacingMode.REALISTIC else PacingMode.CONSTANT
                }) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = stringResource(R.string.pacing_mode))
                }
            }

            Button(
                onClick = {
                    onForecast(viewModel.computeStartInstant().epochSecond, viewModel.avgSpeedKmh, viewModel.pacingMode, viewModel.cropRangeM())
                },
                shape = RoundedCornerShape(4.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(vertical = 12.dp),
            ) {
                Text(stringResource(R.string.forecast_route).uppercase(), style = MaterialTheme.typography.titleMedium)
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
                            viewModel.stopAt(lat, lon)?.let { editingStop = StopEdit(null, it) }
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
            Column {
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
        val today = remember { LocalDate.now() }
        val maxDate = remember(today, horizonDays) { today.plusDays(horizonDays) }
        val datePickerState = remember {
            DatePickerState(
                // The picker takes its first weekday from the locale; weeks must start on Monday.
                locale = mondayFirstLocale(),
                initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                // Beyond this horizon Open-Meteo's high-resolution regional models fall back to lower
                // detail, and reliability drops accordingly -- see WeatherRepository's forecastRoute.
                selectableDates = object : SelectableDates {
                    override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                        val candidate = Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate()
                        return !candidate.isBefore(today) && !candidate.isAfter(maxDate)
                    }
                },
            )
        }
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let {
                        date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text(stringResource(R.string.cancel)) }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/** The device locale if its weeks already start on Monday, otherwise the same language with a Monday-first region. */
private fun mondayFirstLocale(): Locale {
    val default = Locale.getDefault()
    if (WeekFields.of(default).firstDayOfWeek == DayOfWeek.MONDAY) return default
    // Week start is regional data, so borrowing a Monday-first region keeps the language's month and day names.
    return Locale(default.language, "GB")
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

/** "133 km  3,371 m↑  25 km/h  5h 19m": the ridden part, its time including planned stops. */
private fun rideSummary(route: Route, avgSpeedKmh: Double): String {
    val ridingMin = if (avgSpeedKmh > 0) route.distanceKm / avgSpeedKmh * 60 else 0.0
    val totalMin = (ridingMin + route.stops.sumOf { it.durationMin }).roundToInt()
    return "%.0f km  %,d m↑  %s km/h  %dh %02dm".format(
        route.distanceKm, route.elevationGainM.roundToInt(), formatSpeedInput(avgSpeedKmh), totalMin / 60, totalMin % 60,
    )
}

private fun formatSpeedInput(speedKmh: Double): String =
    if (speedKmh == speedKmh.toInt().toDouble()) speedKmh.toInt().toString() else "%.1f".format(speedKmh)
