package com.windrm.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.windrm.app.BuildConfig
import com.windrm.app.R
import com.windrm.app.domain.CaiProfile
import com.windrm.app.domain.RideThresholds
import com.windrm.app.settings.MapStyle
import com.windrm.app.settings.ThemeMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val settings = viewModel.settings
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.setGpxFolder(uri)
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.useCurrentLocationAsHome()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
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
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            SettingsSection(stringResource(R.string.settings_account)) {
                StravaAccountRow(viewModel)
            }

            SettingsSection(stringResource(R.string.settings_map)) {
                Text(stringResource(R.string.settings_map_style), style = MaterialTheme.typography.labelLarge)
                MapStyle.entries.forEach { style ->
                    val available = !style.requiresApiKey || apiKeyFor(style).isNotBlank()
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = settings.mapStyle == style,
                            onClick = { viewModel.setMapStyle(style) },
                            enabled = available,
                        )
                        Column(Modifier.padding(start = 4.dp)) {
                            Text(style.label, color = if (available) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant)
                            if (!available) {
                                Text(
                                    stringResource(R.string.settings_requires_api_key),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            SettingsSection(stringResource(R.string.settings_forecast)) {
                Text(stringResource(R.string.settings_default_speed), style = MaterialTheme.typography.labelLarge)
                var speedText by remember(settings.defaultAvgSpeedKmh) { mutableStateOf(formatSpeed(settings.defaultAvgSpeedKmh)) }
                OutlinedTextField(
                    value = speedText,
                    onValueChange = { text ->
                        speedText = text
                        text.toDoubleOrNull()?.let { viewModel.setDefaultAvgSpeedKmh(it) }
                    },
                    suffix = { Text(stringResource(R.string.km_h)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )

                Text(
                    stringResource(R.string.settings_forecast_horizon),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(top = 16.dp),
                )
                var horizonText by remember(settings.forecastHorizonDays) { mutableStateOf(settings.forecastHorizonDays.toString()) }
                OutlinedTextField(
                    value = horizonText,
                    onValueChange = { text ->
                        horizonText = text
                        text.toIntOrNull()?.let { viewModel.setForecastHorizonDays(it) }
                    },
                    suffix = { Text(stringResource(R.string.settings_days_suffix)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }

            SettingsSection(stringResource(R.string.settings_pacing)) {
                Text(
                    stringResource(R.string.settings_pacing_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                NumberSetting(stringResource(R.string.settings_rider_mass), settings.riderMassKg, "kg", { viewModel.setRiderMassKg(it) }, 20.0..200.0)
                NumberSetting(stringResource(R.string.settings_bike_mass), settings.bikeMassKg, "kg", { viewModel.setBikeMassKg(it) }, 3.0..50.0)
                NumberSetting(
                    stringResource(R.string.settings_max_descent_speed),
                    settings.maxDescentSpeedKmh,
                    stringResource(R.string.km_h),
                    { viewModel.setMaxDescentSpeedKmh(it) },
                    10.0..120.0,
                )
            }

            SettingsSection(stringResource(R.string.settings_gpx_folder)) {
                Text(
                    stringResource(R.string.settings_gpx_folder_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    when {
                        settings.gpxFolderUri == null -> stringResource(R.string.settings_gpx_folder_none)
                        viewModel.gpxFolderName != null -> stringResource(R.string.settings_gpx_folder_current, viewModel.gpxFolderName ?: "")
                        else -> stringResource(R.string.settings_gpx_folder_unreadable)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { folderLauncher.launch(null) }) {
                        Text(stringResource(if (settings.gpxFolderUri == null) R.string.settings_gpx_folder_choose else R.string.settings_gpx_folder_change))
                    }
                    if (settings.gpxFolderUri != null) {
                        TextButton(onClick = { viewModel.setGpxFolder(null) }) { Text(stringResource(R.string.settings_gpx_folder_clear)) }
                    }
                }
            }

            SettingsSection(stringResource(R.string.settings_cai)) {
                CaiProfileEditor(settings.caiProfile, { viewModel.setCaiProfile(it) }, { viewModel.resetCaiProfile() })
            }

            SettingsSection(stringResource(R.string.settings_ride_light)) {
                RideThresholdsEditor(settings.rideThresholds, viewModel::setRideThresholds, viewModel::resetRideThresholds)
            }

            SettingsSection(stringResource(R.string.settings_home_location)) {
                if (settings.hasFixedHomeLocation) {
                    Text(
                        stringResource(R.string.settings_home_location_fixed, settings.homeLabel ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    TextButton(onClick = { viewModel.clearHomeLocation() }, modifier = Modifier.padding(top = 4.dp)) {
                        Text(stringResource(R.string.settings_use_live_gps))
                    }
                } else {
                    Text(stringResource(R.string.settings_home_location_gps), style = MaterialTheme.typography.bodyMedium)
                    if (viewModel.homeLocationLoading) {
                        CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp))
                    } else {
                        OutlinedButton(
                            onClick = { locationPermissionLauncher.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION) },
                            modifier = Modifier.padding(top = 8.dp),
                        ) {
                            Text(stringResource(R.string.settings_use_current_location))
                        }
                    }
                    viewModel.homeLocationError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            SettingsSection(stringResource(R.string.settings_appearance)) {
                ThemeMode.entries.forEach { mode ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = settings.themeMode == mode, onClick = { viewModel.setThemeMode(mode) })
                        Text(mode.label, modifier = Modifier.padding(start = 4.dp))
                    }
                }
            }

            SettingsSection(stringResource(R.string.settings_about)) {
                DataSources()
                BuildInfo(Modifier.padding(top = 16.dp))
            }
        }
    }
}

/**
 * Every external data source with the credit its licence asks for, plus the open-source libraries.
 * Titles with a link open the provider's site or licence page.
 */
@Composable
private fun DataSources() {
    val year = remember { java.time.Year.now().value }
    Text(stringResource(R.string.settings_sources), style = MaterialTheme.typography.titleMedium)
    SourceEntry(stringResource(R.string.source_weather_title), stringResource(R.string.source_weather_text), "https://open-meteo.com/")
    SourceEntry(
        stringResource(R.string.source_air_quality_title),
        stringResource(R.string.source_air_quality_text, year),
        "https://atmosphere.copernicus.eu/",
    )
    SourceEntry(stringResource(R.string.source_maps_title), stringResource(R.string.source_maps_text), "https://www.openstreetmap.org/copyright")
    SourceEntry(stringResource(R.string.source_routing_title), stringResource(R.string.source_routing_text), "https://brouter.de/")
    SourceEntry(stringResource(R.string.source_strava_title), stringResource(R.string.source_strava_text), "https://www.strava.com/")
    SourceEntry(stringResource(R.string.source_places_title), stringResource(R.string.source_places_text), null)
    SourceEntry(
        stringResource(R.string.source_software_title),
        stringResource(R.string.source_software_text),
        "https://www.apache.org/licenses/LICENSE-2.0",
    )
}

@Composable
private fun SourceEntry(title: String, text: String, url: String?) {
    val uriHandler = LocalUriHandler.current
    Column(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(
            if (url != null) "$title ↗" else title,
            style = MaterialTheme.typography.labelLarge,
            color = if (url != null) MaterialTheme.colorScheme.primary else Color.Unspecified,
            modifier = if (url != null) Modifier.clickable { uriHandler.openUri(url) } else Modifier,
        )
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** App name, CI build number and commit, build date and copyright, as the last lines of Settings. */
@Composable
private fun BuildInfo(modifier: Modifier = Modifier) {
    val number = BuildConfig.BUILD_NUMBER.toIntOrNull()?.let { "#$it" } ?: BuildConfig.BUILD_NUMBER
    val commit = BuildConfig.BUILD_COMMIT.takeIf { it.isNotEmpty() }?.let { " ($it)" } ?: ""
    val date = BuildConfig.BUILD_TIME_MS.takeIf { it > 0 }?.let {
        DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)
            .format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()))
    }
    Column(modifier.fillMaxWidth()) {
        Text("${stringResource(R.string.app_name)} · ${stringResource(R.string.settings_build)} $number$commit")
        if (date != null) {
            Text(
                stringResource(R.string.settings_built_on, date),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            stringResource(R.string.settings_copyright),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StravaAccountRow(viewModel: SettingsViewModel) {
    Text(
        stringResource(if (viewModel.stravaAuthorized) R.string.settings_strava_connected else R.string.settings_strava_not_connected),
        style = MaterialTheme.typography.bodyMedium,
    )
    viewModel.stravaError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    when {
        !viewModel.stravaConfigured -> Text(
            stringResource(R.string.strava_not_configured_message),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        viewModel.stravaAuthorized -> OutlinedButton(onClick = { viewModel.disconnectStrava() }, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.disconnect_strava))
        }
        else -> Button(onClick = { viewModel.connectStrava() }, modifier = Modifier.padding(top = 8.dp)) {
            Text(stringResource(R.string.connect_strava))
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 8.dp))
        content()
    }
    HorizontalDivider()
}

private fun apiKeyFor(style: MapStyle): String = when (style) {
    MapStyle.MAPBOX_OUTDOORS -> BuildConfig.MAPBOX_ACCESS_TOKEN
    MapStyle.CARTO_POSITRON -> BuildConfig.CARTO_API_KEY
    MapStyle.THUNDERFOREST_OUTDOORS -> BuildConfig.THUNDERFOREST_API_KEY
    else -> ""
}

/**
 * Labelled decimal field; accepts "," as the decimal separator too (Italian keyboards). A value is
 * saved only once it is inside [range], so partial input ("6" on the way to "62") never gets
 * clamped and rewritten under the user's fingers; the text follows outside changes (Restore defaults).
 */
@Composable
private fun NumberSetting(
    label: String,
    value: Double,
    unit: String,
    onChange: (Double) -> Unit,
    range: ClosedFloatingPointRange<Double> = 0.0..Double.MAX_VALUE,
) {
    Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp))
    var text by remember { mutableStateOf(formatSpeed(value)) }
    LaunchedEffect(value) {
        if (parseNumber(text) != value) text = formatSpeed(value)
    }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input
            parseNumber(input)?.takeIf { it in range }?.let(onChange)
        },
        suffix = { Text(unit) },
        isError = parseNumber(text)?.let { it !in range } ?: text.isNotBlank(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
    )
}

/** Trekking times by the CAI signpost rule; every value is editable, with one tap back to the CAI defaults. */
@Composable
private fun CaiProfileEditor(profile: CaiProfile, onChange: (CaiProfile) -> Unit, onReset: () -> Unit) {
    Text(
        stringResource(R.string.settings_cai_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    NumberSetting(stringResource(R.string.settings_cai_flat), profile.flatKmh, stringResource(R.string.km_h), { onChange(profile.copy(flatKmh = it)) }, 1.0..10.0)
    NumberSetting(stringResource(R.string.settings_cai_up), profile.upMetresPerHour, "m/h", { onChange(profile.copy(upMetresPerHour = it)) }, 50.0..2000.0)
    NumberSetting(stringResource(R.string.settings_cai_down), profile.downMetresPerHour, "m/h", { onChange(profile.copy(downMetresPerHour = it)) }, 50.0..3000.0)
    NumberSetting(stringResource(R.string.settings_cai_high_altitude), profile.highAltitudeM, "m", { onChange(profile.copy(highAltitudeM = it)) }, 500.0..9000.0)
    NumberSetting(stringResource(R.string.settings_cai_high_up), profile.highUpMetresPerHour, "m/h", { onChange(profile.copy(highUpMetresPerHour = it)) }, 50.0..2000.0)
    NumberSetting(stringResource(R.string.settings_cai_high_down), profile.highDownMetresPerHour, "m/h", { onChange(profile.copy(highDownMetresPerHour = it)) }, 50.0..3000.0)
    TextButton(onClick = onReset, modifier = Modifier.padding(top = 8.dp)) {
        Text(stringResource(R.string.settings_ride_reset))
    }
}

/**
 * One row per traffic-light metric with its yellow and red limits side by side. Each edit is
 * clamped to a plausible range and saved at once; ice risk has a single (red) limit.
 */
@Composable
private fun RideThresholdsEditor(
    limits: RideThresholds,
    onChange: (RideThresholds) -> Unit,
    onReset: () -> Unit,
) {
    Text(
        stringResource(R.string.settings_ride_light_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.weight(THRESHOLD_LABEL_WEIGHT))
        LimitHeader(stringResource(R.string.settings_ride_yellow), RIDE_YELLOW, Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        LimitHeader(stringResource(R.string.settings_ride_red), RIDE_RED, Modifier.weight(1f))
    }
    ThresholdRow(stringResource(R.string.settings_ride_feels_cold), limits.feelsColdYellowC, limits.feelsColdRedC, -30.0..40.0, downwards = true,
        onYellow = { onChange(limits.copy(feelsColdYellowC = it)) }, onRed = { onChange(limits.copy(feelsColdRedC = it)) })
    ThresholdRow(stringResource(R.string.settings_ride_feels_hot), limits.feelsHotYellowC, limits.feelsHotRedC, 0.0..55.0,
        onYellow = { onChange(limits.copy(feelsHotYellowC = it)) }, onRed = { onChange(limits.copy(feelsHotRedC = it)) })
    ThresholdRow(stringResource(R.string.settings_ride_rain_prob), limits.rainProbYellowPct, limits.rainProbRedPct, 0.0..100.0,
        onYellow = { onChange(limits.copy(rainProbYellowPct = it)) }, onRed = { onChange(limits.copy(rainProbRedPct = it)) })
    ThresholdRow(stringResource(R.string.settings_ride_rain), limits.rainYellowMmH, limits.rainRedMmH, 0.0..50.0,
        onYellow = { onChange(limits.copy(rainYellowMmH = it)) }, onRed = { onChange(limits.copy(rainRedMmH = it)) })
    ThresholdRow(stringResource(R.string.settings_ride_wind), limits.windYellowKmh, limits.windRedKmh, 0.0..150.0,
        onYellow = { onChange(limits.copy(windYellowKmh = it)) }, onRed = { onChange(limits.copy(windRedKmh = it)) })
    ThresholdRow(stringResource(R.string.settings_ride_gusts), limits.gustYellowKmh, limits.gustRedKmh, 0.0..200.0,
        onYellow = { onChange(limits.copy(gustYellowKmh = it)) }, onRed = { onChange(limits.copy(gustRedKmh = it)) })
    ThresholdRow(stringResource(R.string.settings_ride_aqi), limits.aqiYellow, limits.aqiRed, 0.0..500.0,
        onYellow = { onChange(limits.copy(aqiYellow = it)) }, onRed = { onChange(limits.copy(aqiRed = it)) })
    ThresholdRow(stringResource(R.string.settings_ride_uv), limits.uvYellow, limits.uvRed, 0.0..15.0,
        onYellow = { onChange(limits.copy(uvYellow = it)) }, onRed = { onChange(limits.copy(uvRed = it)) })
    ThresholdRow(stringResource(R.string.settings_ride_dew), limits.dewPointYellowC, limits.dewPointRedC, -20.0..35.0,
        onYellow = { onChange(limits.copy(dewPointYellowC = it)) }, onRed = { onChange(limits.copy(dewPointRedC = it)) })
    ThresholdRow(stringResource(R.string.settings_ride_ice), null, limits.iceMaxTempC, -10.0..10.0,
        onYellow = {}, onRed = { onChange(limits.copy(iceMaxTempC = it)) })
    TextButton(onClick = onReset, modifier = Modifier.padding(top = 8.dp)) {
        Text(stringResource(R.string.settings_ride_reset))
    }
}

@Composable
private fun LimitHeader(text: String, color: Color, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).background(color, CircleShape))
        Text(text, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 6.dp))
    }
}

/** [downwards] metrics (cold) worsen as the value drops, so red must sit below yellow instead of above. */
@Composable
private fun ThresholdRow(
    label: String,
    yellow: Double?,
    red: Double,
    range: ClosedFloatingPointRange<Double>,
    downwards: Boolean = false,
    onYellow: (Double) -> Unit,
    onRed: (Double) -> Unit,
) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(THRESHOLD_LABEL_WEIGHT).padding(end = 8.dp))
        if (yellow != null) {
            LimitField(yellow, range, onYellow, Modifier.weight(1f))
        } else {
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.width(8.dp))
        LimitField(red, range, onRed, Modifier.weight(1f))
    }
    val misordered = yellow != null && (if (downwards) red > yellow else red < yellow)
    if (misordered) {
        Text(
            stringResource(R.string.settings_ride_order_error),
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * Keeps what the user is typing (e.g. "0," on the way to "0,5") and only rewrites the text when the
 * stored value changes from elsewhere, such as "Restore defaults".
 */
@Composable
private fun LimitField(value: Double, range: ClosedFloatingPointRange<Double>, onChange: (Double) -> Unit, modifier: Modifier) {
    var text by remember { mutableStateOf(formatLimit(value)) }
    LaunchedEffect(value) {
        if (parseNumber(text) != value) text = formatLimit(value)
    }
    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input
            parseNumber(input)?.let { onChange(it.coerceIn(range)) }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        textStyle = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center),
        singleLine = true,
        modifier = modifier,
    )
}

private const val THRESHOLD_LABEL_WEIGHT = 1.6f
private val RIDE_YELLOW = Color(0xFFF2B400)
private val RIDE_RED = Color(0xFFD7261E)

private fun parseNumber(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()

private fun formatLimit(value: Double): String =
    if (value == value.toInt().toDouble()) value.toInt().toString() else value.toString()

private fun formatSpeed(speedKmh: Double): String =
    if (speedKmh == speedKmh.toInt().toDouble()) speedKmh.toInt().toString() else "%.1f".format(speedKmh)
