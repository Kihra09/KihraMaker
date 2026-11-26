package com.kihra.maker.data

import android.content.Context
import java.io.File
import java.security.MessageDigest

/** Screenshots taken in-game: files/screenshots/<game key>/<time>.png */
object Shots {
    data class Shot(val file: File, val gameKey: String, val time: Long)

    fun key(gameId: String): String =
        MessageDigest.getInstance("SHA-1").digest(gameId.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(10)

    private fun root(context: Context) = File(AppPaths.base(context), "screenshots")

    @JvmStatic
    fun newFile(context: Context, gameId: String): File {
        val dir = File(root(context), key(gameId)).apply { mkdirs() }
        return File(dir, "${System.currentTimeMillis()}.png")
    }

    /** Newest first. */
    fun list(context: Context): List<Shot> {
        val dirs = root(context).listFiles { f -> f.isDirectory } ?: return emptyList()
        return dirs.flatMap { d ->
            (d.listFiles { f -> f.isFile && f.extension == "png" } ?: emptyArray()).map {
                Shot(it, d.name, it.nameWithoutExtension.toLongOrNull() ?: it.lastModified())
            }
        }.sortedByDescending { it.time }
    }
}
