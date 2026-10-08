package com.windrm.app.ui.live

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.windrm.app.R
import com.windrm.app.ui.components.AppBar
import com.windrm.app.domain.TrackMatcher
import com.windrm.app.ui.components.MapMarker
import com.windrm.app.ui.components.RouteMapView
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

private const val FINE = Manifest.permission.ACCESS_FINE_LOCATION
private val OFF_ROUTE_ARGB = 0xFFE5532D.toInt()

@Composable
fun LiveScreen(viewModel: LiveViewModel, onBack: () -> Unit, onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onPermission(granted)
    }

    // The screen stays lit while it is open: it is meant to sit on the handlebars.
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    // GPS only while the page is in front.
    LifecycleResumeEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, FINE) == PackageManager.PERMISSION_GRANTED
        viewModel.onPermission(granted)
        if (!granted && !asked) {
            asked = true
            launcher.launch(FINE)
        }
        onPauseOrDispose { viewModel.stop() }
    }

    Scaffold(
        topBar = {
            AppBar(
                title = listOfNotNull(stringResource(R.string.live_title), viewModel.route?.name).joinToString(" · "),
                onBack = onBack,
                onOpenSettings = onOpenSettings,
            )
        },
    ) { padding ->
        val route = viewModel.route
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                viewModel.loadError != null -> Text(viewModel.loadError.orEmpty(), Modifier.padding(24.dp), color = MaterialTheme.colorScheme.error)
                route == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                else -> LiveContent(viewModel, onRequestPermission = { launcher.launch(FINE) })
            }
        }
    }

    viewModel.pendingPasses?.let { passes ->
        PassChoiceDialog(passes, totalKm = viewModel.totalKm, onChoose = viewModel::choosePass)
    }
}

@Composable
private fun LiveContent(viewModel: LiveViewModel, onRequestPermission: () -> Unit) {
    val route = viewModel.route ?: return
    val progress = viewModel.progress
    val offRoute = progress != null && progress.offTrackM > LiveViewModel.ON_ROUTE_M
    val clock = remember { DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()).withZone(ZoneId.systemDefault()) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        StatusBanner(viewModel, offRoute, onRequestPermission)

        Box(Modifier.fillMaxWidth().height(260.dp).padding(horizontal = 8.dp).clip(RoundedCornerShape(8.dp))) {
            RouteMapView(
                points = route.points,
                mapStyle = viewModel.mapStyle,
                stops = route.stops,
                highlightPoint = viewModel.matchedPoint,
                // The raw GPS dot is only worth showing where it differs from the point on the route.
                markers = if (offRoute) listOfNotNull(viewModel.gpsPosition?.let { MapMarker(it, OFF_ROUTE_ARGB) }) else emptyList(),
                height = 260.dp,
            )
        }

        if (progress != null) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tile(
                    label = stringResource(R.string.live_remaining),
                    value = if (progress.arrived) stringResource(R.string.live_arrived) else "%.1f".format(progress.remainingKm),
                    unit = if (progress.arrived) "" else "km",
                    modifier = Modifier.weight(1f),
                )
                Tile(
                    label = stringResource(R.string.live_time_left),
                    value = formatHm(progress.plannedLeftS),
                    unit = stringResource(R.string.live_planned),
                    sub = stringResource(R.string.live_arrival_at, clock.format(Instant.now().plusSeconds(progress.plannedLeftS))),
                    modifier = Modifier.weight(1f),
                )
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val corrected = progress.correctedLeftS
                Tile(
                    label = stringResource(R.string.live_at_your_pace),
                    value = corrected?.let { formatHm(it) } ?: "—",
                    unit = progress.recentSpeedKmh?.let { "%.1f km/h".format(it) } ?: stringResource(R.string.live_pace_pending),
                    sub = corrected?.let { stringResource(R.string.live_arrival_at, clock.format(Instant.now().plusSeconds(it))) },
                    modifier = Modifier.weight(1f),
                )
                Tile(
                    label = stringResource(R.string.live_to_go),
                    value = progress.ascentLeftM?.let { "↑ ${it.roundToInt()}" } ?: "—",
                    unit = "m",
                    sub = progress.descentLeftM?.let { "↓ ${it.roundToInt()} m" },
                    modifier = Modifier.weight(1f),
                )
            }

            if (progress.elevationAhead.size > 1) {
                Text(
                    stringResource(R.string.live_profile_ahead),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 16.dp, top = 14.dp, end = 16.dp),
                )
                ElevationAhead(
                    progress.elevationAhead,
                    totalKm = progress.remainingKm,
                    modifier = Modifier.fillMaxWidth().height(120.dp).padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
            if (viewModel.windIgnored) {
                Text(
                    stringResource(R.string.live_wind_ignored),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        Box(Modifier.height(16.dp))
    }
}

@Composable
private fun StatusBanner(viewModel: LiveViewModel, offRoute: Boolean, onRequestPermission: () -> Unit) {
    val progress = viewModel.progress
    val message: String?
    val isWarning: Boolean
    var action: (@Composable () -> Unit)? = null
    when {
        !viewModel.permissionGranted -> {
            message = stringResource(R.string.live_permission_needed)
            isWarning = true
            action = { Button(onClick = onRequestPermission) { Text(stringResource(R.string.enable_location)) } }
        }
        !viewModel.gpsEnabled && viewModel.gpsPosition == null -> {
            message = stringResource(R.string.live_gps_off)
            isWarning = true
        }
        viewModel.pendingPasses != null -> {
            message = stringResource(R.string.live_choose_pass_hint)
            isWarning = false
        }
        offRoute && progress != null -> {
            message = stringResource(R.string.live_off_route, progress.offTrackM.roundToInt())
            isWarning = true
        }
        progress == null -> {
            message = stringResource(R.string.live_waiting_fix)
            isWarning = false
        }
        else -> {
            message = null
            isWarning = false
        }
    }
    if (message == null) return
    Surface(
        color = if (isWarning) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            action?.let {
                Box(Modifier.padding(top = 8.dp)) { it() }
            }
        }
    }
}

