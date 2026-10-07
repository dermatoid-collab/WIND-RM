package com.windrm.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.windrm.app.ui.theme.DaylightColor
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

data class ChartSeries(
    val label: String,
    val color: Color,
    val values: List<Float>,
    val filled: Boolean = true,
    // Smoothing suits weather data (interpolated between hourly samples anyway), but elevation
    // should trace the GPX recording exactly -- smoothing it would round off real grade changes.
    val smooth: Boolean = true,
    /** Short name shown in the scrub tooltip; the legend keeps the full [label]. */
    val tooltipLabel: String = label,
    /** Unit appended to the tooltip value, e.g. "°C", "%", " km/h". */
    val unit: String = "",
    val decimals: Int = 1,
    /** Multiplies the value shown in the tooltip only, e.g. to show a 0..12 plotted line as 0..100 %. */
    val tooltipFactor: Float = 1f,
)

/** A horizontal colored band drawn behind the chart, e.g. air-quality severity ranges. */
data class ChartBand(
    val range: ClosedFloatingPointRange<Float>,
    val color: Color,
)

private val Y_AXIS_WIDTH = 40.dp
private val NIGHT_COLOR = Color(0xFF37474F)

/**
 * A lightweight line/area chart (no external charting library): draws a shared time axis on
 * top, gridlines with y-axis labels on both sides, one or more series, and a legend below.
 *
 * Every series is spread evenly across the full width regardless of how many values it has, so
 * the x axis is a 0..1 fraction of the route's distance. Pressing and dragging over the plot
 * reports that fraction ([onScrub]) and shows each series' value there in a card placed beside
 * the finger; lifting the finger clears it ([onScrubEnd]) -- the common "press to inspect" pattern.
 */
