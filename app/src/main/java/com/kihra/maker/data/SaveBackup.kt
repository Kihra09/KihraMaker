package com.kihra.maker.data

import android.content.Context
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Copies of a game's save files, kept in files/backups/<game>/<time>/ (the last few only). */
object SaveBackup {
    private const val KEEP = 5
    private val saveName = Regex("^save.*\\.(rvdata2|rvdata|rxdata)$", RegexOption.IGNORE_CASE)

    data class Backup(val dir: File, val time: Long, val files: Int)

    fun saveFiles(game: Game): List<File> =
        File(game.path).listFiles { f -> f.isFile && saveName.matches(f.name) }?.toList() ?: emptyList()

    private fun root(context: Context, game: Game) =
        File(File(AppPaths.base(context), "backups"), Shots.key(game.id))

    fun list(context: Context, game: Game): List<Backup> =
        (root(context, game).listFiles { f -> f.isDirectory } ?: emptyArray())
            .mapNotNull { d ->
                val t = d.name.toLongOrNull() ?: return@mapNotNull null
                Backup(d, t, d.listFiles()?.size ?: 0)
            }
            .sortedByDescending { it.time }

    /** Makes a backup when the saves changed since the last one. Returns true when one was made. */
    fun backup(context: Context, game: Game): Boolean = runCatching {
        val files = saveFiles(game)
        if (files.isEmpty()) return false
        val newest = files.maxOf { it.lastModified() }
        val latest = list(context, game).firstOrNull()
        val latestSaved = latest?.dir?.listFiles()?.maxOfOrNull { it.lastModified() } ?: 0L
        // Copies keep the original time, so "unchanged" means nothing is newer than the last copy.
        if (latest != null && newest <= latestSaved) return false
        val dir = File(root(context, game), System.currentTimeMillis().toString()).apply { mkdirs() }
        files.forEach { it.copyTo(File(dir, it.name), overwrite = true).setLastModified(it.lastModified()) }
        list(context, game).drop(KEEP).forEach { it.dir.deleteRecursively() }
        true
    }.getOrDefault(false)

    /** Puts a backup's files back into the game's save folder (the current saves are backed up first). */
    fun restore(context: Context, game: Game, backup: Backup): Boolean = runCatching {
        backup(context, game)
        val target = File(game.path)
        backup.dir.listFiles()?.forEach { it.copyTo(File(target, it.name), overwrite = true) }
        true
    }.getOrDefault(false)

    fun exportZip(game: Game, out: OutputStream): Int {
        val files = saveFiles(game)
        ZipOutputStream(out).use { z ->
            files.forEach { f ->
                z.putNextEntry(ZipEntry(f.name))
                f.inputStream().use { it.copyTo(z) }
                z.closeEntry()
            }
        }
        return files.size
    }

    /** Takes the save files out of a zip (any folder inside it) and puts them in the game's save folder. */
    fun importZip(context: Context, game: Game, input: InputStream): Int {
        backup(context, game)
        val target = File(game.path).apply { mkdirs() }
        var n = 0
        ZipInputStream(input).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                if (e.isDirectory) continue
                val name = e.name.replace('\\', '/').substringAfterLast('/')
                if (!saveName.matches(name)) continue
                File(target, name).outputStream().use { z.copyTo(it) }
                n++
            }
        }
        return n
    }
}
