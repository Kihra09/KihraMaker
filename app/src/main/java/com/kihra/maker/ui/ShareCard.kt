package com.kihra.maker.ui

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.FileProvider
import java.io.File

/** Draws a small picture of the player's stats and hands it to the share sheet. */
object ShareCard {
    fun share(context: Context, streak: Int, longest: Int, totalText: String, sessions: Int) {
        runCatching {
            val w = 1080
            val h = 600
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(bmp)
            c.drawColor(Color.parseColor("#1E1F20"))
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.color = Color.parseColor("#00A86B")
            c.drawRoundRect(RectF(40f, 40f, w - 40f, 110f), 35f, 35f, p)
            p.color = Color.WHITE
            p.typeface = Typeface.DEFAULT_BOLD
            p.textSize = 44f
            c.drawText("KihraMaker", 70f, 90f, p)
            p.color = Color.parseColor("#E3E3E3")
            p.textSize = 150f
            c.drawText("$streak-day streak", 60f, 290f, p)
            p.typeface = Typeface.DEFAULT
            p.color = Color.parseColor("#C4C7C5")
            p.textSize = 46f
            c.drawText("Longest streak: $longest days", 60f, 390f, p)
            c.drawText("Total play time: $totalText", 60f, 460f, p)
            c.drawText("$sessions sessions", 60f, 530f, p)
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            val file = File(dir, "kihra_stats.png")
            file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            val uri = FileProvider.getUriForFile(context, context.packageName + ".share", file)
            val send = Intent(Intent.ACTION_SEND)
                .setType("image/png")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(send, "Share your stats").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}
