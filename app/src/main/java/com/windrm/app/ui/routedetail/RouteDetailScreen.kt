package com.windrm.app.ui.routedetail

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
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
import com.windrm.app.ui.components.RouteMapView
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteDetailScreen(
    viewModel: RouteDetailViewModel,
    onBack: () -> Unit,
    onForecast: (startEpochS: Long, speedKmh: Double) -> Unit,
) {
    val route = viewModel.route

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(route?.name ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        if (route == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }

        var showTimeDialog by remember { mutableStateOf(false) }
        val plannedLabelFormatter = remember { DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.getDefault()) }

        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            RouteMapView(points = route.points)

            Column(Modifier.padding(16.dp)) {
                Text("%.1f km · %.0f m↑".format(route.distanceKm, route.elevationGainM), style = MaterialTheme.typography.bodyMedium)

                Text(
                    stringResource(R.string.starting),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 20.dp),
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (viewModel.startsNow) {
                            stringResource(R.string.starting_now)
                        } else {
                            viewModel.plannedDate.atTime(viewModel.plannedHour, viewModel.plannedMinute).format(plannedLabelFormatter)
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { showTimeDialog = true }) {
                        Icon(Icons.Filled.Schedule, contentDescription = null)
                    }
                }

                Text(
                    stringResource(R.string.average_speed),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 20.dp),
                )
                OutlinedTextField(
                    value = if (viewModel.avgSpeedKmh == viewModel.avgSpeedKmh.toInt().toDouble()) {
                        viewModel.avgSpeedKmh.toInt().toString()
                    } else {
                        "%.1f".format(viewModel.avgSpeedKmh)
                    },
                    onValueChange = { text ->
                        text.toDoubleOrNull()?.let { viewModel.avgSpeedKmh = it.coerceIn(1.0, 80.0) }
                    },
                    suffix = { Text(stringResource(R.string.km_h)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Button(
                    onClick = { onForecast(viewModel.computeStartInstant().epochSecond, viewModel.avgSpeedKmh) },
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                ) {
                    Text(stringResource(R.string.forecast_route))
                }
            }
        }

        if (showTimeDialog) {
            StartTimeDialog(
                initialDate = viewModel.plannedDate,
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartTimeDialog(
    initialDate: LocalDate,
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
        val maxDate = remember(today) { today.plusDays(FORECAST_HORIZON_DAYS) }
        val datePickerState = rememberDatePickerState(
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
