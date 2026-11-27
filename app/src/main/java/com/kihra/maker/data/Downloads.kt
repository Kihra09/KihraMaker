package com.kihra.maker.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.OutputStream

/** Saves a file into the phone's Download/KihraMaker folder so it can be found in any file manager. */
object Downloads {
    /** Returns the folder name shown to the user, or null when saving failed. */
    fun save(context: Context, name: String, mime: String, write: (OutputStream) -> Unit): String? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mime)
                put(MediaStore.Downloads.RELATIVE_PATH, "Download/KihraMaker")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)!!
            context.contentResolver.openOutputStream(uri)!!.use(write)
        } else {
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "KihraMaker")
            dir.mkdirs()
            File(dir, name).outputStream().use(write)
        }
        "Download/KihraMaker"
    }.getOrNull()
}
