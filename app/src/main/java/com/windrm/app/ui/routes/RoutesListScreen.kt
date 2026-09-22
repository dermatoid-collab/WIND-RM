package com.windrm.app.ui.routes

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Delete
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
import com.windrm.app.model.Route
import com.windrm.app.remote.strava.StravaRouteSummary
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutesListScreen(
    viewModel: RoutesListViewModel,
    onRouteSelected: (Route) -> Unit,
) {
    val context = LocalContext.current
    val routes by viewModel.routes.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
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
            Icon(
                Icons.Filled.DirectionsBike,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(route.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(formatDate(route.createdAtEpochMs), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("%.1f km".format(route.distanceKm), style = MaterialTheme.typography.bodyMedium)
                    Text("${route.elevationGainM.roundToInt()} m↑", style = MaterialTheme.typography.bodyMedium)
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

        LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(viewModel.stravaRoutes, key = { it.id }) { route ->
                StravaRouteCard(
                    route = route,
                    importing = viewModel.importingRouteId == route.id,
                    onImport = { viewModel.importStravaRoute(route, onImported) },
                )
            }
        }
    }
}

@Composable
private fun StravaRouteCard(route: StravaRouteSummary, importing: Boolean, onImport: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(route.name, style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("%.1f km".format(route.distance / 1000.0), style = MaterialTheme.typography.bodyMedium)
                Text("${route.elevation_gain.roundToInt()} m↑", style = MaterialTheme.typography.bodyMedium)
            }
            TextButton(onClick = onImport, enabled = !importing) {
                if (importing) CircularProgressIndicator(modifier = Modifier.size(16.dp)) else Text(stringResource(R.string.strava_import_route))
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, modifier = Modifier.padding(32.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

private fun formatDate(epochMs: Long): String {
    val formatter = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm").withZone(ZoneId.systemDefault())
    return formatter.format(Instant.ofEpochMilli(epochMs))
}
