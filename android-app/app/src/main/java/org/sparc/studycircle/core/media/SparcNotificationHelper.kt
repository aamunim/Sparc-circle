package org.sparc.studycircle.core.media

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import org.sparc.studycircle.MainActivity
import org.sparc.studycircle.R
import java.time.LocalTime
import java.util.concurrent.atomic.AtomicInteger

object SparcNotificationHelper {
    private const val CHANNEL_ID = "sparc_academic_alerts"
    private val nextId = AtomicInteger(2001)

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SPARC Study Circle Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Pokes, Study Circle Invites, Mentor Evaluations, Wallet Updates & Live Sessions"
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }

    /** Respects 10:00 PM – 7:00 AM Quiet Hours per SPARC academic etiquette. */
    fun isQuietHoursNow(now: LocalTime = LocalTime.now()): Boolean =
        now.hour >= 22 || now.hour < 7

    fun dispatchAlert(context: Context, title: String, body: String) {
        if (isQuietHoursNow()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(nextId.getAndIncrement(), notification)
        }
    }
}
