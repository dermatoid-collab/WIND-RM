package com.windrm.app.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.windrm.app.R
import com.windrm.app.model.RoutePoint
import com.windrm.app.settings.MapStyle
import com.windrm.app.ui.components.MapMarker
import com.windrm.app.ui.components.RouteMapView

private const val CHOSEN_ARGB = 0xFFE53935.toInt()

/** A full-screen map to tap the favourite place on; [onConfirm] gets its latitude and longitude. */
@Composable
fun PlacePickerDialog(
    mapStyle: MapStyle,
    /** Where the map opens (latitude, longitude), and the place already chosen, if any. */
    start: Pair<Double, Double>,
    startZoom: Double,
    chosenAtStart: Pair<Double, Double>?,
    onDismiss: () -> Unit,
    onConfirm: (lat: Double, lon: Double) -> Unit,
) {
    var chosen by remember { mutableStateOf(chosenAtStart) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                    Text(
                        stringResource(R.string.settings_pick_on_map_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    )
                }
                RouteMapView(
                    points = emptyList(),
                    mapStyle = mapStyle,
                    markers = chosen?.let { listOf(MapMarker(RoutePoint(lat = it.first, lon = it.second), CHOSEN_ARGB)) }.orEmpty(),
                    onMapTap = { lat, lon -> chosen = lat to lon },
                    height = Dp.Unspecified,
                    autoFit = false,
                    initialCenter = start,
                    initialZoom = startZoom,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = { chosen?.let { onConfirm(it.first, it.second) } },
                    enabled = chosen != null,
                    modifier = Modifier.fillMaxWidth().padding(12.dp).navigationBarsPadding(),
                ) { Text(stringResource(R.string.settings_pick_on_map_use)) }
            }
        }
    }
}
