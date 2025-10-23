package com.example.tieniiltempo

import androidx.compose.runtime.mutableStateOf

object NavIntents {
    // semplice ponte per navegare alla screen commenti senza passare il NavController ovunque
    // (AppRoot osserva questo stato e navega)
    val toComments = mutableStateOf<Pair<String,String>?>(null) // (activityId, subtaskId)
    fun navToComments(activityId: String, subtaskId: String) {
        toComments.value = activityId to subtaskId
    }
}