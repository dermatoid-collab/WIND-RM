package com.windrm.app.ui.forecast

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.windrm.app.R
import com.windrm.app.domain.ActivityType
import com.windrm.app.domain.PacingMode
import com.windrm.app.model.RouteForecastResult
import com.windrm.app.model.aqiLabel
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ShareFormat { JPG, PDF }

// PDF viewers reject pages taller than 14 400 pt (200 in), so very long pages are scaled down to fit.
private const val PDF_PAGE_WIDTH_PT = 595
private const val PDF_MAX_SIDE_PT = 14_400

/**
 * Stitches the captured page parts (pinned map, then the scrolling gauges and charts) under a
 * short header into one image, writes it as a JPG or a single-page PDF and opens the share sheet.
 */
suspend fun shareForecastPage(
    context: Context,
    result: RouteForecastResult,
    parts: List<Bitmap>,
    backgroundArgb: Int,
    textArgb: Int,
    format: ShareFormat,
) {
    val file = withContext(Dispatchers.Default) {
        val page = stitch(context, result, parts.map(::softwareCopy), backgroundArgb, textArgb)
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        when (format) {
            ShareFormat.JPG -> File(dir, "windrm-forecast.jpg").also { out ->
                out.outputStream().use { page.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            }
            ShareFormat.PDF -> File(dir, "windrm-forecast.pdf").also { out -> writePdf(page, out) }
        }.also { page.recycle() }
    }

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = if (format == ShareFormat.JPG) "image/jpeg" else "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TEXT, forecastSummaryText(result))
        clipData = ClipData.newRawUri(file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.share)))
}

fun forecastSummaryText(result: RouteForecastResult): String {
    val formatter = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm").withZone(ZoneId.systemDefault())
    val first = result.points.firstOrNull()?.weather
    return buildString {
        appendLine("WIND-RM · ${result.route.name}")
        appendLine("Starting: ${formatter.format(result.startTime)}")
        appendLine("%.1f km · %.0f m↑".format(result.route.distanceKm, result.route.elevationGainM))
        appendLine("%s · %s km/h · %s pacing".format(activityLabel(result), formatKmh(result.avgSpeedKmh), pacingLabel(result)))
        windEffectLabel(result)?.let { appendLine("Wind effect on the ride time: $it") }
        if (first != null) {
            appendLine("At the start: ${first.temperatureC.roundToInt()}°C, wind ${first.windSpeedKmh.roundToInt()} km/h")
        }
        result.peakAqi?.let { appendLine("Peak AQI: ${it.europeanAqi.roundToInt()} (${aqiLabel(it.europeanAqi)})") }
    }
}

/** Layers come back as hardware bitmaps, which a software Canvas (and PdfDocument) can't draw. */
private fun softwareCopy(bitmap: Bitmap): Bitmap =
    if (bitmap.config == Bitmap.Config.HARDWARE) bitmap.copy(Bitmap.Config.ARGB_8888, false) else bitmap

private fun stitch(context: Context, result: RouteForecastResult, parts: List<Bitmap>, background: Int, text: Int): Bitmap {
    val density = context.resources.displayMetrics.density
    val width = parts.maxOf { it.width }
    val margin = 16 * density
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = text
        textSize = 20 * density
        typeface = Typeface.DEFAULT_BOLD
    }
    val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = text
        alpha = 190
        textSize = 14 * density
    }
    val formatter = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm").withZone(ZoneId.systemDefault())
    val subtitle = "%s · %.1f km · %.0f m↑ · %s km/h · %s".format(
        formatter.format(result.startTime), result.route.distanceKm, result.route.elevationGainM,
        formatKmh(result.avgSpeedKmh), pacingLabel(result) + (windEffectLabel(result)?.let { " ($it)" } ?: ""),
    )
    val headerHeight = (margin * 2 + titlePaint.textSize + subtitlePaint.textSize * 1.6f).toInt()

    val page = Bitmap.createBitmap(width, headerHeight + parts.sumOf { it.height }, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(page)
    canvas.drawColor(background)
    canvas.drawText(ellipsize(result.route.name, titlePaint, width - 2 * margin), margin, margin + titlePaint.textSize, titlePaint)
    canvas.drawText(subtitle, margin, margin + titlePaint.textSize + subtitlePaint.textSize * 1.4f, subtitlePaint)
    var y = headerHeight.toFloat()
    for (part in parts) {
        canvas.drawBitmap(part, 0f, y, null)
        y += part.height
    }
    return page
}

private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
    if (paint.measureText(text) <= maxWidth) return text
    var end = text.length
    while (end > 0 && paint.measureText(text, 0, end) + paint.measureText("…") > maxWidth) end--
    return text.substring(0, end) + "…"
}

private fun writePdf(page: Bitmap, out: File) {
    var pageWidth = PDF_PAGE_WIDTH_PT.toFloat()
    var pageHeight = pageWidth * page.height / page.width
    if (pageHeight > PDF_MAX_SIDE_PT) {
        val shrink = PDF_MAX_SIDE_PT / pageHeight
        pageWidth *= shrink
        pageHeight = PDF_MAX_SIDE_PT.toFloat()
    }
    val document = PdfDocument()
    try {
        val info = PdfDocument.PageInfo.Builder(pageWidth.roundToInt(), pageHeight.roundToInt(), 1).create()
        val pdfPage = document.startPage(info)
        pdfPage.canvas.drawBitmap(
            page,
            null,
            Rect(0, 0, info.pageWidth, info.pageHeight),
            Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG),
        )
        document.finishPage(pdfPage)
        out.outputStream().use { document.writeTo(it) }
    } finally {
        document.close()
    }
}

/** How the arrival times were estimated, as shown on the map pill and in shared files: Constant, Realistic or CAI (trekking). */
fun pacingLabel(result: RouteForecastResult): String = when {
    result.pacing == PacingMode.CONSTANT -> "Constant"
    result.activity == ActivityType.TREK -> "CAI"
    result.pacing == PacingMode.REALISTIC_WIND -> "Realistic + wind"
    else -> "Realistic"
}

/** "+14 min" or "-6 min": what the wind did to the ride time; null when the wind was not part of the estimate. */
fun windEffectLabel(result: RouteForecastResult): String? = result.windEffectSeconds?.let { seconds ->
    val minutes = Math.round(seconds / 60.0).toInt()
    if (minutes == 0) "±0 min" else "%+d min".format(minutes)
}

fun activityLabel(result: RouteForecastResult): String = if (result.activity == ActivityType.TREK) "Trekking" else "Ride"

/** "29.5", or "4" for a whole number. */
fun formatKmh(kmh: Double): String = if (kmh == kmh.toInt().toDouble()) kmh.toInt().toString() else "%.1f".format(kmh)
