package com.example.tieniiltempo.core

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Notifier {

    /**
     * Mostra una notifica.
     *
     * @param channelId canale su cui notificare. Se non esiste lo crea al volo con nome=channelId.
     * @param contentIntent PendingIntent opzionale per aprire una schermata quando toccata.
     * @param notificationId id della notifica (di default un valore from timestamp)
     */
    fun notify(
        ctx: Context,
        title: String,
        body: String,
        channelId: String = "chat_default",
        @DrawableRes smallIcon: Int = android.R.drawable.ic_dialog_email,
        contentIntent: PendingIntent? = null,
        notificationId: Int = ((System.currentTimeMillis() and 0x7FFFFFFF).toInt())
    ) {
        // Android 13+ serve permesso
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        ensureChannel(ctx, channelId)

        val builder = NotificationCompat.Builder(ctx, channelId)
            .setSmallIcon(smallIcon)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        if (contentIntent != null) builder.setContentIntent(contentIntent)

        try {
            NotificationManagerCompat.from(ctx).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // permesso rifiutato nessun crash
        }
    }

    private fun ensureChannel(ctx: Context, id: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(id) != null) return

        val ch = NotificationChannel(
            id, id, NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifiche $id"
            enableLights(true)
            lightColor = Color.CYAN
            enableVibration(true)
        }
        nm.createNotificationChannel(ch)
    }
}