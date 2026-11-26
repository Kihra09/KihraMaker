package com.kihra.maker.data

import android.net.Uri
import android.provider.DocumentsContract
import java.io.File

object PathUtils {
    /**
     * Turns a folder picked with the system picker into a real file path,
     * e.g. primary:Games/Foo -> /storage/emulated/0/Games/Foo. Returns null when
     * the folder is on a provider that has no real path (cloud drives, etc).
     */
    fun treeUriToPath(uri: Uri): String? {
        val docId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull() ?: return null
        return idToPath(docId)
    }

    /** Same for a single picked file, e.g. a .zip. */
    fun documentUriToPath(uri: Uri): String? {
        val docId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return null
        return idToPath(docId)
    }

    private fun idToPath(docId: String): String? {
        val sep = docId.indexOf(':')
        if (sep < 0) return null
        val volume = docId.substring(0, sep)
        val rel = docId.substring(sep + 1)
        val base = when {
            volume == "primary" -> "/storage/emulated/0"
            volume == "raw" -> return rel.takeIf { it.startsWith("/") }
            volume.matches(Regex("[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}")) -> "/storage/$volume"
            else -> return null
        }
        return if (rel.isEmpty()) base else File(base, rel).path
    }
}
