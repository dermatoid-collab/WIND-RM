package com.windrm.app.ui.forecast

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.windrm.app.R
import com.windrm.app.model.RouteForecastPoint
import com.windrm.app.model.RouteForecastResult
import com.windrm.app.model.aqiLabel
import com.windrm.app.ui.components.AqiDial
import com.windrm.app.ui.components.ChartBand
import com.windrm.app.ui.components.ChartSeries
import com.windrm.app.ui.components.MultiSeriesChart
import com.windrm.app.ui.components.RouteMapView
import com.windrm.app.ui.components.SemiCircularGauge
import com.windrm.app.ui.components.WindArrowPoint
import com.windrm.app.ui.theme.AqiFair
import com.windrm.app.ui.theme.AqiGood
import com.windrm.app.ui.theme.AqiModerate
import com.windrm.app.ui.theme.AqiPoor
import com.windrm.app.ui.theme.AqiSevere
import com.windrm.app.ui.theme.AqiVeryPoor
import com.windrm.app.ui.theme.CloudColor
import com.windrm.app.ui.theme.DaylightColor
import com.windrm.app.ui.theme.DewPointColor
import com.windrm.app.ui.theme.FeelsLikeColor
import com.windrm.app.ui.theme.GustColor
import com.windrm.app.ui.theme.HumidityColor
import com.windrm.app.ui.theme.IntensityColor
import com.windrm.app.ui.theme.PrecipColor
import com.windrm.app.ui.theme.TempColor
import com.windrm.app.ui.theme.UvColor
import com.windrm.app.ui.theme.WindColor
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForecastScreen(viewModel: ForecastViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val state = viewModel.uiState

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(routeTitle(state))
                        subTitle(state)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                    }
                },
                actions = {
                    if (state is ForecastUiState.Success) {
                        IconButton(onClick = { shareForecast(context, state.result) }) {
                            Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.share), tint = Color.White)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                is ForecastUiState.Loading -> LoadingContent()
                is ForecastUiState.Error -> ErrorContent(state.message, onRetry = viewModel::load)
                is ForecastUiState.Success -> ForecastContent(
                    result = state.result,
                    scrubIndex = viewModel.scrubIndex,
                    onScrub = { viewModel.scrubIndex = it },
                )
            }
        }
    }
}

@Composable
private fun routeTitle(state: ForecastUiState): String =
    (state as? ForecastUiState.Success)?.result?.route?.name ?: ""

@Composable
private fun subTitle(state: ForecastUiState): String? {
    val result = (state as? ForecastUiState.Success)?.result ?: return null
    val formatter = DateTimeFormatter.ofPattern("d MMM yyyy 'alle' HH:mm").withZone(ZoneId.systemDefault())
    return formatter.format(result.startTime)
}

