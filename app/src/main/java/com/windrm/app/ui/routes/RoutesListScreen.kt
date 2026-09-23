package com.windrm.app.ui.routes

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.windrm.app.R
import com.windrm.app.domain.PolylineDecoder
import com.windrm.app.model.Route
import com.windrm.app.remote.strava.StravaActivitySummary
import com.windrm.app.remote.strava.StravaMapSummary
import com.windrm.app.remote.strava.StravaRouteSummary
import com.windrm.app.remote.strava.StravaSegmentSummary
import com.windrm.app.ui.components.RoutePolylinePreview
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm").withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutesListScreen(
    viewModel: RoutesListViewModel,
    initialTab: Int,
    onBack: () -> Unit,
    onRouteSelected: (Route) -> Unit,
) {
    val context = LocalContext.current
    val routes by viewModel.routes.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    var showStravaInfo by remember { mutableStateOf(false) }

    // Re-checks Strava authorization when returning from the OAuth browser flow (or app switcher).
    LifecycleResumeEffect(Unit) {
        viewModel.onStravaAuthorized()
        onPauseOrDispose { }
    }

    val gpxLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.importGpx(context, uri, onImported = onRouteSelected)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.routes_title)) },
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
        floatingActionButton = {
            if (selectedTab == 0) {
                FloatingActionButton(onClick = { gpxLauncher.launch(arrayOf("application/gpx+xml", "application/octet-stream", "*/*")) }) {
                    Icon(Icons.Filled.UploadFile, contentDescription = stringResource(R.string.import_gpx))
                }
            }
        },
        bottomBar = {
            if (selectedTab == 1 && viewModel.stravaAuthorized) {
                StravaSectionBar(
                    selected = viewModel.stravaSection,
                    onSelect = viewModel::selectStravaSection,
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text(stringResource(R.string.tab_recent)) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text(stringResource(R.string.tab_strava)) })
            }

            when (selectedTab) {
                0 -> RecentRoutesTab(routes, onRouteSelected, onDelete = viewModel::deleteRoute)
                1 -> StravaTab(
                    viewModel = viewModel,
                    onConnect = {
                        if (viewModel.stravaConfigured) viewModel.connectStrava() else showStravaInfo = true
                    },
                    onImported = onRouteSelected,
                )
            }
        }
    }

    if (showStravaInfo) {
        AlertDialog(
            onDismissRequest = { showStravaInfo = false },
            title = { Text(stringResource(R.string.strava_not_configured_title)) },
            text = { Text(stringResource(R.string.strava_not_configured_message)) },
            confirmButton = { TextButton(onClick = { showStravaInfo = false }) { Text("OK") } },
        )
    }
}

@Composable
private fun StravaSectionBar(selected: StravaSection, onSelect: (StravaSection) -> Unit) {
    NavigationBar {
        NavigationBarItem(
            selected = selected == StravaSection.ACTIVITIES,
            onClick = { onSelect(StravaSection.ACTIVITIES) },
            icon = { Icon(Icons.Filled.DirectionsBike, contentDescription = null) },
            label = { Text(stringResource(R.string.strava_section_activities)) },
        )
        NavigationBarItem(
            selected = selected == StravaSection.ROUTES,
            onClick = { onSelect(StravaSection.ROUTES) },
            icon = { Icon(Icons.Filled.Map, contentDescription = null) },
            label = { Text(stringResource(R.string.strava_section_routes)) },
        )
        NavigationBarItem(
            selected = selected == StravaSection.SEGMENTS,
            onClick = { onSelect(StravaSection.SEGMENTS) },
            icon = { Icon(Icons.Filled.EmojiEvents, contentDescription = null) },
            label = { Text(stringResource(R.string.strava_section_segments)) },
        )
    }
}

@Composable
private fun RecentRoutesTab(routes: List<Route>, onSelected: (Route) -> Unit, onDelete: (Route) -> Unit) {
    if (routes.isEmpty()) {
        EmptyState(stringResource(R.string.no_routes_yet))
        return
    }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(routes, key = { it.id }) { route ->
            RouteCard(route = route, onClick = { onSelected(route) }, onDelete = { onDelete(route) })
        }
    }
}

@Composable
private fun RouteCard(route: Route, onClick: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RoutePolylinePreview(points = route.points.map { it.lat to it.lon })
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(route.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(dateFormatter.format(Instant.ofEpochMilli(route.createdAtEpochMs)), style = MaterialTheme.typography.bodySmall)
                val durationS = route.points.lastOrNull()?.timeOffsetS?.takeIf { it > 0 }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    durationS?.let { Text(formatDuration(it), style = MaterialTheme.typography.bodyMedium) }
                    Text("%.1f km".format(route.distanceKm), style = MaterialTheme.typography.bodyMedium)
                    Text("${route.elevationGainM.roundToInt()} m↑", style = MaterialTheme.typography.bodyMedium)
                    durationS?.let {
                        val speed = route.distanceKm / (it / 3600.0)
                        Text("%.1f km/h".format(speed), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.delete_route))
            }
        }
    }
}

