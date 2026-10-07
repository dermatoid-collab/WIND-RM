package com.windrm.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.input.pointer.pointerInput
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

/**
 * A ~270° dual-ring arc gauge, matching the "Temperature / Precipitation / Wind" dials at the
 * top of a forecast: the outer ring shows [rangeMax] and the inner ring [rangeMin] -- the
 * highest/lowest value of that metric across the whole displayed forecast window -- each as a
 * fraction of the fixed [scaleMin]..[scaleMax] scale, while the center text is the current
 * (scrubbed) reading. [maxText]/[minText] spell those two values out under the label, each in
 * its ring's colour.
 */
@Composable
fun SemiCircularGauge(
    rangeMin: Double,
    rangeMax: Double,
    scaleMin: Double,
    scaleMax: Double,
    label: String,
    valueText: String,
    minText: String,
    maxText: String,
    minColor: Color,
    maxColor: Color,
    modifier: Modifier = Modifier,
    /** true while a finger is held on the gauge, false when it lifts. */
    onPressChange: ((Boolean) -> Unit)? = null,
) {
    // Keyed on Unit with the latest callback read through state: re-keying on the lambda would
    // restart the detector mid-press (the press itself recomposes the screen) and lose the release.
    val pressCallback by rememberUpdatedState(onPressChange)
    val scale = (scaleMax - scaleMin).coerceAtLeast(0.0001)
    val minFraction = ((rangeMin - scaleMin) / scale).coerceIn(0.0, 1.0)
    val maxFraction = ((rangeMax - scaleMin) / scale).coerceIn(0.0, 1.0)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.pointerInput(Unit) {
            detectTapGestures(onPress = {
                try {
                    pressCallback?.invoke(true)
                    tryAwaitRelease()
                } finally {
                    pressCallback?.invoke(false)
                }
            })
        },
    ) {
        Box(contentAlignment = Alignment.Center, modifier = modifier.size(96.dp)) {
            Canvas(modifier = Modifier.size(96.dp)) {
                val ringStroke = 7.dp.toPx()
                val ringGap = 3.dp.toPx()
                val startAngle = 135f
                val sweepAngle = 270f

                val outerTopLeft = Offset(ringStroke / 2, ringStroke / 2)
                val outerSize = Size(size.width - ringStroke, size.height - ringStroke)
                val innerInset = ringStroke + ringGap + ringStroke / 2
                val innerTopLeft = Offset(innerInset, innerInset)
                val innerSize = Size(size.width - innerInset * 2, size.height - innerInset * 2)

                // Outer ring: rangeMax.
                drawArc(
                    color = Color.LightGray.copy(alpha = 0.3f),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = outerTopLeft,
                    size = outerSize,
                    style = Stroke(width = ringStroke, cap = StrokeCap.Round),
                )
                drawArc(
                    color = maxColor,
                    startAngle = startAngle,
                    sweepAngle = (sweepAngle * maxFraction).toFloat(),
                    useCenter = false,
                    topLeft = outerTopLeft,
                    size = outerSize,
                    style = Stroke(width = ringStroke, cap = StrokeCap.Round),
                )

                // Inner ring: rangeMin.
                drawArc(
                    color = Color.LightGray.copy(alpha = 0.3f),
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = innerTopLeft,
                    size = innerSize,
                    style = Stroke(width = ringStroke, cap = StrokeCap.Round),
                )
                drawArc(
                    color = minColor,
                    startAngle = startAngle,
                    sweepAngle = (sweepAngle * minFraction).toFloat(),
                    useCenter = false,
                    topLeft = innerTopLeft,
                    size = innerSize,
                    style = Stroke(width = ringStroke, cap = StrokeCap.Round),
                )
            }
            Text(valueText, style = MaterialTheme.typography.titleMedium)
        }
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("max $maxText", fontSize = 12.sp, color = maxColor)
        Text("min $minText", fontSize = 12.sp, color = minColor)
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