@Composable
private fun LoadingContent() {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CircularProgressIndicator()
        Text(stringResource(R.string.loading_forecast), modifier = Modifier.padding(top = 16.dp))
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.forecast_error))
        Text(message, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 8.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

@Composable
private fun ForecastContent(result: RouteForecastResult, scrubIndex: Int, onScrub: (Int) -> Unit) {
    val points = result.points
    if (points.isEmpty()) {
        ErrorContent(stringResource(R.string.forecast_error), onRetry = {})
        return
    }
    val current = points[scrubIndex.coerceIn(0, points.lastIndex)]
    val timeLabels = remember(points) { timeAxisLabels(points) }
    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        GaugesRow(current)

        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        SectionBox {
            MultiSeriesChart(
                title = stringResource(R.string.temperature),
                xLabels = timeLabels,
                series = listOf(
                    ChartSeries("Temperature (°C)", TempColor, points.map { it.weather.temperatureC.toFloat() }),
                    ChartSeries("Feels Like (°C)", FeelsLikeColor, points.map { it.weather.feelsLikeC.toFloat() }),
                ),
            )
        }

        SectionBox {
            MultiSeriesChart(
                title = stringResource(R.string.precipitation_and_cloud_cover),
                xLabels = timeLabels,
                yRangeOverride = 0f..100f,
                series = listOf(
                    ChartSeries("Probability (%)", PrecipColor, points.map { it.weather.precipitationProbabilityPct.toFloat() }, filled = false),
                    ChartSeries("Intensity", IntensityColor, points.map { it.weather.precipitationMm.toFloat() }, filled = false),
                    ChartSeries("Cloud Cover (%)", CloudColor, points.map { it.weather.cloudCoverPct.toFloat() }),
                ),
            )
        }

        SectionBox {
            MultiSeriesChart(
                title = stringResource(R.string.wind),
                xLabels = timeLabels,
                yUnit = " km/h",
                series = listOf(
                    ChartSeries("Wind (km/h)", WindColor, points.map { it.weather.windSpeedKmh.toFloat() }, filled = false),
                    ChartSeries("Wind Gust (km/h)", GustColor, points.map { it.weather.windGustKmh.toFloat() }),
                ),
            )
        }

        SectionBox {
            Text(stringResource(R.string.wind_direction), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 8.dp))
            RouteMapView(
                points = points.map { it.point },
                windArrows = points.map { WindArrowPoint(it.point, it.weather.windDirectionDeg) },
            )
        }

        SectionBox {
            MultiSeriesChart(
                title = stringResource(R.string.elevation),
                xLabels = timeLabels,
                yUnit = " m",
                series = listOf(
                    ChartSeries("Elevazione (m)", TempColor, points.map { (it.point.eleM ?: 0.0).toFloat() }),
                ),
            )
            Text(
                "%s: %.0f m↑    %s: %.1f km    %s: %.1f km/h".format(
                    "Dislivello", result.route.elevationGainM,
                    "Distanza", result.route.distanceKm,
                    "Velocità", result.avgSpeedKmh,
                ),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        SectionBox {
            MultiSeriesChart(
                title = stringResource(R.string.daylight_and_uv),
                xLabels = timeLabels,
                yRangeOverride = 0f..12f,
                series = listOf(
                    ChartSeries("Daylight", DaylightColor, points.map { if (it.weather.isDay) 12f else 0f }),
                    ChartSeries("UV Index", UvColor, points.map { it.weather.uvIndex.toFloat() }),
                ),
            )
            DaylightSummary(result, timeFmt)
        }

        if (points.any { it.airQuality != null }) {
            SectionBox {
                Text(stringResource(R.string.air_quality), style = MaterialTheme.typography.headlineSmall)
                val peak = result.peakAqi
                if (peak != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 8.dp)) {
                        AqiDial(
                            value = peak.europeanAqi,
                            maxValue = 120.0,
                            label = stringResource(R.string.peak_aqi),
                            subLabel = aqiLabel(peak.europeanAqi),
                            color = aqiColor(peak.europeanAqi),
                        )
                        Column(Modifier.padding(start = 16.dp)) {
                            Text(stringResource(R.string.main_pollutant), style = MaterialTheme.typography.labelMedium)
                            Text(result.mainPollutant ?: "-", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                MultiSeriesChart(
                    title = "AQI",
                    xLabels = timeLabels,
                    yRangeOverride = 0f..100f,
                    bands = listOf(
                        ChartBand(0f..20f, AqiGood),
                        ChartBand(20f..40f, AqiFair),
                        ChartBand(40f..60f, AqiModerate),
                        ChartBand(60f..80f, AqiPoor),
                        ChartBand(80f..100f, AqiVeryPoor),
                    ),
                    series = listOf(
                        ChartSeries("European AQI", AqiSevere, points.map { (it.airQuality?.europeanAqi ?: 0.0).toFloat() }),
                    ),
                )
            }
        }

        SectionBox {
            MultiSeriesChart(
                title = stringResource(R.string.humidity_and_dew_point),
                xLabels = timeLabels,
                series = listOf(
                    ChartSeries("Humidity (%)", HumidityColor, points.map { it.weather.humidityPct.toFloat() }),
                    ChartSeries("Dew Point (°C)", DewPointColor, points.map { it.weather.dewPointC.toFloat() }),
                ),
            )
        }

        SectionBox {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(timeFmt.format(points.first().arrivalTime), style = MaterialTheme.typography.bodySmall)
                Text(timeFmt.format(points.last().arrivalTime), style = MaterialTheme.typography.bodySmall)
            }
            Slider(
                value = scrubIndex.toFloat(),
                onValueChange = { onScrub(it.roundToInt()) },
                valueRange = 0f..(points.lastIndex).toFloat().coerceAtLeast(0f),
                steps = (points.size - 2).coerceAtLeast(0),
            )
        }
    }
}

@Composable
private fun GaugesRow(current: RouteForecastPoint) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        SemiCircularGauge(
            value = current.weather.temperatureC,
            minValue = -10.0,
            maxValue = 40.0,
            label = stringResource(R.string.temperature),
            valueText = "${current.weather.temperatureC.roundToInt()}°C",
            color = TempColor,
        )
        SemiCircularGauge(
            value = current.weather.precipitationProbabilityPct,
            minValue = 0.0,
            maxValue = 100.0,
            label = stringResource(R.string.precipitation),
            valueText = "${current.weather.precipitationProbabilityPct.roundToInt()}%",
            color = PrecipColor,
        )
        SemiCircularGauge(
            value = current.weather.windSpeedKmh,
            minValue = 0.0,
            maxValue = 80.0,
            label = stringResource(R.string.wind),
            valueText = "${current.weather.windSpeedKmh.roundToInt()} km/h",
            color = WindColor,
        )
    }
}

