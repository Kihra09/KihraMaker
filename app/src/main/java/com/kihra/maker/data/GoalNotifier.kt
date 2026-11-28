package com.kihra.maker.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import java.time.LocalDate

/** One notification per day when the daily play goal is reached. Silent if the system doesn't allow notifications. */
object GoalNotifier {
    fun check(context: Context, goalMinutes: Int, todayMs: Long) {
        if (goalMinutes <= 0 || todayMs < goalMinutes * 60_000L) return
        runCatching {
            val prefs = context.getSharedPreferences("goal", Context.MODE_PRIVATE)
            val today = LocalDate.now().toString()
            if (prefs.getString("notified", "") == today) return
            if (Build.VERSION.SDK_INT >= 33 &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) return
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel("goals", "Play goals", NotificationManager.IMPORTANCE_DEFAULT))
            nm.notify(
                1,
                NotificationCompat.Builder(context, "goals")
                    .setSmallIcon(android.R.drawable.star_on)
                    .setContentTitle("Daily goal reached")
                    .setContentText("You played " + PlayLog.format(todayMs) + " today.")
                    .setAutoCancel(true)
                    .build(),
            )
            prefs.edit().putString("notified", today).apply()
        }
    }
}
