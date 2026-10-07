package com.windrm.app.gpx

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.windrm.app.model.Route
import com.windrm.app.repository.RouteRepository
import java.io.BufferedInputStream

/** Shared by every screen that offers "import a track file" (Home's Files row, the Recent tab's FAB, the Files tab): GPX or TCX, told apart by name or content. */
object GpxUriImporter {
    suspend fun import(context: Context, uri: Uri, routeRepository: RouteRepository): Route {
        val name = queryDisplayName(context, uri) ?: "Imported route"
        val fallbackName = name.substringBeforeLast('.')
        val route = context.contentResolver.openInputStream(uri)?.use { raw ->
            val stream = BufferedInputStream(raw)
            if (isTcx(stream, name)) TcxParser.parse(stream, fallbackName) else GpxParser.parse(stream, fallbackName)
        } ?: error("Couldn't open the selected file")
        val id = routeRepository.saveRoute(route)
        return route.copy(id = id)
    }

    /** A ".tcx" name, or a TCX root element in the first few KB (exports are often renamed or come as ".xml"). */
    private fun isTcx(stream: BufferedInputStream, name: String): Boolean {
        if (name.endsWith(".tcx", ignoreCase = true)) return true
        stream.mark(HEAD_BYTES)
        val head = ByteArray(HEAD_BYTES)
        val read = stream.read(head)
        stream.reset()
        return read > 0 && String(head, 0, read, Charsets.ISO_8859_1).contains("TrainingCenterDatabase")
    }

    private const val HEAD_BYTES = 4096

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
