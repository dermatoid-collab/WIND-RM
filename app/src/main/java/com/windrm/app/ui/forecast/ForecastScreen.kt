package com.windrm.app.ui.forecast

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.windrm.app.R
import com.windrm.app.ui.components.SettingsGear
import com.windrm.app.domain.PacingMode
import com.windrm.app.domain.RideLevel
import com.windrm.app.domain.RideQuality
import com.windrm.app.domain.RideQualityEvaluator
import com.windrm.app.model.AirQualityPoint
import com.windrm.app.model.DaylightInfo
import com.windrm.app.model.RouteForecastPoint
import com.windrm.app.model.RouteForecastResult
import com.windrm.app.model.RoutePoint
import com.windrm.app.model.aqiLabel
import com.windrm.app.settings.MapStyle
import com.windrm.app.ui.components.AqiDial
import com.windrm.app.ui.components.ChartBand
import com.windrm.app.ui.components.ChartSeries
import com.windrm.app.ui.components.MapMarker
import com.windrm.app.ui.components.MultiSeriesChart
import com.windrm.app.ui.components.RouteMapView
import com.windrm.app.ui.components.SemiCircularGauge
import com.windrm.app.ui.components.WindArrowPoint
import com.windrm.app.ui.components.WindSpeedLegend
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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForecastScreen(viewModel: ForecastViewModel, onBack: () -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val state = viewModel.uiState
    var showStartPicker by remember { mutableStateOf(false) }
    var showShareChoice by remember { mutableStateOf(false) }
    var showQuality by remember { mutableStateOf(false) }
    val quality = (state as? ForecastUiState.Success)?.result?.let { remember(it, viewModel.rideThresholds) { RideQualityEvaluator.evaluate(it, viewModel.rideThresholds) } }
    // The pinned map and the scrolling content are recorded separately so the share export can
    // stitch the whole page together, including the parts scrolled out of view.
    val mapLayer = rememberGraphicsLayer()
    val chartsLayer = rememberGraphicsLayer()
    val scope = rememberCoroutineScope()
    val pageBackground = MaterialTheme.colorScheme.background.toArgb()
    val pageText = MaterialTheme.colorScheme.onBackground.toArgb()

    Scaffold(
        topBar = {
            CompactTopBar(
                title = routeTitle(state),
                quality = quality,
                onQualityClick = { showQuality = true },
                onBack = onBack,
                onOpenSettings = onOpenSettings,
                onShare = (state as? ForecastUiState.Success)?.let { { showShareChoice = true } },
                onStartTime = (state as? ForecastUiState.Success)?.let {
                    {
                        viewModel.loadStartPickerWeather()
                        showStartPicker = true
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (state) {
                is ForecastUiState.Loading -> LoadingContent()
                is ForecastUiState.Error -> ErrorContent(state.message, onRetry = viewModel::load)
                is ForecastUiState.Success -> ForecastContent(
                    result = state.result,
                    scrubFraction = viewModel.scrubFraction,
                    onScrub = { viewModel.scrubFraction = it },
                    onScrubEnd = { viewModel.scrubFraction = null },
                    mapStyle = viewModel.mapStyle,
                    mapLayer = mapLayer,
                    chartsLayer = chartsLayer,
                    onTogglePacing = viewModel::togglePacing,
                )
            }
        }
    }

    val success = state as? ForecastUiState.Success
    if (showShareChoice && success != null) {
        fun export(format: ShareFormat) {
            showShareChoice = false
            scope.launch {
                runCatching {
                    val parts = listOf(mapLayer, chartsLayer).map { it.toImageBitmap().asAndroidBitmap() }
                    shareForecastPage(context, success.result, parts, pageBackground, pageText, format)
                }.onFailure {
                    Toast.makeText(context, context.getString(R.string.share_failed), Toast.LENGTH_LONG).show()
                }
            }
        }
        AlertDialog(
            onDismissRequest = { showShareChoice = false },
            title = { Text(stringResource(R.string.share_forecast)) },
            text = {
                Column {
                    TextButton(onClick = { export(ShareFormat.JPG) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.share_as_image), modifier = Modifier.fillMaxWidth())
                    }
                    TextButton(onClick = { export(ShareFormat.PDF) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.share_as_pdf), modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showShareChoice = false }) { Text(stringResource(R.string.cancel)) } },
        )
    }

    if (showQuality && quality != null) {
        RideQualityDialog(quality, onDismiss = { showQuality = false })
    }

    if (showStartPicker) {
        StartTimePickerDialog(
            initialStart = java.time.Instant.ofEpochSecond(viewModel.startEpochS),
            hourlyWeather = viewModel.startPickerWeather,
            horizonDays = viewModel.forecastHorizonDays,
            onDismiss = { showStartPicker = false },
            onConfirm = { start ->
                showStartPicker = false
                viewModel.changeStart(start.epochSecond)
            },
        )
    }
}

/** Single-row orange bar: back, route name, share -- as short as the touch targets allow. */
@Composable
private fun CompactTopBar(
    title: String,
    quality: RideQuality?,
    onQualityClick: () -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onShare: (() -> Unit)?,
    onStartTime: (() -> Unit)?,
) {
    Surface(color = MaterialTheme.colorScheme.primary, contentColor = Color.White) {
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).height(com.windrm.app.ui.components.AppBarHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
            }
            quality?.let { TrafficLight(it.level, onClick = onQualityClick) }
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            onStartTime?.let {
                IconButton(onClick = it) {
                    Icon(Icons.Filled.Schedule, contentDescription = stringResource(R.string.start_time), tint = Color.White)
                }
            }
            onShare?.let {
                IconButton(onClick = it) {
                    Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.share), tint = Color.White)
                }
            }
            SettingsGear(onOpenSettings)
        }
    }
}

