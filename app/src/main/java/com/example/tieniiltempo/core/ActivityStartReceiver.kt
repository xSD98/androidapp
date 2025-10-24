package com.example.tieniiltempo.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.tieniiltempo.NavIntents
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ActivityStartReceiver : BroadcastReceiver() {

    override fun onReceive(ctx: Context, intent: Intent) {
        val actId = intent.getStringExtra("activityId") ?: return
        val title = intent.getStringExtra("title") ?: "Attività"

        // Estende la vita del receiver finché non terminiamo il lavoro async
        val pr = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = FirebaseFirestore.getInstance()
                val actRef = db.collection("activities").document(actId)
                val subsRef = actRef.collection("subtasks")

                // Prendi la prima sotto-attività non avviata, ordinata per stage
                val subsSnap = subsRef.orderBy("stage").get().awaitOrNull()
                val firstToStart = subsSnap?.documents?.firstOrNull { d ->
                    d.getTimestamp("startedAt") == null && d.getTimestamp("completedAt") == null
                }

                // Aggiorno in un'unica batch: attività → RUNNING + startedAt, e la prima sub → startedAt
                db.runBatch { b ->
                    b.update(actRef, mapOf(
                        "status" to "RUNNING",
                        "startedAt" to FieldValue.serverTimestamp()
                    ))
                    firstToStart?.let { doc ->
                        b.update(doc.reference, "startedAt", FieldValue.serverTimestamp())
                    }
                }.awaitOrNull()

                // Notifica locale che apre il runner
                val channelId = "activity_start"
                NotifierEnsure.ensureChannel(
                    ctx,
                    id = channelId,
                    name = "Promemoria attività",
                    desc = "Notifiche di avvio attività"
                )

                val pi = NavIntents.pendingIntentToRoute(ctx, "runner/$actId")

                val n = NotificationCompat.Builder(ctx, channelId)
                    .setSmallIcon(android.R.drawable.ic_dialog_email)
                    .setContentTitle("È ora di iniziare")
                    .setContentText(title)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(title))
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .build()

                try {
                    val id = (System.currentTimeMillis() % Int.MAX_VALUE).toInt()
                    NotificationManagerCompat.from(ctx).notify(id, n)
                } catch (_: SecurityException) {
                    // permesso notifiche negato → ignora
                }
            } finally {
                pr.finish()
            }
        }
    }
}

/** Helper per creare canali al volo. */
object NotifierEnsure {
    fun ensureChannel(ctx: Context, id: String, name: String, desc: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (nm.getNotificationChannel(id) == null) {
            val ch = android.app.NotificationChannel(
                id, name, android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = desc
                enableVibration(true)
            }
            nm.createNotificationChannel(ch)
        }
    }
}

/* --- piccole extension per usare await() in modo sicuro senza far esplodere il receiver --- */
private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitOrNull(): T? =
    try { await() } catch (_: Exception) { null }