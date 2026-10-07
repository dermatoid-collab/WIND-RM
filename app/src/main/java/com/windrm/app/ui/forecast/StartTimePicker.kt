package com.windrm.app.ui.forecast

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.windrm.app.R
import com.windrm.app.model.WeatherPoint
import com.windrm.app.ui.components.ChartSeries
import com.windrm.app.ui.components.MultiSeriesChart
import com.windrm.app.ui.theme.PrecipColor
import com.windrm.app.ui.theme.TempColor
import com.windrm.app.ui.theme.WindColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

// The chart covers the hours anyone plausibly starts a ride in.
private const val WINDOW_START_MIN = 4 * 60
private const val WINDOW_END_MIN = 22 * 60
private const val STEP_MIN = 15

/**
 * Pick a new start from inside the forecast, like the original app: page through the days, then
 * tap or drag on the chart of the weather at the route start to choose the time.
 */
@Composable
fun StartTimePickerDialog(
    initialStart: Instant,
    hourlyWeather: List<WeatherPoint>?,
    horizonDays: Int,
    onDismiss: () -> Unit,
    onConfirm: (Instant) -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    val today = remember { LocalDate.now(zone) }
    val initial = remember(initialStart) { initialStart.atZone(zone) }
    var date by remember { mutableStateOf(initial.toLocalDate().coerceIn(today, today.plusDays(horizonDays.toLong()))) }
    var minuteOfDay by remember {
        mutableIntStateOf((initial.hour * 60 + initial.minute).coerceIn(WINDOW_START_MIN, WINDOW_END_MIN))
    }
    val dayFormatter = remember { DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()) }
    val windowMin = (WINDOW_END_MIN - WINDOW_START_MIN).toFloat()
    val timeText = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)

    val dayHours = remember(hourlyWeather, date) {
        hourlyWeather.orEmpty().filter {
            val t = it.time.atZone(zone)
            t.toLocalDate() == date && t.hour * 60 in WINDOW_START_MIN..WINDOW_END_MIN
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth(0.95f)) {
            Column {
                Surface(color = MaterialTheme.colorScheme.primary, contentColor = Color.White) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel), color = Color.White) }
                        Text(
                            stringResource(R.string.start_time),
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = {
                            onConfirm(date.atStartOfDay(zone).plusMinutes(minuteOfDay.toLong()).toInstant())
                        }) { Text("OK", color = Color.White) }
                    }
                }

                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { date = date.minusDays(1) }, enabled = date > today) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
                    }
                    val dayLabel = when (date) {
                        today -> stringResource(R.string.today)
                        today.plusDays(1) -> stringResource(R.string.tomorrow)
                        else -> date.format(dayFormatter)
                    }
                    Text(
                        stringResource(R.string.day_at_time, dayLabel, timeText),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = { date = date.plusDays(1) }, enabled = date < today.plusDays(horizonDays.toLong())) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                    }
                }

                Text(
                    stringResource(R.string.start_time_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )

                Box(Modifier.fillMaxWidth().padding(horizontal = 8.dp).padding(bottom = 16.dp)) {
                    when {
                        hourlyWeather == null -> Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        dayHours.size < 2 -> Text(
                            stringResource(R.string.start_time_no_data),
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            textAlign = TextAlign.Center,
                        )
                        else -> MultiSeriesChart(
                            title = "",
                            xLabels = (0..6).map { "%02d:00".format((WINDOW_START_MIN / 60) + it * 3) },
                            series = listOf(
                                ChartSeries("Temperature (°C)", TempColor, dayHours.map { it.temperatureC.toFloat() }, tooltipLabel = "Temp.", unit = "°C"),
                                ChartSeries(
                                    "Precipitation (%)", PrecipColor, dayHours.map { it.precipitationProbabilityPct.toFloat() },
                                    filled = false, tooltipLabel = "Prob.", unit = "%", decimals = 0,
                                ),
                                ChartSeries(
                                    "Wind (km/h)", WindColor, dayHours.map { it.windSpeedKmh.toFloat() },
                                    filled = false, tooltipLabel = "Wind", unit = " km/h", decimals = 0,
                                ),
                            ),
                            // The chosen time stays marked after the finger lifts: it is the selection.
                            scrubFraction = (minuteOfDay - WINDOW_START_MIN) / windowMin,
                            onScrub = { f ->
                                val raw = WINDOW_START_MIN + f * windowMin
                                minuteOfDay = ((raw / STEP_MIN).roundToInt() * STEP_MIN).coerceIn(WINDOW_START_MIN, WINDOW_END_MIN)
                            },
                            onScrubEnd = {},
                            scrubLabel = timeText,
                            daylightBar = dayHours.map { if (it.isDay) 1f else 0f },
                            chartHeight = 180.dp,
                        )
                    }
                }
            }
        }
    }
}

private fun LocalDate.coerceIn(min: LocalDate, max: LocalDate): LocalDate = when {
    isBefore(min) -> min
    isAfter(max) -> max
    else -> this
}