@Composable
fun MultiSeriesChart(
    title: String,
    xLabels: List<String>,
    series: List<ChartSeries>,
    modifier: Modifier = Modifier,
    yUnit: String = "",
    yRangeOverride: ClosedFloatingPointRange<Float>? = null,
    bands: List<ChartBand> = emptyList(),
    chartHeight: Dp = 160.dp,
    scrubFraction: Float? = null,
    onScrub: ((Float) -> Unit)? = null,
    onScrubEnd: (() -> Unit)? = null,
    scrubLabel: String? = null,
    /** Optional daylight strip above the plot: light levels 0 (night) .. 1 (full day), evenly spread like a series. */
    daylightBar: List<Float>? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 8.dp))

        TimeAxisLabels(xLabels, yUnit.trim())
        if (!daylightBar.isNullOrEmpty()) DaylightBar(daylightBar)

        val allValues = series.flatMap { it.values }.ifEmpty { listOf(0f) }
        val step = niceStep(allValues)
        val rawMin = yRangeOverride?.start ?: (floor(allValues.min() / step) * step)
        val rawMax = yRangeOverride?.endInclusive ?: (ceil(allValues.max() / step) * step)
        val min = if (rawMin == rawMax) rawMin - 1f else rawMin
        val max = if (rawMin == rawMax) rawMax + 1f else rawMax

        Row(Modifier.fillMaxWidth().height(chartHeight)) {
            YAxisLabels(min, max, Modifier.width(Y_AXIS_WIDTH), alignEnd = false, height = chartHeight)
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().height(chartHeight)) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(chartHeight)
                        .pointerInput(onScrub) {
                            val scrub = onScrub ?: return@pointerInput
                            fun fractionForX(x: Float): Float =
                                if (size.width <= 0) 0f else (x / size.width).coerceIn(0f, 1f)
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                scrub(fractionForX(down.position.x))
                                down.consume()
                                var pointer = down
                                while (pointer.pressed) {
                                    val event = awaitPointerEvent()
                                    pointer = event.changes.first()
                                    if (pointer.pressed) {
                                        scrub(fractionForX(pointer.position.x))
                                        pointer.consume()
                                    }
                                }
                                onScrubEnd?.invoke()
                            }
                        },
                ) {
                    bands.forEach { band ->
                        val topFraction = ((band.range.endInclusive - min) / (max - min)).coerceIn(0f, 1f)
                        val bottomFraction = ((band.range.start - min) / (max - min)).coerceIn(0f, 1f)
                        val top = size.height * (1f - topFraction)
                        val bottom = size.height * (1f - bottomFraction)
                        drawRect(
                            color = band.color.copy(alpha = 0.18f),
                            topLeft = Offset(0f, top),
                            size = Size(size.width, bottom - top),
                        )
                    }
                    val gridColor = Color.LightGray.copy(alpha = 0.4f)
                    val stepCount = 4
                    for (i in 0..stepCount) {
                        val y = size.height * i / stepCount
                        drawLine(color = gridColor, start = Offset(0f, y), end = Offset(size.width, y), strokeWidth = 1.dp.toPx())
                    }
                    if (xLabels.size > 1) {
                        for (i in xLabels.indices) {
                            val x = size.width * i / (xLabels.size - 1)
                            drawLine(color = gridColor, start = Offset(x, 0f), end = Offset(x, size.height), strokeWidth = 1.dp.toPx())
                        }
                    }
                    series.forEach { s ->
                        if (s.values.size < 2) return@forEach
                        val n = s.values.size
                        val stepX = size.width / (n - 1)
                        val linePath = if (s.smooth) {
                            smoothLinePath(s.values, min, max, stepX, size.height)
                        } else {
                            rawLinePath(s.values, min, max, stepX, size.height)
                        }
                        if (s.filled) {
                            val fillPath = Path().apply {
                                addPath(linePath)
                                lineTo((n - 1) * stepX, size.height)
                                lineTo(0f, size.height)
                                close()
                            }
                            drawPath(fillPath, color = s.color.copy(alpha = 0.28f))
                        }
                        drawPath(linePath, color = s.color, style = Stroke(width = 1.6.dp.toPx()))
                    }
                    if (scrubFraction != null) {
                        val f = scrubFraction.coerceIn(0f, 1f)
                        val x = size.width * f
                        series.forEach { s ->
                            val v = s.valueAtFraction(f) ?: return@forEach
                            val y = size.height * (1f - ((v - min) / (max - min)).coerceIn(0f, 1f))
                            drawLine(
                                color = s.color.copy(alpha = 0.6f),
                                start = Offset(0f, y),
                                end = Offset(size.width, y),
                                strokeWidth = 1.dp.toPx(),
                            )
                        }
                        drawLine(
                            color = Color.DarkGray.copy(alpha = 0.6f),
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 1.5.dp.toPx(),
                        )
                        series.forEach { s ->
                            val v = s.valueAtFraction(f) ?: return@forEach
                            val y = size.height * (1f - ((v - min) / (max - min)).coerceIn(0f, 1f))
                            drawCircle(color = Color.White, radius = 5.dp.toPx(), center = Offset(x, y))
                            drawCircle(color = s.color, radius = 3.5.dp.toPx(), center = Offset(x, y))
                        }
                    }
                }
                if (scrubFraction != null) {
                    val f = scrubFraction.coerceIn(0f, 1f)
                    ScrubTooltip(
                        series, f, scrubLabel,
                        // Placed beside the finger -- right of it on the left half, left of it on
                        // the right half -- so the card never hides the point being inspected.
                        modifier = Modifier.layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
                            val width = constraints.maxWidth
                            val touchX = (width * f).roundToInt()
                            val gap = 12.dp.roundToPx()
                            val preferredX = if (f <= 0.5f) touchX + gap else touchX - gap - placeable.width
                            val x = preferredX.coerceIn(0, (width - placeable.width).coerceAtLeast(0))
                            val y = 4.dp.roundToPx()
                            layout(width, placeable.height + y) { placeable.place(x, y) }
                        },
                    )
                }
            }
            YAxisLabels(min, max, Modifier.width(Y_AXIS_WIDTH), alignEnd = true, height = chartHeight)
        }

        if (series.any { it.label.isNotEmpty() }) {
            Legend(series)
        }
    }
}

/**
 * Builds a rounded line through [values] using quadratic Bezier segments between consecutive
 * midpoints, matching the smooth (not sharply-angled) curves used elsewhere in this style of
 * forecast chart.
 */
private fun smoothLinePath(values: List<Float>, min: Float, max: Float, stepX: Float, height: Float): Path {
    val points = values.mapIndexed { i, v ->
        val fraction = ((v - min) / (max - min)).coerceIn(0f, 1f)
        Offset(i * stepX, height * (1f - fraction))
    }
    val path = Path().apply { moveTo(points.first().x, points.first().y) }
    for (i in 0 until points.size - 1) {
        val current = points[i]
        val next = points[i + 1]
        val midX = (current.x + next.x) / 2f
        val midY = (current.y + next.y) / 2f
        path.quadraticTo(current.x, current.y, midX, midY)
    }
    path.lineTo(points.last().x, points.last().y)
    return path
}