@Composable
private fun DaylightSummary(result: RouteForecastResult, formatter: DateTimeFormatter) {
    val d = result.daylight
    Column(Modifier.padding(top = 8.dp)) {
        d.sunrise?.let { Text("${stringResource(R.string.sunrise)}: ${formatter.format(it)}", style = MaterialTheme.typography.bodyMedium) }
        d.sunset?.let { Text("${stringResource(R.string.sunset)}: ${formatter.format(it)}", style = MaterialTheme.typography.bodyMedium) }
        d.civilSunrise?.let { Text("${stringResource(R.string.civil_sunrise)}: ${formatter.format(it)}", style = MaterialTheme.typography.bodySmall) }
        d.civilSunset?.let { Text("${stringResource(R.string.civil_sunset)}: ${formatter.format(it)}", style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
private fun SectionBox(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        content()
    }
}

private fun aqiColor(aqi: Double): Color = when {
    aqi <= 20 -> AqiGood
    aqi <= 40 -> AqiFair
    aqi <= 60 -> AqiModerate
    aqi <= 80 -> AqiPoor
    aqi <= 100 -> AqiVeryPoor
    else -> AqiSevere
}

private fun timeAxisLabels(points: List<RouteForecastPoint>, count: Int = 6): List<String> {
    if (points.isEmpty()) return emptyList()
    val formatter = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault())
    if (points.size <= count) return points.map { formatter.format(it.arrivalTime) }
    val step = (points.size - 1).toDouble() / (count - 1)
    return (0 until count).map { i ->
        val index = (i * step).roundToInt().coerceIn(0, points.lastIndex)
        formatter.format(points[index].arrivalTime)
    }
}

private fun shareForecast(context: android.content.Context, result: RouteForecastResult) {
    val formatter = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm").withZone(ZoneId.systemDefault())
    val first = result.points.firstOrNull()?.weather
    val text = buildString {
        appendLine("WIND-RM · ${result.route.name}")
        appendLine("Partenza: ${formatter.format(result.startTime)}")
        appendLine("%.1f km · %.0f m↑".format(result.route.distanceKm, result.route.elevationGainM))
        if (first != null) {
            appendLine("Al via: ${first.temperatureC.roundToInt()}°C, vento ${first.windSpeedKmh.roundToInt()} km/h")
        }
        result.peakAqi?.let { appendLine("AQI di picco: ${it.europeanAqi.roundToInt()} (${aqiLabel(it.europeanAqi)})") }
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.share)))
}
