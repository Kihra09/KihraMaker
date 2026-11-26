package com.kihra.maker.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File

/** Small, memory-friendly image loading for the launcher UI. */
object Thumbs {
    private val cache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun load(file: File, maxPx: Int): Bitmap? {
        val key = "${file.absolutePath}|${file.lastModified()}|$maxPx"
        cache.get(key)?.let { return it }
        val bmp = decode(file, maxPx) ?: return null
        cache.put(key, bmp)
        return bmp
    }

    fun decode(file: File, maxPx: Int): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxPx && bounds.outHeight / (sample * 2) >= maxPx) sample *= 2
        BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()
}
