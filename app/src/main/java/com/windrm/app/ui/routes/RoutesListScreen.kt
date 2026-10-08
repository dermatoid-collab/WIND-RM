package com.windrm.app.ui.routes

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
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
import androidx.compose.runtime.LaunchedEffect
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
import com.windrm.app.ui.components.AppBar
import com.windrm.app.domain.PolylineDecoder
import com.windrm.app.gpx.GpxFolder
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

/** Tab order in the routes screen; the home menu opens a given tab by index. */
const val TAB_RECENT = 0
const val TAB_FAVORITES = 1
const val TAB_STRAVA = 2
const val TAB_FILES = 3

private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm").withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutesListScreen(
    viewModel: RoutesListViewModel,
    initialTab: Int,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
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

    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.setGpxFolder(context, uri)
    }
    // The folder is read again every time the Files tab comes up.
    LaunchedEffect(selectedTab) {
        if (selectedTab == TAB_FILES) viewModel.loadFiles(context)
    }

    val gpxLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.importGpx(context, uri, onImported = onRouteSelected)
        }
    }

    Scaffold(
        topBar = {
            AppBar(title = stringResource(R.string.routes_title), onBack = onBack, onOpenSettings = onOpenSettings)
        },
        floatingActionButton = {
            if (selectedTab == TAB_RECENT) {
                FloatingActionButton(onClick = { gpxLauncher.launch(arrayOf("application/gpx+xml", "application/vnd.garmin.tcx+xml", "application/octet-stream", "*/*")) }) {
                    Icon(Icons.Filled.UploadFile, contentDescription = stringResource(R.string.import_gpx))
                }
            }
        },
        bottomBar = {
            if (selectedTab == TAB_STRAVA && viewModel.stravaAuthorized) {
                StravaSectionBar(
                    selected = viewModel.stravaSection,
                    onSelect = viewModel::selectStravaSection,
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == TAB_RECENT, onClick = { selectedTab = TAB_RECENT }, text = { Text(stringResource(R.string.tab_recent)) })
                Tab(selected = selectedTab == TAB_FAVORITES, onClick = { selectedTab = TAB_FAVORITES }, text = { Text(stringResource(R.string.tab_favorites)) })
                Tab(selected = selectedTab == TAB_STRAVA, onClick = { selectedTab = TAB_STRAVA }, text = { Text(stringResource(R.string.tab_strava)) })
                Tab(selected = selectedTab == TAB_FILES, onClick = { selectedTab = TAB_FILES }, text = { Text(stringResource(R.string.tab_files)) })
            }

            when (selectedTab) {
                TAB_RECENT -> RouteListTab(
                    routes = routes,
                    emptyMessage = stringResource(R.string.no_routes_yet),
                    onSelected = onRouteSelected,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onDelete = viewModel::deleteRoute,
                )
                TAB_FAVORITES -> RouteListTab(
                    routes = routes.filter { it.isFavorite },
                    emptyMessage = stringResource(R.string.no_favorites_yet),
                    onSelected = onRouteSelected,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onDelete = viewModel::deleteRoute,
                )
                TAB_STRAVA -> StravaTab(
                    viewModel = viewModel,
                    onConnect = {
                        if (viewModel.stravaConfigured) viewModel.connectStrava() else showStravaInfo = true
                    },
                    onImported = onRouteSelected,
                )
                TAB_FILES -> FilesTab(
                    viewModel = viewModel,
                    onChooseFolder = { folderLauncher.launch(null) },
                    onOpen = { entry -> viewModel.openGpxFile(context, entry, onRouteSelected) },
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

/** The GPX files of the folder chosen in Settings; tapping one opens it as a route. */
@Composable
private fun FilesTab(viewModel: RoutesListViewModel, onChooseFolder: () -> Unit, onOpen: (GpxFolder.Entry) -> Unit) {
    if (viewModel.gpxFolderUri == null) {
        Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(stringResource(R.string.files_no_folder), style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onChooseFolder, modifier = Modifier.padding(top = 16.dp)) { Text(stringResource(R.string.settings_gpx_folder_choose)) }
        }
        return
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                viewModel.gpxFolderName ?: stringResource(R.string.settings_gpx_folder_unreadable),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onChooseFolder) { Text(stringResource(R.string.settings_gpx_folder_change)) }
        }
        viewModel.gpxError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp)) }
        viewModel.filesError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp)) }
        when {
            viewModel.filesLoading && viewModel.gpxFiles.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            viewModel.gpxFiles.isEmpty() -> EmptyState(stringResource(R.string.files_empty))
            else -> LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(viewModel.gpxFiles, key = { it.documentUri.toString() }) { entry -> GpxFileCard(entry, onClick = { onOpen(entry) }) }
            }
        }
    }
}

