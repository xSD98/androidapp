package com.example.tieniiltempo.data

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint

/**
 * Sotto-attività di un'Activity.
 * - type: NORMAL | LOCATION | CHOICE
 * - imageUrl: immagine opzionale della sotto-attività (non i commenti)
 */
data class Subtask(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val stage: Int = 1,
    val expectedMinutes: Int = 5,
    val type: String = "NORMAL", // NORMAL | LOCATION | CHOICE
    val imageUrl: String? = null,
    val startedAt: Timestamp? = null,
    val completedAt: Timestamp? = null,
    val location: GeoPoint? = null,
    val medal: String? = null         // "GOLD" | "SILVER" | "BRONZE" | null
)