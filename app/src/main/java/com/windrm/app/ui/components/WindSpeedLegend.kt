package com.windrm.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** One 10 km/h wind band: arrows below [upperKmh] get [argb]. */
internal data class WindBand(val upperKmh: Double, val argb: Int, val label: String)

// Darker shades of green / blue / orange / red so they read over terrain greens and the red track.
internal val WIND_BANDS = listOf(
    WindBand(10.0, 0xFF1B5E20.toInt(), "0–10"),
    WindBand(20.0, 0xFF0277BD.toInt(), "10–20"),
    WindBand(30.0, 0xFFE65100.toInt(), "20–30"),
    WindBand(40.0, 0xFF8E0000.toInt(), "30–40"),
    WindBand(Double.POSITIVE_INFINITY, 0xFF000000.toInt(), "40+"),
)

internal fun windBandColor(windSpeedKmh: Double): Int = WIND_BANDS.first { windSpeedKmh < it.upperKmh }.argb

/** Colour key for the wind arrows on the map, one small arrow per speed band. */
@Composable
fun WindSpeedLegend(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WIND_BANDS.forEach { band ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                LegendArrow(Color(band.argb))
                Text(" ${band.label}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("km/h", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A right-pointing miniature of the map arrow, with the same thin white edge so black stays visible in dark mode. */
@Composable
private fun LegendArrow(color: Color) {
    Canvas(Modifier.size(width = 16.dp, height = 10.dp)) {
        val headLength = 6.dp.toPx()
        val headHalfWidth = 3.5.dp.toPx()
        val shaftWidth = 2.dp.toPx()
        val edge = 1.dp.toPx()
        val midY = size.height / 2
        val tipX = size.width - edge
        val baseX = tipX - headLength
        val head = Path().apply {
            moveTo(tipX, midY)
            lineTo(baseX, midY - headHalfWidth)
            lineTo(baseX, midY + headHalfWidth)
            close()
        }
        drawLine(Color.White, Offset(edge, midY), Offset(baseX, midY), strokeWidth = shaftWidth + 2 * edge)
        drawPath(head, Color.White, style = Stroke(width = 2 * edge, join = StrokeJoin.Round))
        drawLine(color, Offset(edge, midY), Offset(baseX + 0.5f, midY), strokeWidth = shaftWidth)
        drawPath(head, color)
    }
}
