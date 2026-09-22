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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.ceil
import kotlin.math.floor

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
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 8.dp))

        TimeAxisLabels(xLabels)

        val allValues = series.flatMap { it.values }.ifEmpty { listOf(0f) }
        val step = niceStep(allValues)
        val rawMin = yRangeOverride?.start ?: (floor(allValues.min() / step) * step)
        val rawMax = yRangeOverride?.endInclusive ?: (ceil(allValues.max() / step) * step)
        val min = if (rawMin == rawMax) rawMin - 1f else rawMin
        val max = if (rawMin == rawMax) rawMax + 1f else rawMax

        Row(Modifier.fillMaxWidth().height(chartHeight)) {
            YAxisLabels(min, max, yUnit, Modifier.width(Y_AXIS_WIDTH), alignEnd = false, height = chartHeight)
            Box(Modifier.weight(1f).fillMaxWidth().height(chartHeight)) {
                Canvas(modifier = Modifier.fillMaxWidth().height(chartHeight)) {
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
                    val stepCount = 4
                    for (i in 0..stepCount) {
                        val y = size.height * i / stepCount
                        drawLine(
                            color = Color.LightGray.copy(alpha = 0.4f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }
                    series.forEach { s ->
                        if (s.values.size < 2) return@forEach
                        val n = s.values.size
                        val stepX = size.width / (n - 1)
                        val linePath = Path()
                        s.values.forEachIndexed { i, v ->
                            val x = i * stepX
                            val fraction = ((v - min) / (max - min)).coerceIn(0f, 1f)
                            val y = size.height * (1f - fraction)
                            if (i == 0) linePath.moveTo(x, y) else linePath.lineTo(x, y)
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
                        drawPath(linePath, color = s.color, style = Stroke(width = 2.5.dp.toPx()))
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
