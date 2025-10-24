package com.example.tieniiltempo.core

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.tieniiltempo.MainActivity
import com.example.tieniiltempo.R
import com.example.tieniiltempo.data.Repo
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class MessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        // salva il token su Firestore per l’utente loggato
        GlobalScope.launch {
            try { Repo.saveFcmTokenForCurrentUser(token) } catch (_: Exception) {}
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val myUid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid

        // la function invia anche fromId e toId
        val fromId = message.data["fromId"]
        val toId   = message.data["toId"]

        // se per qualsiasi motivo arriva una notifica del MIO messaggio → ignora
        if (fromId != null && myUid != null && fromId == myUid) return

        val title = message.data["title"] ?: "Nuovo messaggio"
        val body  = message.data["body"]  ?: "Hai ricevuto un messaggio"
        val withId = message.data["withId"] // mittente, per aprire la chat

        showNotification(this, title, body, withId)
    }

    private fun showNotification(ctx: Context, title: String, body: String, withId: String?) {
        // Android 13+: verifica permesso
        if (Build.VERSION.SDK_INT >= 33) {
            val granted = ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }

        val channelId = "chat_messages"
        val nm = NotificationManagerCompat.from(ctx)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                channelId, "Chat",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifiche chat"
                enableLights(true); lightColor = Color.CYAN
                enableVibration(true)
            }
            nm.createNotificationChannel(ch)
        }

        // Intent per aprire l’app (e facoltativamente una chat specifica)
        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (!withId.isNullOrBlank()) putExtra("openChatWithId", withId)
        }
        val pi = PendingIntent.getActivity(
            ctx,
            /*requestCode*/ (System.currentTimeMillis() % Int.MAX_VALUE).toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
        )

        val notification = NotificationCompat.Builder(ctx, channelId)
            .setSmallIcon(R.mipmap.ic_launcher) // metti una small icon bianca tua quando puoi
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        nm.notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notification)
    }
}