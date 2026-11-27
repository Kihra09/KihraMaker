package com.kihra.maker.data

import android.content.Context
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** One zip with the library, settings, saves, control layouts, play time and screenshots. */
object AppBackup {
    // Unpacked games, the RTP, caches and logs are big or can be rebuilt, so they stay out.
    private val skip = setOf("games", "rtp", "icons", "logs")

    fun export(context: Context, out: OutputStream): Int {
        val base = AppPaths.base(context)
        var n = 0
        ZipOutputStream(out).use { z ->
            base.walkTopDown().filter { it.isFile }.forEach { f ->
                val rel = f.relativeTo(base).invariantSeparatorsPath
                if (rel.substringBefore('/') in skip) return@forEach
                if (rel.startsWith("session.") || rel.endsWith(".tmp")) return@forEach
                z.putNextEntry(ZipEntry(rel))
                f.inputStream().use { it.copyTo(z) }
                z.closeEntry()
                n++
            }
        }
        return n
    }

    /** Returns the number of files restored, or -1 when the zip isn't a KihraMaker backup. */
    fun restore(context: Context, input: InputStream): Int {
        val base = AppPaths.base(context)
        val canonical = base.canonicalPath + File.separator
        var n = 0
        var sawLibrary = false
        ZipInputStream(input).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.isDirectory) continue
                val rel = e.name.replace('\\', '/')
                if (rel.substringBefore('/') in skip) continue
                val out = File(base, rel)
                if (!out.canonicalPath.startsWith(canonical)) continue // zip-slip
                if (rel == "library.json") sawLibrary = true
                out.parentFile?.mkdirs()
                out.outputStream().use { z.copyTo(it) }
                n++
            }
        }
        return if (sawLibrary) n else -1
    }
}
