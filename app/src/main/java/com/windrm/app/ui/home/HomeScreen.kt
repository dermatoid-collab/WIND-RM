package com.windrm.app.ui.home

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Umbrella
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.windrm.app.R
import com.windrm.app.model.CurrentWeatherSnapshot
import com.windrm.app.model.Route
import com.windrm.app.model.WeatherPoint
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onOpenRecent: () -> Unit,
    onOpenStrava: () -> Unit,
    onRouteImported: (Route) -> Unit,
) {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onPermissionResult(granted)
    }
    val gpxLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importGpx(context, uri, onImported = onRouteImported)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            WeatherSnapshotSection(
                viewModel = viewModel,
                onRequestPermission = { permissionLauncher.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION) },
            )

            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MenuRow(Icons.Filled.History, stringResource(R.string.tab_recent), onOpenRecent)
                MenuRow(Icons.Filled.DirectionsBike, stringResource(R.string.tab_strava), onOpenStrava)
                MenuRow(Icons.Filled.UploadFile, stringResource(R.string.menu_files)) {
                    gpxLauncher.launch(arrayOf("application/gpx+xml", "application/octet-stream", "*/*"))
                }
                viewModel.gpxError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun WeatherSnapshotSection(viewModel: HomeViewModel, onRequestPermission: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when {
            !viewModel.hasLocationPermission -> {
                Text(stringResource(R.string.location_permission_prompt), modifier = Modifier.padding(bottom = 12.dp))
                Button(onClick = onRequestPermission) { Text(stringResource(R.string.enable_location)) }
            }
            viewModel.loading -> CircularProgressIndicator()
            viewModel.error != null -> {
                Text(viewModel.error ?: "", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(bottom = 12.dp))
                Button(onClick = viewModel::retry) { Text(stringResource(R.string.retry)) }
            }
            viewModel.snapshot != null -> CurrentWeatherCard(viewModel.snapshot!!)
        }
    }
}

@Composable
private fun CurrentWeatherCard(snapshot: CurrentWeatherSnapshot) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(snapshot.locationLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${snapshot.current.temperatureC.roundToInt()}°", style = MaterialTheme.typography.displayLarge)
        Text(
            "Feels Like: ${snapshot.current.feelsLikeC.roundToInt()}°   H:${snapshot.highC.roundToInt()}° L:${snapshot.lowC.roundToInt()}°",
            style = MaterialTheme.typography.bodyMedium,
        )

        val timeFmt = remember(snapshot) { DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()) }
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(horizontal = 8.dp),
        ) {
            items(snapshot.hourly) { hour ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(timeFmt.format(hour.time), style = MaterialTheme.typography.labelMedium)
                    Icon(weatherIcon(hour), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Text("${hour.temperatureC.roundToInt()}°", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp))
        }
    }
}

private fun weatherIcon(point: WeatherPoint): ImageVector = when {
    point.precipitationProbabilityPct >= 50 -> Icons.Filled.Umbrella
    point.cloudCoverPct >= 60 -> Icons.Filled.Cloud
    point.isDay -> Icons.Filled.WbSunny
    else -> Icons.Filled.NightsStay
}
