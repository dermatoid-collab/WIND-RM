package com.windrm.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.windrm.app.R
import com.windrm.app.domain.RelativeWind
import com.windrm.app.domain.RelativeWindProfile
import com.windrm.app.domain.WindKind
import com.windrm.app.ui.theme.CrosswindColor
import com.windrm.app.ui.theme.HeadwindColor
import com.windrm.app.ui.theme.TailwindColor
import kotlin.math.abs
import kotlin.math.roundToInt

fun WindKind.color(): Color = when (this) {
    WindKind.HEAD -> HeadwindColor
    WindKind.TAIL -> TailwindColor
    WindKind.CROSS -> CrosswindColor
}

@Composable
fun WindKind.label(): String = stringResource(
    when (this) {
        WindKind.HEAD -> R.string.headwind
        WindKind.TAIL -> R.string.tailwind
        WindKind.CROSS -> R.string.crosswind
    },
)

/** The size of the wind that matters for the kind: along the track for head and tail, across it for a crosswind. */
fun RelativeWind.mainKmh(): Double = if (kind == WindKind.CROSS) abs(crossKmh) else abs(headKmh)

/** Tooltip text for a head(+)/tail(-) value on the chart, e.g. "Headwind 14 km/h". */
@Composable
fun headTailTooltip(): (Float) -> String {
    val head = stringResource(R.string.headwind)
    val tail = stringResource(R.string.tailwind)
    return { v -> "${if (v >= 0f) head else tail} ${abs(v).roundToInt()} km/h" }
}

/**
 * One line under the wind charts saying how the wind meets the rider at the scrubbed point: an arrow drawn as
 * the rider sees it (heading up, so an arrow pointing down is a headwind), the kind of wind and its size.
 * Without a scrub position it shows how the route splits between head, cross and tail wind. The height is
 * fixed, so the page doesn't jump when a finger lands on a chart.
 */
@Composable
fun RelativeWindReadout(wind: RelativeWind?, profile: RelativeWindProfile, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    val tint = wind?.kind?.color() ?: MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(tint.copy(alpha = 0.13f), shape)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (wind == null) {
            Column(Modifier.weight(1f)) {
                Text(
                    "%s %d%%  ·  %s %d%%  ·  %s %d%%".format(
                        stringResource(R.string.headwind), (profile.share(WindKind.HEAD) * 100).roundToInt(),
                        stringResource(R.string.crosswind), (profile.share(WindKind.CROSS) * 100).roundToInt(),
                        stringResource(R.string.tailwind), (profile.share(WindKind.TAIL) * 100).roundToInt(),
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.relative_wind_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            return@Row
        }
        val color = wind.kind.color()
        RiderRelativeArrow(wind.flowRotationDeg, color, Modifier.size(40.dp))
        Column(Modifier.weight(1f)) {
            Text(wind.kind.label(), color = color, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 1)
            val side = if (wind.fromRight) "right" else "left"
            val detail = if (wind.kind == WindKind.CROSS) {
                "wind %d km/h from the %s · %d %s component".format(
                    wind.speedKmh.roundToInt(), side, abs(wind.headKmh).roundToInt(), if (wind.headKmh >= 0) "head" else "tail",
                )
            } else {
                "wind %d km/h · %d° off your line · %d cross from the %s".format(
                    wind.speedKmh.roundToInt(), abs(wind.relDeg).roundToInt(), abs(wind.crossKmh).roundToInt(), side,
                )
            }
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        }
        Text(
            "%d".format(wind.mainKmh().roundToInt()),
            color = color,
            fontWeight = FontWeight.Bold,
            fontSize = 32.sp,
        )
        Text("km/h", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** An arrow rotated by [rotationDeg] clockwise from pointing up, i.e. from the rider's own direction of travel. */
@Composable
private fun RiderRelativeArrow(rotationDeg: Double, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        val half = size.minDimension * 0.36f
        val head = size.minDimension * 0.2f
        val stroke = Stroke(width = size.minDimension * 0.09f, cap = StrokeCap.Round)
        rotate(rotationDeg.toFloat(), pivot = c) {
            drawLine(color, Offset(c.x, c.y + half), Offset(c.x, c.y - half), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(color, Offset(c.x, c.y - half), Offset(c.x - head, c.y - half + head), strokeWidth = stroke.width, cap = StrokeCap.Round)
            drawLine(color, Offset(c.x, c.y - half), Offset(c.x + head, c.y - half + head), strokeWidth = stroke.width, cap = StrokeCap.Round)
        }
    }
}
