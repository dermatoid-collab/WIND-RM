package com.windrm.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.awaitEachGesture
import androidx.compose.ui.input.pointer.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

data class ChartSeries(
    val label: String,
    val color: Color,
    val values: List<Float>,
    val filled: Boolean = true,
)

/** A horizontal colored band drawn behind the chart, e.g. air-quality severity ranges. */
data class ChartBand(
    val range: ClosedFloatingPointRange<Float>,
    val color: Color,
)

private val Y_AXIS_WIDTH = 40.dp

/**
 * A lightweight line/area chart (no external charting library): draws a shared time axis on
 * top, gridlines with y-axis labels on both sides, one or more series, and a legend below.
 * Mirrors the layout used throughout the forecast screen (temperature, wind, elevation, etc.).
 *
 * Dragging a finger anywhere over the plot moves a shared scrub cursor ([scrubIndex]/[onScrub])
 * and shows each series' value at that instant next to the title.
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
    scrubIndex: Int? = null,
    onScrub: ((Int) -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 8.dp))
            if (scrubIndex != null) {
                ScrubReadout(series, scrubIndex, yUnit)
            }
        }

        TimeAxisLabels(xLabels)

        val allValues = series.flatMap { it.values }.ifEmpty { listOf(0f) }
        val step = niceStep(allValues)
        val rawMin = yRangeOverride?.start ?: (floor(allValues.min() / step) * step)
        val rawMax = yRangeOverride?.endInclusive ?: (ceil(allValues.max() / step) * step)
        val min = if (rawMin == rawMax) rawMin - 1f else rawMin
        val max = if (rawMin == rawMax) rawMax + 1f else rawMax
        val sampleCount = series.maxOfOrNull { it.values.size } ?: 0

        Row(Modifier.fillMaxWidth().height(chartHeight)) {
            YAxisLabels(min, max, yUnit, Modifier.width(Y_AXIS_WIDTH), alignEnd = false, height = chartHeight)
            Box(Modifier.weight(1f).fillMaxWidth().height(chartHeight)) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(chartHeight)
                        .pointerInput(sampleCount, onScrub) {
                            if (onScrub == null || sampleCount < 2) return@pointerInput
                            fun indexForX(x: Float): Int {
                                val stepX = size.width.toFloat() / (sampleCount - 1)
                                return (x / stepX).roundToInt().coerceIn(0, sampleCount - 1)
                            }
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                onScrub(indexForX(down.position.x))
                                down.consume()
                                var pointer = down
                                while (pointer.pressed) {
                                    val event = awaitPointerEvent()
                                    pointer = event.changes.first()
                                    if (pointer.pressed) {
                                        onScrub(indexForX(pointer.position.x))
                                        pointer.consume()
                                    }
                                }
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
                            size = androidx.compose.ui.geometry.Size(size.width, bottom - top),
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
                        val smoothPath = smoothLinePath(s.values, min, max, stepX, size.height)
                        if (s.filled) {
                            val fillPath = Path().apply {
                                addPath(smoothPath)
                                lineTo((n - 1) * stepX, size.height)
                                lineTo(0f, size.height)
                                close()
                            }
                            drawPath(fillPath, color = s.color.copy(alpha = 0.28f))
                        }
                        drawPath(smoothPath, color = s.color, style = Stroke(width = 1.6.dp.toPx()))
                    }
                    if (scrubIndex != null && sampleCount > 1) {
                        val stepX = size.width / (sampleCount - 1)
                        val x = scrubIndex.coerceIn(0, sampleCount - 1) * stepX
                        drawLine(
                            color = Color.DarkGray.copy(alpha = 0.6f),
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 1.5.dp.toPx(),
                        )
                        series.forEach { s ->
                            val v = s.values.getOrNull(scrubIndex.coerceIn(0, s.values.size - 1)) ?: return@forEach
                            val fraction = ((v - min) / (max - min)).coerceIn(0f, 1f)
                            val y = size.height * (1f - fraction)
                            drawCircle(color = Color.White, radius = 5.dp.toPx(), center = Offset(x, y))
                            drawCircle(color = s.color, radius = 3.5.dp.toPx(), center = Offset(x, y))
                        }
                    }
                }
            }
            YAxisLabels(min, max, yUnit, Modifier.width(Y_AXIS_WIDTH), alignEnd = true, height = chartHeight)
        }

        if (series.isNotEmpty() && series.any { it.label.isNotEmpty() }) {
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

private fun niceStep(values: List<Float>): Float {
    val range = (values.max() - values.min())
    return if (range <= 0.01f) 1f else range / 4f
}

@Composable
private fun TimeAxisLabels(labels: List<String>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Y_AXIS_WIDTH, end = Y_AXIS_WIDTH, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        labels.forEach { label ->
            Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun YAxisLabels(min: Float, max: Float, unit: String, modifier: Modifier, alignEnd: Boolean, height: Dp) {
    Column(
        modifier = modifier.height(height),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start,
    ) {
        Text(formatAxisValue(max) + unit, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatAxisValue((max + min) / 2) + unit, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatAxisValue(min) + unit, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatAxisValue(v: Float): String = if (v == v.toInt().toFloat()) v.toInt().toString() else "%.1f".format(v)

@Composable
private fun ScrubReadout(series: List<ChartSeries>, index: Int, yUnit: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        series.forEach { s ->
            val v = s.values.getOrNull(index.coerceIn(0, s.values.size - 1)) ?: return@forEach
            Text(formatAxisValue(v) + yUnit, color = s.color, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun Legend(series: List<ChartSeries>) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        series.filter { it.label.isNotEmpty() }.forEach { s ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(10.dp).background(s.color))
                Text(" ${s.label}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
