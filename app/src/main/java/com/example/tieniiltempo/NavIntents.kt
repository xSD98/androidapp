package com.example.tieniiltempo

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import androidx.core.net.toUri

object NavIntents {
    // ponte per navigare ai commenti senza passare il NavController ovunque
    // (AppRoot osserva questo stato e naviga)
    val toComments = mutableStateOf<Pair<String,String>?>(null)
    fun navToComments(activityId: String, subtaskId: String) {
        toComments.value = activityId to subtaskId
    }

    // PendingIntent
    fun pendingIntentToRoute(ctx: Context, route: String): PendingIntent {
        val intent = Intent(
            Intent.ACTION_VIEW,
            "app://tieniiltempo/$route".toUri(),
            ctx,
            MainActivity::class.java
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            ctx, route.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }


}