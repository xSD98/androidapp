package com.example.tieniiltempo.core

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings

object AlarmScheduler {
    private const val ACTION_START_ACTIVITY = "com.example.tieniiltempo.ACTION_START_ACTIVITY"
    const val EXTRA_ACTIVITY_ID = "activityId"

    /**
     * true = allarme EXACT impostato
     * false = fallback impostato (inexact) perché non consentito, o non concesso.
     */
    fun scheduleActivityStart(
        context: Context,
        activityId: String,
        triggerAtMillis: Long
    ): Boolean {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = buildPendingIntent(context, activityId)

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+ richiede che l’app sia autorizzata a usare gli exact alarms
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
                    true
                } else {
                    // Non autorizzato: fai fallback ad un allarme non “exact”
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
                    false
                }
            } else {
                // Pre-S: nessun gate speciale
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
                true
            }
        } catch (se: SecurityException) {
            // Alcuni dispositivi lanciano comunque SecurityException: fallback
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            false
        }
    }

    fun cancelActivityStart(context: Context, activityId: String) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(buildPendingIntent(context, activityId))
    }

    /**
     * Apre le impostazioni per concedere l’autorizzazione agli exact alarms (Android 12+).
     * Chiama questa se `scheduleActivityStart` ti ha restituito false e vuoi guidare l’utente.
     */
    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val i = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                // alcuni OEM richiedono il package esplicito
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(i)
        }
    }

    private fun buildPendingIntent(context: Context, activityId: String): PendingIntent {
        val intent = Intent(context, ActivityStartReceiver::class.java).apply {
            action = ACTION_START_ACTIVITY
            putExtra(EXTRA_ACTIVITY_ID, activityId)
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        return PendingIntent.getBroadcast(
            context,
            activityId.hashCode(), // id stabile per poter cancellare
            intent,
            flags
        )
    }
}
