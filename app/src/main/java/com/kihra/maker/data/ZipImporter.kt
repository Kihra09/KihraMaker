package com.kihra.maker.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

/** Zip handling: checking a zip for direct play, and unpacking it into app storage. */
object ZipImporter {

    class Analysis(
        /** True when the engine can play straight from this zip. */
        val ok: Boolean,
        val reason: String,
        /** Most of the data is deflate-compressed (slower to stream than stored data). */
        val compressed: Boolean,
        val iniBytes: ByteArray?,
    )

    /**
     * Direct play needs: Game.ini at the top level of the zip, UTF-8 compatible names, no backslash
     * names, and only stored/deflated entries (the only methods the engine's zip reader supports).
     */
    fun analyze(zip: File): Analysis {
        return try {
            ZipFile(zip, Charsets.UTF_8).use { zf ->
                var ini: ZipEntry? = null
                var total = 0L
                var deflated = 0L
                for (e in zf.entries()) {
                    if (e.isDirectory) continue
                    if (e.name.contains('\\')) {
                        return Analysis(false, "The zip uses Windows-style paths.", false, null)
                    }
                    if (e.method != ZipEntry.STORED && e.method != ZipEntry.DEFLATED) {
                        return Analysis(false, "The zip uses an unsupported compression method.", false, null)
                    }
                    if (e.name.equals("Game.ini", ignoreCase = true)) ini = e
                    val size = e.size.coerceAtLeast(0)
                    total += size
                    if (e.method == ZipEntry.DEFLATED) deflated += size
                }
                if (ini == null) {
                    return Analysis(false, "Game.ini is inside a folder in the zip, not at the top.", false, null)
                }
                val bytes = zf.getInputStream(ini).use { it.readBytes() }
                Analysis(true, "", total > 0 && deflated.toDouble() / total > 0.5, bytes)
            }
        } catch (e: Exception) {
            Analysis(false, "Couldn't read the zip directly (${e.javaClass.simpleName}).", false, null)
        }
    }

    /** Copies the zip's Game.ini into [dest]: the engine reads it before it mounts any archive. */
    fun extractIni(zip: File, dest: File): Boolean = runCatching {
        ZipFile(zip, Charsets.UTF_8).use { zf ->
            val entry = zf.entries().asSequence()
                .firstOrNull { !it.isDirectory && it.name.equals("Game.ini", ignoreCase = true) }
                ?: return false
            dest.parentFile?.mkdirs()
            zf.getInputStream(entry).use { input -> dest.outputStream().use { input.copyTo(it) } }
            true
        }
    }.getOrDefault(false)

    /** Moves a zip game's saves into the unpacked folder (never overwrites existing files). */
    fun copySaves(saveDir: File, targetDir: File) {
        val skip = setOf("game.ini", "mkxp.json")
        saveDir.walkTopDown().filter { it.isFile && it.name.lowercase() !in skip }.forEach { f ->
            val dest = File(targetDir, f.relativeTo(saveDir).path)
            if (!dest.exists()) {
                dest.parentFile?.mkdirs()
                runCatching { f.copyTo(dest) }
            }
        }
    }

    // ---------- unpacking ----------

    /** @param onProgress called with the megabytes written so far (not on the main thread). */
    fun import(context: Context, uri: Uri, onProgress: (Long) -> Unit): GameScanner.Result =
        importStream(
            context, displayName(context, uri),
            { context.contentResolver.openInputStream(uri) ?: error("Can't open the file") },
            onProgress,
        )

    fun importFile(context: Context, zip: File, onProgress: (Long) -> Unit): GameScanner.Result =
        importStream(context, zip.name, { zip.inputStream() }, onProgress)

    private fun importStream(
        context: Context,
        rawName: String,
        open: () -> InputStream,
        onProgress: (Long) -> Unit,
    ): GameScanner.Result {
        val base = rawName.removeSuffix(".zip").removeSuffix(".ZIP")
            .replace(Regex("[^A-Za-z0-9._ -]"), "_").trim().ifEmpty { "game" }
        val target = uniqueDir(AppPaths.gamesRoot(context), base)

        // UTF-8 first; zips made on Windows with local-language names fall back to other charsets.
        val charsets = listOf(Charsets.UTF_8, Charset.forName("windows-31j"), Charset.forName("IBM437"))
        var lastError: Throwable? = null
        for (cs in charsets) {
            target.deleteRecursively()
            target.mkdirs()
            try {
                extract(open, target, cs, onProgress)
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
        open: () -> InputStream,
        target: File,
        charset: Charset,
        onProgress: (Long) -> Unit,
    ) {
        val canonicalTarget = target.canonicalPath + File.separator
        var written = 0L
        var lastReported = -1L
        val buffer = ByteArray(64 * 1024)

        ZipInputStream(open().buffered(), charset).use { zip ->
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
