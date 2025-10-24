package com.example.tieniiltempo.data

import com.google.firebase.Timestamp

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
// data/ChatMessage.kt
data class ChatMessage(
    val id: String = "",
    val chatId: String = "",
    val fromId: String = "",
    val toId: String = "",
    val text: String = "",
    val createdAt: Timestamp? = null   // <--
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
    val points: Int = 0,
    val totalCompleted: Int = 0,
    val level: Int = 1,          // 1..100
    val xpInLevel: Int = 0,      // punti accumulati nel livello corrente
    val totalXp: Int = 0,        // totale storico
    val goldCount: Int = 0,
    val silverCount: Int = 0,
    val bronzeCount: Int = 0,
    val platinumCount: Int = 0,
    val lastCompletedAt: Timestamp? = null,
    val badges: List<String> = emptyList(), // opzionale (nomi/titoli)
    val streakDays: Int = 0,     // lascio compatibilità col tuo codice
    val onTimeCount: Int = 0
)