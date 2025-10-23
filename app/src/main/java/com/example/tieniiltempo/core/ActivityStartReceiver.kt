// app/src/main/java/com/example/tieniiltempo/core/ActivityStartReceiver.kt
package com.example.tieniiltempo.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Mostra una notifica che invita ad avviare l'attività.
 * (Volendo puoi aprire direttamente l'app/runner tramite PendingIntent.)
 */
class ActivityStartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val actId = intent.getStringExtra(AlarmScheduler.EXTRA_ACTIVITY_ID) ?: return

        Notifier.notify(
            ctx = context,
            title = "È ora di iniziare",
            body = "Tocca per avviare l'attività ($actId)"
        )
    }
}