private val LIGHT_RED = Color(0xFFD7261E)
private val LIGHT_YELLOW = Color(0xFFF2B400)
private val LIGHT_GREEN = Color(0xFF2E9E44)
private val LIGHT_OFF = Color(0xFF555555)

private fun RideLevel.color(): Color = when (this) {
    RideLevel.RED -> LIGHT_RED
    RideLevel.YELLOW -> LIGHT_YELLOW
    RideLevel.GREEN -> LIGHT_GREEN
}

/** Mini vertical traffic light for the ride quality: three lamps, the current level lit; tap for details. */
@Composable
private fun TrafficLight(level: RideLevel, onClick: () -> Unit) {
    val description = stringResource(R.string.ride_quality)
    Box(
        Modifier
            .size(width = 30.dp, height = com.windrm.app.ui.components.AppBarHeight)
            .clickable(onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .size(width = 14.dp, height = 34.dp)
                .background(Color(0xFF2B2B2B), RoundedCornerShape(4.dp)),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            listOf(RideLevel.RED, RideLevel.YELLOW, RideLevel.GREEN).forEach { lamp ->
                Box(Modifier.size(8.dp).background(if (lamp == level) lamp.color() else LIGHT_OFF, CircleShape))
            }
        }
    }
}

/** What turned the light yellow or red: each metric's worst point, with where and when. */
@Composable
private fun RideQualityDialog(quality: RideQuality, onDismiss: () -> Unit) {
    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()) }
    val levelName = stringResource(
        when (quality.level) {
            RideLevel.GREEN -> R.string.ride_green
            RideLevel.YELLOW -> R.string.ride_yellow
            RideLevel.RED -> R.string.ride_red
        },
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ride_quality_title, levelName)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (quality.issues.isEmpty()) {
                    Text(stringResource(R.string.ride_all_within_limits))
                }
                quality.issues.forEach { issue ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).background(issue.level.color(), CircleShape))
                        Text(issue.text, modifier = Modifier.weight(1f).padding(start = 10.dp))
                        Text(
                            "km %.0f · %s".format(issue.distanceKm, timeFmt.format(issue.time)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}

@Composable
private fun routeTitle(state: ForecastUiState): String =
    (state as? ForecastUiState.Success)?.result?.route?.name ?: ""

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
private fun ForecastContent(
    result: RouteForecastResult,
    scrubFraction: Float?,
    onScrub: (Float) -> Unit,
    onScrubEnd: () -> Unit,
    mapStyle: MapStyle,
    mapLayer: GraphicsLayer,
    chartsLayer: GraphicsLayer,
    onTogglePacing: () -> Unit,
) {
    val points = result.points
    if (points.isEmpty()) {
        ErrorContent(stringResource(R.string.forecast_error), onRetry = {})
        return
    }
    val track = result.route.points
    val totalDistanceM = track.lastOrNull()?.distanceFromStartM ?: 0.0
    val fraction = (scrubFraction ?: 0f).coerceIn(0f, 1f)
    val current = points[(fraction * points.lastIndex).roundToInt()]
    // Interpolated on the full-resolution track, so the map dot glides smoothly instead of
    // jumping between the ~3 km-spaced weather samples.
    val highlightPoint = scrubFraction?.let { pointAtDistance(track, it * totalDistanceM) }
    var heldGauge by remember { mutableStateOf<GaugeMetric?>(null) }
    var aqiView by remember { mutableStateOf(AqiView.OVERALL) }
    // Colours match the gauge rings: min = inner blue ring, max = outer orange ring.
    val gaugeMarkers = heldGauge?.let { metric ->
        listOf(
            MapMarker(points.minBy(metric.value).point, TempColor.toArgb()),
            MapMarker(points.maxBy(metric.value).point, FeelsLikeColor.toArgb()),
        )
    }.orEmpty()
    val timeLabels = remember(points) { timeAxisLabels(points) }
    val timeFmt = remember { DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()) }
    val scrubLabel = "%.1f km, %s".format(fraction * totalDistanceM / 1000.0, timeFmt.format(timeAtFraction(points, fraction)))
    val elevationProfile = remember(track) { track.map { (it.eleM ?: 0.0).toFloat() } }
    val daylightLevels = remember(result) {
        (0 until DAYLIGHT_BAR_SAMPLES).map { k ->
            val f = k.toFloat() / (DAYLIGHT_BAR_SAMPLES - 1)
            val nearest = points[(f * points.lastIndex).roundToInt()]
            lightLevel(timeAtFraction(points, f), result.daylight, fallbackIsDay = nearest.weather.isDay)
        }
    }

    // The map stays pinned on top (so the scrub dot is always visible); everything else scrolls under it.
    Column(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .drawWithContent {
                    mapLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(mapLayer)
                }
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
        ) {
            Box {
                RouteMapView(
                    // The full-resolution track, not the sparse weather-sampling points -- otherwise
                    // the drawn line cuts corners on every curve between samples.
                    points = result.route.points,
                    windArrows = points.map { WindArrowPoint(it.point, it.weather.windDirectionDeg, it.weather.windSpeedKmh) },
                    mapStyle = mapStyle,
                    highlightPoint = highlightPoint,
                    scrubFraction = scrubFraction,
                    markers = gaugeMarkers,
                    stops = result.route.stops,
                    height = 336.dp,
                )
                PacingPill(result, onTogglePacing, Modifier.align(Alignment.TopStart).padding(10.dp))
            }
            WindSpeedLegend()
        }
        HorizontalDivider()
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                // After verticalScroll, so this sees the full-height content, not just the visible part.
                .drawWithContent {
                    chartsLayer.record { this@drawWithContent.drawContent() }
                    drawLayer(chartsLayer)
                },
        ) {
            GaugesRow(points, current, onGaugeHeld = { heldGauge = it })

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            SectionBox {
                MultiSeriesChart(
                    title = stringResource(R.string.temperature),
                    xLabels = timeLabels,
                    scrubFraction = scrubFraction,
                    onScrub = onScrub,
                    onScrubEnd = onScrubEnd,
                    scrubLabel = scrubLabel,
                    series = listOf(
                        ChartSeries("Temperature (°C)", TempColor, points.map { it.weather.temperatureC.toFloat() }, tooltipLabel = "Temp.", unit = "°C"),
                        ChartSeries("Feels Like (°C)", FeelsLikeColor, points.map { it.weather.feelsLikeC.toFloat() }, tooltipLabel = "Feels", unit = "°C"),
                    ),
                )
            }

            SectionBox {
                MultiSeriesChart(
                    title = stringResource(R.string.precipitation_and_cloud_cover),
                    xLabels = timeLabels,
                    yRangeOverride = 0f..100f,
                    scrubFraction = scrubFraction,
                    onScrub = onScrub,
                    onScrubEnd = onScrubEnd,
                    scrubLabel = scrubLabel,
                    series = listOf(
                        ChartSeries("Probability (%)", PrecipColor, points.map { it.weather.precipitationProbabilityPct.toFloat() }, filled = false, tooltipLabel = "Prob.", unit = "%", decimals = 0),
                        ChartSeries("Intensity", IntensityColor, points.map { it.weather.precipitationMm.toFloat() }, filled = false, tooltipLabel = "Intens."),
                        ChartSeries("Cloud Cover (%)", CloudColor, points.map { it.weather.cloudCoverPct.toFloat() }, tooltipLabel = "Clouds", unit = "%", decimals = 0),
                    ),
                )
            }

            SectionBox {
                MultiSeriesChart(
                    title = stringResource(R.string.wind),
                    xLabels = timeLabels,
                    yUnit = " km/h",
                    scrubFraction = scrubFraction,
                    onScrub = onScrub,
                    onScrubEnd = onScrubEnd,
                    scrubLabel = scrubLabel,
                    series = listOf(
                        ChartSeries("Wind (km/h)", WindColor, points.map { it.weather.windSpeedKmh.toFloat() }, filled = false, tooltipLabel = "Wind", unit = " km/h", decimals = 0),
                        ChartSeries("Wind Gust (km/h)", GustColor, points.map { it.weather.windGustKmh.toFloat() }, tooltipLabel = "Gusts", unit = " km/h", decimals = 0),
                    ),
                )
            }

            SectionBox {
                MultiSeriesChart(
                    title = stringResource(R.string.elevation),
                    xLabels = timeLabels,
                    yUnit = " m",
                    scrubFraction = scrubFraction,
                    onScrub = onScrub,
                    onScrubEnd = onScrubEnd,
                    scrubLabel = scrubLabel,
                    series = listOf(
                        // The full-resolution GPX/Strava track, not the ~45 weather samples, which
                        // flatten real climbs/descents into a crude staircase.
                        ChartSeries("Elevation (m)", TempColor, elevationProfile, smooth = false, tooltipLabel = "Elevation", unit = " m", decimals = 0),
                    ),
                )
                Text(
                    "%s: %.0f m↑    %s: %.1f km    %s: %.1f km/h".format(
                        stringResource(R.string.elevation_gain_label), result.route.elevationGainM,
                        stringResource(R.string.distance_label), result.route.distanceKm,
                        stringResource(R.string.speed_label), result.avgSpeedKmh,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            SectionBox {
                MultiSeriesChart(
                    title = stringResource(R.string.daylight_and_uv),
                    xLabels = timeLabels,
                    yRangeOverride = 0f..UV_SCALE_MAX,
                    scrubFraction = scrubFraction,
                    onScrub = onScrub,
                    onScrubEnd = onScrubEnd,
                    scrubLabel = scrubLabel,
                    daylightBar = daylightLevels,
                    series = listOf(
                        // Same light level as the bar above (0 at night, ramping through civil
                        // twilight to full daylight), drawn from 0 up to the top of the 0..12 scale.
                        ChartSeries(
                            "Daylight", DaylightColor, daylightLevels.map { it * UV_SCALE_MAX },
                            filled = false, smooth = false,
                            tooltipLabel = "Daylight", unit = "%", decimals = 0, tooltipFactor = 100f / UV_SCALE_MAX,
                        ),
                        ChartSeries("UV Index", UvColor, points.map { it.weather.uvIndex.toFloat() }, tooltipLabel = "UV index"),
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
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AqiView.entries.forEach { view ->
                            val selected = view == aqiView
                            Surface(
                                onClick = { aqiView = view },
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    if (selected) 2.dp else 1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                ),
                            ) {
                                Text(
                                    view.title,
                                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                )
                            }
                        }
                    }
                    MultiSeriesChart(
                        title = aqiView.title,
                        xLabels = timeLabels,
                        yUnit = aqiView.unit,
                        yRangeOverride = 0f..aqiView.bandTops.last(),
                        scrubFraction = scrubFraction,
                        onScrub = onScrub,
                        onScrubEnd = onScrubEnd,
                        scrubLabel = scrubLabel,
                        bands = aqiView.bandTops.mapIndexed { i, top ->
                            val bottom = if (i == 0) 0f else aqiView.bandTops[i - 1]
                            ChartBand(bottom..top, AQI_BAND_COLORS[i], AQI_BAND_LABELS[i])
                        },
                        series = listOf(
                            ChartSeries(
                                aqiView.title, AQI_LINE_COLOR,
                                points.map { p -> (p.airQuality?.let(aqiView.value) ?: 0.0).toFloat() },
                                filled = false, tooltipLabel = aqiView.title,
                                unit = if (aqiView.unit.isEmpty()) "" else " ${aqiView.unit}", decimals = 0,
                            ),
                        ),
                    )
                }
            }

            SectionBox {
                MultiSeriesChart(
                    title = stringResource(R.string.humidity_and_dew_point),
                    xLabels = timeLabels,
                    scrubFraction = scrubFraction,
                    onScrub = onScrub,
                    onScrubEnd = onScrubEnd,
                    scrubLabel = scrubLabel,
                    series = listOf(
                        ChartSeries("Humidity (%)", HumidityColor, points.map { it.weather.humidityPct.toFloat() }, tooltipLabel = "Humid.", unit = "%", decimals = 0),
                        ChartSeries("Dew Point (°C)", DewPointColor, points.map { it.weather.dewPointC.toFloat() }, tooltipLabel = "Dew", unit = "°C"),
                    ),
                )
            }

            DataSourcesFooter(showAirQuality = points.any { it.airQuality != null })
        }
    }
}

