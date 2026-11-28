package com.kihra.maker.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.util.zip.ZipFile

/**
 * Game icon: the game's title-screen picture (Graphics/Titles1 or Graphics/Titles), scaled down and
 * cached in files/icons. Returns null when there is none (encrypted archives, odd layouts); the UI
 * then shows a coloured letter instead.
 */
object GameArt {
    private val exts = setOf("png", "jpg", "jpeg")

    fun iconFile(context: Context, game: Game): File? {
        val out = File(File(AppPaths.base(context), "icons").apply { mkdirs() }, Shots.key(game.id) + ".png")
        if (out.isFile && out.length() > 0) return out
        val bmp = runCatching { extract(game) }.getOrNull() ?: return null
        return runCatching {
            val side = 256
            val ratio = minOf(side.toFloat() / bmp.width, side.toFloat() / bmp.height, 1f)
            val scaled = if (ratio < 1f) {
                Bitmap.createScaledBitmap(bmp, (bmp.width * ratio).toInt().coerceAtLeast(1), (bmp.height * ratio).toInt().coerceAtLeast(1), true)
            } else bmp
            out.outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 90, it) }
            out
        }.getOrNull()
    }

    /** Larger title picture used as the game's cover in grid and card views (falls back to the icon). */
    fun coverFile(context: Context, game: Game): File? {
        val out = File(File(AppPaths.base(context), "covers").apply { mkdirs() }, Shots.key(game.id) + ".jpg")
        if (out.isFile && out.length() > 0) return out
        val bmp = runCatching { extract(game) }.getOrNull() ?: return iconFile(context, game)
        return runCatching {
            val ratio = minOf(720f / bmp.width, 720f / bmp.height, 1f)
            val scaled = if (ratio < 1f) {
                Bitmap.createScaledBitmap(bmp, (bmp.width * ratio).toInt().coerceAtLeast(1), (bmp.height * ratio).toInt().coerceAtLeast(1), true)
            } else bmp
            out.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 88, it) }
            out
        }.getOrNull()
    }

    /** Uses a picture chosen by the player as the game's icon. */
    fun setCustomIcon(context: Context, game: Game, uri: android.net.Uri): Boolean = runCatching {
        val out = File(File(AppPaths.base(context), "icons").apply { mkdirs() }, Shots.key(game.id) + ".png")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 512) sample *= 2
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return false
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
        true
    }.getOrDefault(false)

    /** Goes back to the game's own title picture. */
    fun resetIcon(context: Context, game: Game) {
        File(File(AppPaths.base(context), "icons"), Shots.key(game.id) + ".png").delete()
    }

    private fun extract(game: Game): Bitmap? {
        if (game.rgss.isWeb) return extractWeb(game)
        val dirs = listOf("Graphics/Titles1", "Graphics/Titles")
        if (game.fromZip) {
            ZipFile(File(game.zipPath)).use { zip ->
                val entry = zip.entries().asSequence().filter { !it.isDirectory }.filter { e ->
                    val n = e.name.replace('\\', '/')
                    dirs.any { n.startsWith("$it/", ignoreCase = true) } && n.substringAfterLast('.', "").lowercase() in exts
                }.sortedBy { it.name }.firstOrNull() ?: return null
                return zip.getInputStream(entry).use { BitmapFactory.decodeStream(it) }
            }
        }
        for (d in dirs) {
            val folder = File(game.path, d)
            val img = folder.listFiles { f -> f.isFile && f.extension.lowercase() in exts }?.minByOrNull { it.name }
            if (img != null) return BitmapFactory.decodeFile(img.absolutePath)
        }
        return null
    }

    /** MV/MZ: title picture named in data/System.json, else the game's icon. */
    private fun extractWeb(game: Game): Bitmap? {
        val base = File(game.path)
        val root = if (File(base, "www/index.html").isFile) File(base, "www") else base
        val title = runCatching {
            val j = org.json.JSONObject(File(root, "data/System.json").readText())
            j.optString("title1Name")
        }.getOrDefault("")
        val candidates = mutableListOf<File>()
        if (title.isNotEmpty()) exts.forEach { candidates += File(root, "img/titles1/$title.$it") }
        exts.forEach { candidates += File(root, "icon/icon.$it") }
        File(root, "img/titles1").listFiles()?.sortedBy { it.name }?.let { candidates += it }
        val f = candidates.firstOrNull { it.isFile && it.extension.lowercase() in exts } ?: return null
        return BitmapFactory.decodeFile(f.absolutePath)
    }
}