/** Straight segments through the exact recorded values -- no interpolation, for data (elevation) where rounding off real changes would misrepresent it. */
private fun rawLinePath(values: List<Float>, min: Float, max: Float, stepX: Float, height: Float): Path {
    val path = Path()
    values.forEachIndexed { i, v ->
        val fraction = ((v - min) / (max - min)).coerceIn(0f, 1f)
        val x = i * stepX
        val y = height * (1f - fraction)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    return path
}

private fun niceStep(values: List<Float>): Float {
    val range = (values.max() - values.min())
    return if (range <= 0.01f) 1f else range / 4f
}

/**
 * Time labels across the top, with the y-axis unit (if any) written once above each axis column
 * instead of after every axis value, where it wrapped in the narrow column.
 */
@Composable
private fun TimeAxisLabels(labels: List<String>, unit: String) {
    Row(Modifier.fillMaxWidth().padding(bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        AxisText(unit, Modifier.width(Y_AXIS_WIDTH))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
            labels.forEach { label -> AxisText(label) }
        }
        AxisText(unit, Modifier.width(Y_AXIS_WIDTH), textAlign = TextAlign.End)
    }
}

/** Yellow daylight strip aligned with the plot, like the original app: fades to dark through twilight. */
@Composable
private fun DaylightBar(levels: List<Float>) {
    Canvas(
        Modifier
            .fillMaxWidth()
            .padding(start = Y_AXIS_WIDTH, end = Y_AXIS_WIDTH, bottom = 4.dp)
            .height(12.dp)
            .clip(RoundedCornerShape(3.dp)),
    ) {
        if (levels.size == 1) {
            drawRect(lerp(NIGHT_COLOR, DaylightColor, levels[0].coerceIn(0f, 1f)))
            return@Canvas
        }
        val stops = levels.mapIndexed { i, level ->
            i.toFloat() / (levels.size - 1) to lerp(NIGHT_COLOR, DaylightColor, level.coerceIn(0f, 1f))
        }.toTypedArray()
        drawRect(Brush.horizontalGradient(*stops))
    }
}

@Composable
private fun YAxisLabels(min: Float, max: Float, modifier: Modifier, alignEnd: Boolean, height: Dp) {
    Column(
        modifier = modifier.height(height),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
    ) {
        AxisText(formatAxisValue(max))
        AxisText(formatAxisValue((max + min) / 2))
        AxisText(formatAxisValue(min))
    }
}

@Composable
private fun AxisText(text: String, modifier: Modifier = Modifier, textAlign: TextAlign? = null) {
    Text(
        text,
        modifier = modifier,
        fontSize = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        softWrap = false,
        textAlign = textAlign,
    )
}

// Whole numbers above 100 (e.g. elevation): a decimal there only costs width in the narrow axis column.
private fun formatAxisValue(v: Float): String = when {
    v == v.toInt().toFloat() || kotlin.math.abs(v) >= 100f -> v.roundToInt().toString()
    else -> "%.1f".format(v)
}

/** Rounds to [decimals] places and drops a trailing ".0", e.g. 12.0 -> "12", 12.34 -> "12.3". */
private fun formatValue(v: Float, decimals: Int): String {
    if (decimals <= 0) return v.roundToInt().toString()
    return "%.${decimals}f".format(v).trimEnd('0').trimEnd('.', ',')
}

/** Floating card showing every series' value at the scrubbed position. */
@Composable
private fun ScrubTooltip(series: List<ChartSeries>, fraction: Float, label: String?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .shadow(2.dp, RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(8.dp))
            .border(1.dp, Color.LightGray.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        label?.let { Text(it, style = MaterialTheme.typography.labelLarge) }
        series.forEach { s ->
            if (s.label.isEmpty()) return@forEach
            val v = s.valueAtFraction(fraction) ?: return@forEach
            Text("${s.tooltipLabel}: ${formatValue(v * s.tooltipFactor, s.decimals)}${s.unit}", color = s.color, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/** The value at a 0..1 position along the x axis, in this series' own index space. */
private fun ChartSeries.valueAtFraction(fraction: Float): Float? {
    if (values.isEmpty()) return null
    val idx = (fraction * (values.size - 1)).roundToInt().coerceIn(0, values.lastIndex)
    return values[idx]
}

@Composable
private fun Legend(series: List<ChartSeries>) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
    ) {
        series.filter { it.label.isNotEmpty() }.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).background(s.color))
                Text(" ${s.label}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