/**
 * The four air-quality views. Band tops follow the European AQI breakpoints (Good, Fair, Moderate,
 * Poor, Very poor) for the overall index and for each pollutant's concentration in µg/m³, as listed
 * in Open-Meteo's air-quality docs; the last band ("Extremely poor") is open-ended and only drawn up
 * to a cap (+20 %) so the chart keeps a scale.
 */
private enum class AqiView(val title: String, val unit: String, val bandTops: List<Float>, val value: (AirQualityPoint) -> Double) {
    OVERALL("AQI Overall", "", listOf(20f, 40f, 60f, 80f, 100f, 120f), { it.europeanAqi }),
    PM25("PM2.5", "µg/m³", listOf(5f, 15f, 50f, 90f, 140f, 168f), { it.pm2_5 }),
    PM10("PM10", "µg/m³", listOf(15f, 45f, 120f, 195f, 270f, 324f), { it.pm10 }),
    OZONE("Ozone", "µg/m³", listOf(60f, 100f, 120f, 160f, 180f, 216f), { it.ozone }),
}

private val AQI_BAND_LABELS = listOf("Good", "Fair", "Moderate", "Poor", "Very poor", "Extremely poor")
private val AQI_BAND_COLORS = listOf(AqiGood, AqiFair, AqiModerate, AqiPoor, AqiVeryPoor, AqiSevere)
private val AQI_LINE_COLOR = Color(0xFF37474F)

