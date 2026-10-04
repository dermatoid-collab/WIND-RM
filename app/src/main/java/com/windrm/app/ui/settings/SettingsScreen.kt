package com.windrm.app.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
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
import com.windrm.app.BuildConfig
import com.windrm.app.R
import com.windrm.app.settings.MapStyle
import com.windrm.app.settings.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onBack: () -> Unit) {
    val settings = viewModel.settings
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
                Text("${stringResource(R.string.app_name)} ${stringResource(R.string.settings_version)} ${BuildConfig.VERSION_NAME}")
                Text(
                    stringResource(R.string.settings_attribution),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
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

private fun formatSpeed(speedKmh: Double): String =
    if (speedKmh == speedKmh.toInt().toDouble()) speedKmh.toInt().toString() else "%.1f".format(speedKmh)
