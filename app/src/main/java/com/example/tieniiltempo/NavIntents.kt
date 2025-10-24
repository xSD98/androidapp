package com.example.tieniiltempo

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import androidx.core.net.toUri

object NavIntents {
    // ponte per navigare ai commenti senza passare il NavController ovunque
    // (AppRoot osserva questo stato e naviga)
    val toComments = mutableStateOf<Pair<String, String>?>(null) // (activityId, subtaskId)

    fun navToComments(activityId: String, subtaskId: String) {
        toComments.value = activityId to subtaskId
    }

    fun pendingIntentToChat(ctx: Context, withId: String?): PendingIntent {
        // Avvia MainActivity con un deep link: app://tieniiltempo/chat/{withId} oppure /chatList
        val route = if (!withId.isNullOrBlank()) "chat/$withId" else "chatList"
        val intent = Intent(
            Intent.ACTION_VIEW,
            "app://tieniiltempo/$route".toUri(),
            ctx,
            MainActivity::class.java
        ).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            // opzionale: extra di fallback se non usi deep links
            putExtra("nav_route", route)
        }

        return PendingIntent.getActivity(
            ctx,
            route.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}