/** The three top gauges; holding one shows where its min and max occur on the map. */
private enum class GaugeMetric(val value: (RouteForecastPoint) -> Double) {
    TEMPERATURE({ it.weather.temperatureC }),
    PRECIPITATION({ it.weather.precipitationProbabilityPct }),
    WIND({ it.weather.windSpeedKmh }),
}

@Composable
private fun GaugesRow(
    points: List<RouteForecastPoint>,
    current: RouteForecastPoint,
    onGaugeHeld: (GaugeMetric?) -> Unit,
) {
    fun pressHandler(metric: GaugeMetric): (Boolean) -> Unit = { pressed -> onGaugeHeld(if (pressed) metric else null) }
    val minTemp = points.minOf { it.weather.temperatureC }
    val maxTemp = points.maxOf { it.weather.temperatureC }
    val minPrecip = points.minOf { it.weather.precipitationProbabilityPct }
    val maxPrecip = points.maxOf { it.weather.precipitationProbabilityPct }
    val minWind = points.minOf { it.weather.windSpeedKmh }
    val maxWind = points.maxOf { it.weather.windSpeedKmh }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        SemiCircularGauge(
            rangeMin = minTemp,
            rangeMax = maxTemp,
            scaleMin = -10.0,
            scaleMax = 40.0,
            label = stringResource(R.string.temperature),
            valueText = "${current.weather.temperatureC.roundToInt()}°C",
            minText = "${minTemp.roundToInt()}°C",
            maxText = "${maxTemp.roundToInt()}°C",
            minColor = TempColor,
            maxColor = FeelsLikeColor,
            onPressChange = pressHandler(GaugeMetric.TEMPERATURE),
        )
        SemiCircularGauge(
            rangeMin = minPrecip,
            rangeMax = maxPrecip,
            scaleMin = 0.0,
            scaleMax = 100.0,
            label = stringResource(R.string.precipitation),
            valueText = "${current.weather.precipitationProbabilityPct.roundToInt()}%",
            minText = "${minPrecip.roundToInt()}%",
            maxText = "${maxPrecip.roundToInt()}%",
            minColor = TempColor,
            maxColor = FeelsLikeColor,
            onPressChange = pressHandler(GaugeMetric.PRECIPITATION),
        )
        SemiCircularGauge(
            rangeMin = minWind,
            rangeMax = maxWind,
            scaleMin = 0.0,
            scaleMax = 80.0,
            label = stringResource(R.string.wind),
            valueText = "${current.weather.windSpeedKmh.roundToInt()} km/h",
            minText = "${minWind.roundToInt()} km/h",
            maxText = "${maxWind.roundToInt()} km/h",
            minColor = TempColor,
            maxColor = FeelsLikeColor,
            onPressChange = pressHandler(GaugeMetric.WIND),
        )
    }
}