@Composable
private fun StravaTab(
    viewModel: RoutesListViewModel,
    onConnect: () -> Unit,
    onImported: (Route) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        if (!viewModel.stravaAuthorized) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.strava_connect_prompt), modifier = Modifier.padding(24.dp))
                    Button(onClick = onConnect) { Text(stringResource(R.string.connect_strava)) }
                    viewModel.stravaError?.let { error ->
                        Text(
                            error,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 16.dp, start = 24.dp, end = 24.dp),
                        )
                    }
                }
            }
            return
        }

        if (viewModel.stravaLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return
        }

        viewModel.stravaError?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
        }

        when (viewModel.stravaSection) {
            StravaSection.ACTIVITIES -> StravaActivitiesList(viewModel, onImported)
            StravaSection.ROUTES -> StravaRoutesList(viewModel, onImported)
            StravaSection.SEGMENTS -> StravaSegmentsList(viewModel, onImported)
        }
    }
}

@Composable
private fun StravaActivitiesList(viewModel: RoutesListViewModel, onImported: (Route) -> Unit) {
    if (viewModel.stravaActivities.isEmpty()) {
        EmptyState(stringResource(R.string.strava_no_activities))
        return
    }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(viewModel.stravaActivities, key = { it.id }) { activity ->
            StravaActivityCard(
                activity = activity,
                importing = viewModel.importingId == activity.id,
                onImport = { viewModel.importStravaActivity(activity, onImported) },
            )
        }
    }
}

@Composable
private fun StravaRoutesList(viewModel: RoutesListViewModel, onImported: (Route) -> Unit) {
    if (viewModel.stravaRoutes.isEmpty()) {
        EmptyState(stringResource(R.string.strava_no_routes))
        return
    }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(viewModel.stravaRoutes, key = { it.id }) { route ->
            StravaRouteCard(
                route = route,
                importing = viewModel.importingId == route.id,
                onImport = { viewModel.importStravaRoute(route, onImported) },
            )
        }
    }
}

@Composable
private fun StravaSegmentsList(viewModel: RoutesListViewModel, onImported: (Route) -> Unit) {
    if (viewModel.stravaSegments.isEmpty()) {
        EmptyState(stringResource(R.string.strava_no_segments))
        return
    }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(viewModel.stravaSegments, key = { it.id }) { segment ->
            StravaSegmentCard(
                segment = segment,
                importing = viewModel.importingId == segment.id,
                onImport = { viewModel.importStravaSegment(segment, onImported) },
            )
        }
    }
}

@Composable
private fun StravaActivityCard(activity: StravaActivitySummary, importing: Boolean, onImport: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RoutePolylinePreview(points = mapPoints(activity.map))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(activity.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                activity.start_date?.let { Text(formatIsoDate(it), style = MaterialTheme.typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (activity.moving_time > 0) Text(formatDuration(activity.moving_time), style = MaterialTheme.typography.bodyMedium)
                    Text("%.1f km".format(activity.distance / 1000.0), style = MaterialTheme.typography.bodyMedium)
                    Text("${activity.total_elevation_gain.roundToInt()} m↑", style = MaterialTheme.typography.bodyMedium)
                    if (activity.average_speed > 0) Text("%.1f km/h".format(activity.average_speed * 3.6), style = MaterialTheme.typography.bodyMedium)
                }
                ImportButton(importing, onImport)
            }
        }
    }
}

@Composable
private fun StravaRouteCard(route: StravaRouteSummary, importing: Boolean, onImport: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RoutePolylinePreview(points = mapPoints(route.map))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(route.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                route.created_at?.let { Text(formatIsoDate(it), style = MaterialTheme.typography.bodySmall) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("%.1f km".format(route.distance / 1000.0), style = MaterialTheme.typography.bodyMedium)
                    Text("${route.elevation_gain.roundToInt()} m↑", style = MaterialTheme.typography.bodyMedium)
                }
                ImportButton(importing, onImport)
            }
        }
    }
}

@Composable
private fun StravaSegmentCard(segment: StravaSegmentSummary, importing: Boolean, onImport: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RoutePolylinePreview(points = mapPoints(segment.map))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(segment.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("%.1f km".format(segment.distance / 1000.0), style = MaterialTheme.typography.bodyMedium)
                    Text("${(segment.elevation_high - segment.elevation_low).roundToInt()} m↑", style = MaterialTheme.typography.bodyMedium)
                    Text("%.1f%%".format(segment.average_grade), style = MaterialTheme.typography.bodyMedium)
                }
                ImportButton(importing, onImport)
            }
        }
    }
}

@Composable
private fun ImportButton(importing: Boolean, onImport: () -> Unit) {
    TextButton(onClick = onImport, enabled = !importing) {
        if (importing) CircularProgressIndicator(modifier = Modifier.size(16.dp)) else Text(stringResource(R.string.strava_import))
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, modifier = Modifier.padding(32.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

private fun mapPoints(map: StravaMapSummary?): List<Pair<Double, Double>> =
    map?.anyPolyline?.let { PolylineDecoder.decode(it) }.orEmpty()

private fun formatIsoDate(iso: String): String = dateFormatter.format(Instant.parse(iso))

private fun formatDuration(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
