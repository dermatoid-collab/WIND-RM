package com.windrm.app.gpx

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document

/**
 * The folder the user picked for GPX (and TCX) files, reached through Android's document picker: any
 * provider that offers folders works (device storage, Google Drive, Dropbox, an SD card...), and the
 * permission to its tree survives restarts. Everything here is blocking I/O: call it off the main thread.
 */
object GpxFolder {

    /** One GPX file found in the folder; [folder] names the subfolder it sits in, null at the top level. */
    data class Entry(
        val documentUri: Uri,
        val name: String,
        val lastModifiedMs: Long?,
        val sizeBytes: Long?,
        val folder: String?,
    )

    private const val GPX_MIME = "application/gpx+xml"
    private const val TCX_MIME = "application/vnd.garmin.tcx+xml"
    private const val MAX_DEPTH = 3
    private const val MAX_FILES = 500

    private fun isTrackFile(name: String, mime: String): Boolean =
        name.endsWith(".gpx", ignoreCase = true) || name.endsWith(".tcx", ignoreCase = true) || mime == GPX_MIME || mime == TCX_MIME

    /** The folder's own name, or null when it can't be read (permission lost, provider gone). */
    fun displayName(context: Context, tree: Uri): String? = runCatching {
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        context.contentResolver.query(root, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()

    /** GPX and TCX files in the folder and its subfolders (a few levels deep), newest first. */
    fun listTracks(context: Context, tree: Uri): List<Entry> {
        val found = ArrayList<Entry>()
        val columns = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_SIZE,
        )

        fun walk(parentId: String, folderName: String?, depth: Int) {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
            val subfolders = ArrayList<Pair<String, String>>()
            context.contentResolver.query(children, columns, null, null, null)?.use { c ->
                while (c.moveToNext() && found.size < MAX_FILES) {
                    val id = c.getString(0) ?: continue
                    val name = c.getString(1) ?: continue
                    val mime = c.getString(2).orEmpty()
                    when {
                        mime == Document.MIME_TYPE_DIR -> if (depth < MAX_DEPTH) subfolders += id to name
                        isTrackFile(name, mime) -> found += Entry(
                            documentUri = DocumentsContract.buildDocumentUriUsingTree(tree, id),
                            name = name,
                            lastModifiedMs = if (c.isNull(3)) null else c.getLong(3),
                            sizeBytes = if (c.isNull(4)) null else c.getLong(4),
                            folder = folderName,
                        )
                    }
                }
            }
            subfolders.forEach { (id, name) -> walk(id, name, depth + 1) }
        }

        walk(DocumentsContract.getTreeDocumentId(tree), null, 0)
        return found.sortedByDescending { it.lastModifiedMs ?: 0L }
    }

    /** Creates [fileName] in the folder (the provider renames it if it already exists) and returns its uri, or null on failure. */
    fun write(context: Context, tree: Uri, fileName: String, content: String): Uri? {
        val root = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        // A real GPX type, not octet-stream: providers append ".bin" to octet-stream files whose extension they don't know.
        val created = DocumentsContract.createDocument(context.contentResolver, root, GPX_MIME, fileName) ?: return null
        val stream = context.contentResolver.openOutputStream(created, "wt") ?: return null
        stream.use { it.write(content.toByteArray(Charsets.UTF_8)) }
        return created
    }
}