@Composable
private fun DaylightSummary(result: RouteForecastResult, formatter: DateTimeFormatter) {
    val d = result.daylight
    Column(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        d.sunrise?.let { Text("${stringResource(R.string.sunrise)}: ${formatter.format(it)}", style = MaterialTheme.typography.titleMedium) }
        d.sunset?.let { Text("${stringResource(R.string.sunset)}: ${formatter.format(it)}", style = MaterialTheme.typography.titleMedium) }
        d.civilSunrise?.let { Text("${stringResource(R.string.civil_sunrise)}: ${formatter.format(it)}", style = MaterialTheme.typography.bodyLarge) }
        d.civilSunset?.let { Text("${stringResource(R.string.civil_sunset)}: ${formatter.format(it)}", style = MaterialTheme.typography.bodyLarge) }
    }
}

/**
 * Speed and pacing the forecast was computed with, over the map's free top-left corner. Tapping it
 * switches between constant and realistic pacing and recalculates times and weather.
 */
@Composable
private fun PacingPill(result: RouteForecastResult, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    val realistic = result.pacing.isRealistic
    val accent = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(50),
        color = Color.White,
        contentColor = Color(0xFF1C1B1F),
        shadowElevation = 3.dp,
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (realistic) Icons.Filled.Terrain else Icons.Filled.HorizontalRule,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    "%s km/h · ".format(formatKmh(result.avgSpeedKmh)),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(start = 6.dp),
                )
                Text(pacingLabel(result), style = MaterialTheme.typography.labelLarge, color = accent, fontWeight = FontWeight.SemiBold)
            }
            // What the forecast wind did to the ride time, when it is part of the estimate.
            windEffectLabel(result)?.let {
                Text(
                    stringResource(R.string.forecast_wind_effect, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF49454F),
                )
            }
        }
    }
}

