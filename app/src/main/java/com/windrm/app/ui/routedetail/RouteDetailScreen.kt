package com.windrm.app.ui.routedetail

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
                            "+%dg %02d:%02d".format(viewModel.plannedDaysOffset, viewModel.plannedHour, viewModel.plannedMinute)
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
                initialDaysOffset = viewModel.plannedDaysOffset,
                initialHour = if (viewModel.startsNow) Instant.now().atZone(java.time.ZoneId.systemDefault()).hour else viewModel.plannedHour,
                initialMinute = if (viewModel.startsNow) Instant.now().atZone(java.time.ZoneId.systemDefault()).minute else viewModel.plannedMinute,
                onDismiss = { showTimeDialog = false },
                onNow = {
                    viewModel.useNow()
                    showTimeDialog = false
                },
                onConfirm = { daysOffset, hour, minute ->
                    viewModel.setPlannedTime(daysOffset, hour, minute)
                    showTimeDialog = false
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartTimeDialog(
    initialDaysOffset: Int,
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onNow: () -> Unit,
    onConfirm: (daysOffset: Int, hour: Int, minute: Int) -> Unit,
) {
    var daysOffset by remember { mutableStateOf(initialDaysOffset) }
    val timeState = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.starting)) },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        stringResource(R.string.today) to 0,
                        stringResource(R.string.tomorrow) to 1,
                        stringResource(R.string.plus_two_days) to 2,
                    ).forEach { (label, offset) ->
                        FilterChip(selected = daysOffset == offset, onClick = { daysOffset = offset }, label = { Text(label) })
                    }
                }
                Box(Modifier.padding(top = 12.dp)) { TimePicker(state = timeState) }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(daysOffset, timeState.hour, timeState.minute) }) { Text("OK") }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onNow) { Text(stringResource(R.string.starting_now)) }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}
