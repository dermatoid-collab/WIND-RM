package com.windrm.app.ui.builder

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.windrm.app.R
import com.windrm.app.gpx.GpxWriter
import com.windrm.app.model.Route
import java.io.File

/** Writes [route] as a GPX file named after it and opens the share sheet (Strava, Garmin Connect, Drive, mail...). */
fun shareGpx(context: Context, route: Route) {
    val dir = File(context.cacheDir, "shared").apply { mkdirs() }
    val file = File(dir, GpxWriter.fileName(route.name))
    file.writeText(GpxWriter.write(route))

    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/gpx+xml"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, route.name)
        clipData = ClipData.newRawUri(file.name, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, context.getString(R.string.share)))
}
