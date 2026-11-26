package com.kihra.maker.data

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * Everything the app stores lives in Android/data/kihra.kihramaker.app/files/
 *   library.json      game list, per-game settings, RTP folder
 *   saves/<game>/     saves + generated config of games played straight from a zip
 *   games/<game>/     games unpacked from a zip (their saves are inside)
 */
object AppPaths {
    fun base(context: Context): File =
        (context.getExternalFilesDir(null) ?: context.filesDir).apply { mkdirs() }

    fun libraryFile(context: Context) = File(base(context), "library.json")

    fun gamesRoot(context: Context) = File(base(context), "games").apply { mkdirs() }

    /** Writable folder for a zip game: it becomes the engine's working directory. */
    fun saveDirFor(context: Context, zip: File): File {
        val safe = zip.nameWithoutExtension.replace(Regex("[^A-Za-z0-9._-]"), "_").take(40)
        val hash = MessageDigest.getInstance("SHA-1").digest(zip.absolutePath.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(8)
        return File(File(base(context), "saves"), "$safe-$hash").apply { mkdirs() }
    }
}
