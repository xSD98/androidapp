package com.example.tieniiltempo.core

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlin.random.Random

object Notifier {
    private const val CHANNEL_ID = "chat_default"
    private const val CHANNEL_NAME = "Chat"
    private const val CHANNEL_DESC = "Notifiche chat e aggiornamenti"

    private fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = CHANNEL_DESC
                enableLights(true)
                lightColor = Color.CYAN
                enableVibration(true)
            }
            nm.createNotificationChannel(ch)
        }
    }

    fun notify(
        ctx: Context,
        title: String,
        body: String,
        notificationId: Int = Random.nextInt(),
        @DrawableRes smallIcon: Int = android.R.drawable.ic_dialog_email
    ) {
        // ✅ Check permesso su Android 13+ (evita SecurityException)
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                // Permesso non concesso: esci silenziosamente
                return
            }
        }

        ensureChannel(ctx)

        val builder = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        try {
            NotificationManagerCompat.from(ctx).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // L’utente ha negato il permesso o il permesso è stato revocato a caldo.
            // Non facciamo crashare l’app.
        }
    }
}