@Composable
private fun GpxFileCard(entry: GpxFolder.Entry, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Map, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(entry.name.substringBeforeLast('.'), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                val details = listOfNotNull(
                    entry.name.substringAfterLast('.', "").uppercase().ifEmpty { null },
                    entry.folder,
                    entry.lastModifiedMs?.let { dateFormatter.format(Instant.ofEpochMilli(it)) },
                    entry.sizeBytes?.let { "%.0f kB".format(it / 1024.0) },
                ).joinToString(" · ")
                if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
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
private fun RouteListTab(
    routes: List<Route>,
    emptyMessage: String,
    onSelected: (Route) -> Unit,
    onToggleFavorite: (Route) -> Unit,
    onDelete: (Route) -> Unit,
) {
    if (routes.isEmpty()) {
        EmptyState(emptyMessage)
        return
    }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(routes, key = { it.id }) { route ->
            RouteCard(
                route = route,
                onClick = { onSelected(route) },
                onToggleFavorite = { onToggleFavorite(route) },
                onDelete = { onDelete(route) },
            )
        }
    }
}

@Composable
private fun RouteCard(route: Route, onClick: () -> Unit, onToggleFavorite: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            RoutePolylinePreview(points = route.points.map { it.lat to it.lon })
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(route.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                // The route's own creation date (as Strava lists it); the import date only when unknown.
                Text(dateFormatter.format(Instant.ofEpochMilli(route.originalDateEpochMs ?: route.createdAtEpochMs)), style = MaterialTheme.typography.bodySmall)
                val durationS = route.points.lastOrNull()?.timeOffsetS?.takeIf { it > 0 }
                StatsRow {
                    durationS?.let { Text(formatDuration(it), style = MaterialTheme.typography.bodyMedium) }
                    Text("%.1f km".format(route.distanceKm), style = MaterialTheme.typography.bodyMedium)
                    Text("${route.elevationGainM.roundToInt()} m↑", style = MaterialTheme.typography.bodyMedium)
                    durationS?.let {
                        val speed = route.distanceKm / (it / 3600.0)
                        Text("%.1f km/h".format(speed), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            FavoriteButton(isFavorite = route.isFavorite, onClick = onToggleFavorite)
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
    ImportableCard(importing, onImport) {
        RoutePolylinePreview(points = mapPoints(activity.map))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(activity.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            activity.start_date?.let { Text(formatIsoDate(it), style = MaterialTheme.typography.bodySmall) }
            StatsRow {
                if (activity.moving_time > 0) Text(formatDuration(activity.moving_time), style = MaterialTheme.typography.bodyMedium)
                Text("%.1f km".format(activity.distance / 1000.0), style = MaterialTheme.typography.bodyMedium)
                Text("${activity.total_elevation_gain.roundToInt()} m↑", style = MaterialTheme.typography.bodyMedium)
                if (activity.average_speed > 0) Text("%.1f km/h".format(activity.average_speed * 3.6), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun StravaRouteCard(route: StravaRouteSummary, importing: Boolean, onImport: () -> Unit) {
    ImportableCard(importing, onImport) {
        RoutePolylinePreview(points = mapPoints(route.map))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(route.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            route.created_at?.let { Text(formatIsoDate(it), style = MaterialTheme.typography.bodySmall) }
            StatsRow {
                Text("%.1f km".format(route.distance / 1000.0), style = MaterialTheme.typography.bodyMedium)
                Text("${route.elevation_gain.roundToInt()} m↑", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun StravaSegmentCard(segment: StravaSegmentSummary, importing: Boolean, onImport: () -> Unit) {
    ImportableCard(importing, onImport) {
        RoutePolylinePreview(points = mapPoints(segment.map))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(segment.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            StatsRow {
                Text("%.1f km".format(segment.distance / 1000.0), style = MaterialTheme.typography.bodyMedium)
                Text("${(segment.elevation_high - segment.elevation_low).roundToInt()} m↑", style = MaterialTheme.typography.bodyMedium)
                Text("%.1f%%".format(segment.average_grade), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * The stats line of a card (time, km, m↑, km/h, ...). Values that don't fit move whole to a second
 * line: in a plain Row the last one was squeezed to zero width and wrapped a letter per line,
 * stretching the card to several times its height.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatsRow(content: @Composable () -> Unit) {
    // A plain content lambda keeps the experimental FlowRowScope out of every call site.
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) { content() }
}

/** The whole card is the import tap target; a spinner replaces the trailing space while importing. */
@Composable
private fun ImportableCard(importing: Boolean, onImport: () -> Unit, content: @Composable RowScope.() -> Unit) {
    Card(onClick = onImport, enabled = !importing, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            content()
            if (importing) CircularProgressIndicator(modifier = Modifier.padding(start = 8.dp).size(20.dp), strokeWidth = 2.dp)
        }
    }
}

/** Star toggle shared by the route cards and the route detail screen. */
@Composable
fun FavoriteButton(isFavorite: Boolean, onClick: () -> Unit, tint: Color = FAVORITE_STAR) {
    IconButton(onClick = onClick) {
        Icon(
            if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
            contentDescription = stringResource(if (isFavorite) R.string.remove_favorite else R.string.add_favorite),
            tint = tint,
        )
    }
}

private val FAVORITE_STAR = Color(0xFFF2A900)

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
