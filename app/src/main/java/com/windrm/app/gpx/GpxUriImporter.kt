package com.windrm.app.gpx

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.windrm.app.model.Route
import com.windrm.app.repository.RouteRepository

/** Shared by every screen that offers "import a GPX file" (Home's Files row, the Recent tab's FAB). */
object GpxUriImporter {
    suspend fun import(context: Context, uri: Uri, routeRepository: RouteRepository): Route {
        val name = queryDisplayName(context, uri) ?: "Imported route"
        val route = context.contentResolver.openInputStream(uri)?.use { stream ->
            GpxParser.parse(stream, name.substringBeforeLast('.'))
        } ?: error("Couldn't open the selected file")
        val id = routeRepository.saveRoute(route)
        return route.copy(id = id)
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        val cursor = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        return cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) it.getString(index) else null
            } else null
        }
    }
}
