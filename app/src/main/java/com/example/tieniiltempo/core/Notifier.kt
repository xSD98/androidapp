package com.example.tieniiltempo.core

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Notifier {
    private const val CHANNEL_ID = "tieniiltempo_default"
    private const val CHANNEL_NAME = "Tieni il tempo"
    private const val CHANNEL_DESC = "Notifiche dell'app"

    private fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = ctx.getSystemService(NotificationManager::class.java)
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply { description = CHANNEL_DESC }
                nm.createNotificationChannel(ch)
            }
        }
    }

    /**
     * Mostra una notifica semplice.
     * @param id se non lo passi usa un id casuale (in base al tempo)
     */
    fun notify(
        ctx: Context,
        title: String,
        body: String,
        id: Int = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
    ) {
        // Permesso runtime da Android 13 (API 33)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return // esci silenziosamente se non concesso
        }

        ensureChannel(ctx)

        val builder = NotificationCompat.Builder(ctx, CHANNEL_ID)
            // Usa un’icona sempre presente per evitare 'ic_notification' non trovato
            // Puoi sostituire con R.mipmap.ic_launcher o con una tua icona in drawable.
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)

        NotificationManagerCompat.from(ctx).notify(id, builder.build())
    }
}
