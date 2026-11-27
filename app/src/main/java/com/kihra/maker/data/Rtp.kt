package com.kihra.maker.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.zip.ZipInputStream

/** The RPG Maker runtime package (RTP): default graphics and sounds many games rely on. */
object Rtp {
    /** Where an imported RTP is kept: files/rtp */
    fun home(context: Context) = File(AppPaths.base(context), "rtp")

    /** An RTP folder has Graphics and Audio inside. */
    fun isValid(dir: File?): Boolean =
        dir != null && dir.isDirectory &&
            File(dir, "Graphics").isDirectory && File(dir, "Audio").isDirectory

    fun isValid(path: String): Boolean = path.isNotBlank() && isValid(File(path))

    /** Finds the folder that holds Graphics and Audio, up to three levels down. */
    fun findRoot(dir: File, depth: Int = 3): File? {
        if (isValid(dir)) return dir
        if (depth == 0) return null
        dir.listFiles { f -> f.isDirectory }?.sortedBy { it.name }?.forEach {
            findRoot(it, depth - 1)?.let { r -> return r }
        }
        return null
    }

    /**
     * Unpacks an RTP zip into files/rtp and returns the folder to use, or an error text.
     * Works from any file provider (no file access permission needed).
     */
    fun importZip(context: Context, uri: Uri, onProgress: (Long) -> Unit): Pair<File?, String?> {
        val target = home(context)
        return try {
            target.deleteRecursively()
            target.mkdirs()
            val canonicalTarget = target.canonicalPath + File.separator
            var bytes = 0L
            val input = context.contentResolver.openInputStream(uri) ?: return null to "Can't open that file."
            ZipInputStream(input).use { zin ->
                while (true) {
                    val e = zin.nextEntry ?: break
                    val name = e.name.replace('\\', '/')
                    val out = File(target, name)
                    if (!out.canonicalPath.startsWith(canonicalTarget)) continue // zip-slip
                    if (e.isDirectory) {
                        out.mkdirs()
                    } else {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { o ->
                            val buf = ByteArray(64 * 1024)
                            while (true) {
                                val n = zin.read(buf)
                                if (n < 0) break
                                o.write(buf, 0, n)
                                bytes += n
                            }
                        }
                        onProgress(bytes / (1024 * 1024))
                    }
                }
            }
            val root = findRoot(target)
            if (root == null) {
                target.deleteRecursively()
                null to "That zip has no Graphics and Audio folders, so it isn't an RTP."
            } else root to null
        } catch (e: Exception) {
            target.deleteRecursively()
            null to "Couldn't unpack the RTP: ${e.message ?: "unknown error"}"
        }
    }
}
