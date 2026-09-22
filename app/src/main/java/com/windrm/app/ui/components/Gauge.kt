package com.windrm.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/** A ~270° arc gauge, matching the "Temperature / Precipitation / Wind" dials at the top of a forecast. */
@Composable
fun SemiCircularGauge(
    value: Double,
    minValue: Double,
    maxValue: Double,
    label: String,
    valueText: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val fraction = ((value - minValue) / (maxValue - minValue).coerceAtLeast(0.0001)).coerceIn(0.0, 1.0)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = modifier.size(96.dp)) {
            Canvas(modifier = Modifier.size(96.dp)) {
                val strokeWidth = 10.dp.toPx()
                val startAngle = 135f
                val sweepAngle = 270f
                val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

                drawArc(
                    color = Color.LightGray.copy(alpha = 0.3f),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
                drawArc(
                    color = color,
                    startAngle = startAngle,
                    sweepAngle = (sweepAngle * fraction).toFloat(),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }
            Text(valueText, style = MaterialTheme.typography.titleMedium)
        }
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Small circular AQI dial, e.g. "Peak AQI" summary. */
@Composable
fun AqiDial(
    value: Double,
    maxValue: Double,
    label: String,
    subLabel: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val fraction = (value / maxValue.coerceAtLeast(0.0001)).coerceIn(0.0, 1.0)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center, modifier = modifier.size(140.dp)) {
            Canvas(modifier = Modifier.size(140.dp)) {
                val strokeWidth = 14.dp.toPx()
                val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
                val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                drawArc(
                    color = Color.LightGray.copy(alpha = 0.3f),
                    startAngle = 135f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
                drawArc(
                    color = color,
                    startAngle = 135f,
                    sweepAngle = (270f * fraction).toFloat(),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(value.roundToInt().toString(), style = MaterialTheme.typography.headlineMedium)
                Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(subLabel, fontSize = 13.sp, color = color)
    }
}
