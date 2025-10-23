package com.example.tieniiltempo.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint

// --- UTENTI ---
data class AppUser(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    // "caregiver" | "user"
    val role: String = "user",
    // se user, contiene l'uid del caregiver che lo gestisce; se caregiver può essere null o proprio uid
    val caregiverId: String = ""
)

// --- ATTIVITÀ ---
data class ActivityTT(
    val id: String = "",
    val userId: String = "",
    val caregiverId: String? = null,
    val title: String = "",
    val description: String = "",
    // "PLANNED" | "RUNNING" | "DONE"
    val status: String = "PLANNED",
    val expectedMinutes: Int = 0,
    val createdAt: Timestamp? = null,
    val startedAt: Timestamp? = null,
    val completedAt: Timestamp? = null,
    val review: ActivityReview? = null,  // valutazione caregiver (opzionale)
)

// dentro una stessa attività, tutte le sotto-attività con lo stesso "stage" sono PARALLELE.
// Si può passare allo stage successivo solo quando tutte quelle di questo stage sono complete.
data class Subtask(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val stage: Int = 1,                // 1, 2, 3... (stesso numero = parallele)
    val expectedMinutes: Int = 5,
    // "NORMAL" | "LOCATION"
    val type: String = "NORMAL",
    val startedAt: Timestamp? = null,
    val completedAt: Timestamp? = null,
    // per tipo LOCATION
    val location: GeoPoint? = null
)

// Commenti di sotto-attività (autore = user o caregiver). Immagine opzionale (Firebase Storage).
data class SubtaskComment(
    val id: String = "",
    val authorId: String = "",
    // "caregiver" | "user"
    val authorRole: String = "user",
    val text: String = "",
    val imageUrl: String? = null,
    val createdAt: Timestamp? = null
)

// Valutazione finale attività (dato dal caregiver)
data class ActivityReview(
    val rating: Int = 0,              // 1..5
    val comment: String = "",
    val caregiverId: String = "",
    val createdAt: Timestamp? = null
)

// Chat & Gamification & Alert (se già usati altrove)
data class ChatMessage(
    val id: String = "",
    val chatId: String = "",
    val fromId: String = "",
    val toId: String = "",
    val text: String = "",
    val createdAt: Timestamp = Timestamp.now()
)

data class Alert(
    val id: String = "",
    val caregiverId: String = "",
    val userId: String = "",
    val activityId: String = "",
    val subtaskId: String = "",
    val minutesExpected: Int = 0,
    val minutesActual: Int = 0,
    val createdAt: Timestamp = Timestamp.now()
)

data class Gamification(
    val userId: String = "",
    val onTimeCount: Int = 0,
    val streakDays: Int = 0,
    val badges: List<String> = emptyList()
)