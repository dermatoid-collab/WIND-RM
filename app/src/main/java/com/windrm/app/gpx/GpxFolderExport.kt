package com.windrm.app.gpx

import android.content.Context
import android.net.Uri
import android.widget.Toast
import com.windrm.app.R
import com.windrm.app.model.Route
import com.windrm.app.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Keeps a GPX copy of a saved route in the folder chosen in Settings, and says how it went. Call it from the main thread. */
object GpxFolderExport {

    /** Writes [route] as GPX into the chosen folder and links the file to the route, so the Files tab opens it instead of importing it again. */
    suspend fun save(context: Context, settingsRepository: SettingsRepository, route: Route, folderName: String?) {
        val tree = settingsRepository.current().gpxFolderUri?.let(Uri::parse) ?: return
        val written = withContext(Dispatchers.IO) {
            runCatching { GpxFolder.write(context, tree, GpxWriter.fileName(route.name), GpxWriter.write(route)) }.getOrNull()
        }
        if (written != null) {
            settingsRepository.linkGpx(written.toString(), route.id)
            Toast.makeText(context, context.getString(R.string.builder_gpx_saved, folderName ?: ""), Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(context, context.getString(R.string.builder_gpx_failed), Toast.LENGTH_LONG).show()
        }
    }
}