/**
 * Open-Meteo's licence asks for a link wherever its data is shown, and CAMS for a credit wherever its
 * air-quality data is; the full list of sources is in Settings > About. Part of the shared page too.
 */
@Composable
private fun DataSourcesFooter(showAirQuality: Boolean) {
    val uriHandler = LocalUriHandler.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(R.string.source_weather_short),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.clickable { uriHandler.openUri("https://open-meteo.com/") },
        )
        if (showAirQuality) {
            Text(
                stringResource(R.string.source_air_quality_short),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.clickable { uriHandler.openUri("https://atmosphere.copernicus.eu/") },
            )
        }
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


private const val DAYLIGHT_BAR_SAMPLES = 200
private const val UV_SCALE_MAX = 12f

/** Position on the track [distanceM] from the start, linearly interpolated between its two neighbouring points. */
private fun pointAtDistance(track: List<RoutePoint>, distanceM: Double): RoutePoint {
    if (track.size < 2) return track.first()
    var lo = 0
    var hi = track.lastIndex
    while (hi - lo > 1) {
        val mid = (lo + hi) / 2
        if (track[mid].distanceFromStartM <= distanceM) lo = mid else hi = mid
    }
    val a = track[lo]
    val b = track[hi]
    val span = b.distanceFromStartM - a.distanceFromStartM
    val t = if (span <= 0.0) 0.0 else ((distanceM - a.distanceFromStartM) / span).coerceIn(0.0, 1.0)
    val ele = if (a.eleM != null && b.eleM != null) a.eleM + (b.eleM - a.eleM) * t else a.eleM ?: b.eleM
    return RoutePoint(
        lat = a.lat + (b.lat - a.lat) * t,
        lon = a.lon + (b.lon - a.lon) * t,
        eleM = ele,
        distanceFromStartM = distanceM,
    )
}

