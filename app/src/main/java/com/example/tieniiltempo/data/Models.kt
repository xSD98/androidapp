package com.example.tieniiltempo.data

import com.google.firebase.Timestamp

/**
 * Profilo utente.
 * NOTA: 'role' è una String ("caregiver" | "user") per evitare errori di enum in deserializzazione.
 */
data class AppUser(
    val uid: String = "",
    val email: String = "",
    val displayName: String = "",
    val role: String = "user",          // "caregiver" oppure "user"
    val caregiverId: String? = null     // per gli utenti è l'id del loro caregiver
)

/**
 * Attività (macro) assegnata a un utente.
 */
data class ActivityTT(
    val id: String = "",
    val userId: String = "",
    val caregiverId: String = "",
    val title: String = "",
    val description: String = "",
    val status: String = "PLANNED",     // PLANNED | RUNNING | DONE
    val expectedMinutes: Int = 0,       // somma degli expectedMinutes delle sotto-attività
    val createdAt: Timestamp = Timestamp.now(),
    val startedAt: Timestamp? = null,
    val completedAt: Timestamp? = null,
    val startAtMillis: Long? = null     // opzionale: per AlarmManager, avvio programmato
)

/**
 * Sotto-attività. Le sotto-attività con lo stesso 'stage' sono "in parallelo"
 * (ordine libero); si passa allo stage successivo solo quando tutte quelle dello
 * stage corrente sono completate.
 */
data class Subtask(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val stage: Int = 1,                 // 1,2,3...
    val expectedMinutes: Int = 5,
    val type: String = "NORMAL",        // NORMAL | LOCATION | CHOICE ... (se vorrai estendere)
    val startedAt: Timestamp? = null,
    val completedAt: Timestamp? = null
)

/**
 * Messaggio chat (salvato in chats/{chatId}/messages/{id})
 */
data class ChatMessage(
    val id: String = "",
    val chatId: String = "",
    val fromId: String = "",
    val toId: String = "",
    val text: String = "",
    val createdAt: Timestamp = Timestamp.now()
)

/**
 * Alert creato quando una sotto-attività sfora i minuti previsti.
 */
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

/**
 * Gamification basilare per utente: conteggi e badge.
 */
data class Gamification(
    val userId: String = "",
    val onTimeCount: Int = 0,           // quante attività completate "in tempo"
    val streakDays: Int = 0,            // streak incrementale (semplificato)
    val badges: List<String> = emptyList()
)
