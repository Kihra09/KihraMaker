package com.kihra.maker.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.nio.charset.Charset
import java.util.zip.ZipInputStream

/** Unpacks a game .zip into app storage so the engine can read it as a normal folder. */
object ZipImporter {

    fun gamesRoot(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, "games").apply { mkdirs() }

    /**
     * @param onProgress called with the number of megabytes written so far (not on the main thread).
     */
    fun import(context: Context, uri: Uri, onProgress: (Long) -> Unit): GameScanner.Result {
        val base = displayName(context, uri).removeSuffix(".zip").removeSuffix(".ZIP")
            .replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().ifEmpty { "game" }
        val target = uniqueDir(gamesRoot(context), base)

        // UTF-8 first; zips made on Windows with local-language names fall back to other charsets.
        val charsets = listOf(Charsets.UTF_8, Charset.forName("windows-31j"), Charset.forName("IBM437"))
        var lastError: Throwable? = null
        for (cs in charsets) {
            target.deleteRecursively()
            target.mkdirs()
            try {
                extract(context, uri, target, cs, onProgress)
                lastError = null
                break
            } catch (e: IllegalArgumentException) {
                lastError = e // bad file-name encoding, try the next charset
            } catch (e: Exception) {
                target.deleteRecursively()
                return GameScanner.Result.Fail("Couldn't unpack the zip: ${e.message ?: e.javaClass.simpleName}")
            }
        }
        if (lastError != null) {
            target.deleteRecursively()
            return GameScanner.Result.Fail("Couldn't read file names in the zip.")
        }

        val result = GameScanner.scan(target, extracted = true, extractRoot = target.absolutePath)
        if (result is GameScanner.Result.Fail) target.deleteRecursively()
        return result
    }

    private fun extract(
        context: Context,
        uri: Uri,
        target: File,
        charset: Charset,
        onProgress: (Long) -> Unit,
    ) {
        val canonicalTarget = target.canonicalPath + File.separator
        var written = 0L
        var lastReported = -1L
        val buffer = ByteArray(64 * 1024)

        val input = context.contentResolver.openInputStream(uri) ?: error("Can't open the file")
        ZipInputStream(input.buffered(), charset).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name.replace('\\', '/')
                val out = File(target, name)
                // Zip-slip protection: never write outside the target folder.
                if (!(out.canonicalPath + if (entry.isDirectory) File.separator else "").startsWith(canonicalTarget)) {
                    continue
                }
                if (entry.isDirectory) {
                    out.mkdirs()
                    continue
                }
                out.parentFile?.mkdirs()
                out.outputStream().use { os ->
                    while (true) {
                        val n = zip.read(buffer)
                        if (n < 0) break
                        os.write(buffer, 0, n)
                        written += n
                        val mb = written shr 20
                        if (mb != lastReported) {
                            lastReported = mb
                            onProgress(mb)
                        }
                    }
                }
            }
        }
    }

    private fun displayName(context: Context, uri: Uri): String {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) return c.getString(0) }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "game"
    }

    private fun uniqueDir(root: File, name: String): File {
        var dir = File(root, name)
        var i = 2
        while (dir.exists()) dir = File(root, "$name ($i)").also { i++ }
        return dir
    }
}