/** Arrival time at a 0..1 position along the (evenly distance-spaced) forecast samples. */
private fun timeAtFraction(points: List<RouteForecastPoint>, fraction: Float): Instant {
    if (points.size < 2) return points.first().arrivalTime
    val pos = fraction.coerceIn(0f, 1f) * points.lastIndex
    val i = pos.toInt().coerceAtMost(points.lastIndex - 1)
    val t = pos - i
    val a = points[i].arrivalTime.toEpochMilli()
    val b = points[i + 1].arrivalTime.toEpochMilli()
    return Instant.ofEpochMilli(a + ((b - a) * t).toLong())
}

/**
 * 0 = night, 1 = full daylight: ramps up from civil dawn to sunrise and back down from sunset to
 * civil dusk, giving the bar its faded twilight edges.
 */
private fun lightLevel(time: Instant, d: DaylightInfo, fallbackIsDay: Boolean): Float {
    val sunrise = d.sunrise
    val sunset = d.sunset
    if (sunrise == null || sunset == null) return if (fallbackIsDay) 1f else 0f
    val dawn = d.civilSunrise ?: sunrise.minusSeconds(1800)
    val dusk = d.civilSunset ?: sunset.plusSeconds(1800)
    fun ramp(from: Instant, to: Instant): Float {
        val span = (to.toEpochMilli() - from.toEpochMilli()).toFloat()
        return if (span <= 0f) 1f else ((time.toEpochMilli() - from.toEpochMilli()) / span).coerceIn(0f, 1f)
    }
    return when {
        time.isBefore(dawn) -> 0f
        time.isBefore(sunrise) -> ramp(dawn, sunrise)
        !time.isAfter(sunset) -> 1f
        time.isBefore(dusk) -> 1f - ramp(sunset, dusk)
        else -> 0f
    }
}