@Composable
private fun Tile(label: String, value: String, unit: String, modifier: Modifier = Modifier, sub: String? = null) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                if (unit.isNotEmpty()) {
                    Text(
                        unit,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, bottom = 3.dp),
                    )
                }
            }
            Text(
                sub ?: " ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/** The elevation from here to the finish: the left edge is the rider, the right edge the end. */
@Composable
private fun ElevationAhead(values: List<Double>, totalKm: Double, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val low = values.min()
    val high = max(values.max(), low + 40.0)
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${low.roundToInt()} m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${high.roundToInt()} m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Canvas(Modifier.fillMaxWidth().weight(1f)) {
            drawLine(grid, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 2f)
            val area = Path()
            val path = Path()
            values.forEachIndexed { i, v ->
                val x = i.toFloat() / (values.size - 1) * size.width
                val y = size.height - ((v - low) / (high - low)).toFloat() * size.height
                if (i == 0) {
                    path.moveTo(x, y)
                    area.moveTo(x, size.height)
                    area.lineTo(x, y)
                } else {
                    path.lineTo(x, y)
                    area.lineTo(x, y)
                }
            }
            area.lineTo(size.width, size.height)
            area.close()
            drawPath(area, line.copy(alpha = 0.18f))
            drawPath(path, line, style = Stroke(width = 3f))
            // The rider, at the left edge.
            val y0 = size.height - ((values.first() - low) / (high - low)).toFloat() * size.height
            drawCircle(line, radius = 7f, center = Offset(0f, y0))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.live_you), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("%.1f km".format(totalKm), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PassChoiceDialog(passes: List<TrackMatcher.Match>, totalKm: Double, onChoose: (TrackMatcher.Match) -> Unit) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.live_choose_pass_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.live_choose_pass_body))
                passes.forEach { pass ->
                    OutlinedButton(onClick = { onChoose(pass) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.live_pass_option, pass.distanceM / 1000.0, totalKm))
                    }
                }
            }
        },
        confirmButton = {},
    )
}

/** "2h 05m" / "42 min". */
private fun formatHm(seconds: Long): String {
    val minutes = (seconds / 60.0).roundToInt()
    return if (minutes >= 60) "%dh %02dm".format(minutes / 60, minutes % 60) else "$minutes min"
}
