package com.example.tieniiltempo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tieniiltempo.data.Gamification
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import kotlin.math.max
import java.text.DecimalFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamificationScreen(
    userId: String,
    onBack: () -> Unit
) {
    // ---- Gamification (come prima) ----
    var g by remember { mutableStateOf(Gamification(userId = userId)) }
    LaunchedEffect(userId) {
        FirebaseFirestore.getInstance()
            .collection("gamification").document(userId)
            .addSnapshotListener { snap, _ ->
                val data = snap?.toObject(Gamification::class.java)
                if (data != null) g = data
            }
    }
    val costToNext = pointsForNextLevel(g.level)
    val progress   = (g.xpInLevel.toFloat() / costToNext).coerceIn(0f, 1f)

    // ---- STATISTICHE UTENTE (nuove) ----
    var stats by remember { mutableStateOf(UserStats()) }
    var statsLoading by remember { mutableStateOf(true) }
    var statsErr by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(userId) {
        statsLoading = true
        statsErr = null
        try {
            stats = fetchUserStats(userId)
        } catch (e: Exception) {
            statsErr = e.localizedMessage
        } finally {
            statsLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Premi & Progressi") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Indietro") }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---- Livello + XP ----
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Livello ${g.level}", style = MaterialTheme.typography.titleLarge)
                    Text("XP totale: ${g.totalXp}")
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text("${g.xpInLevel}/$costToNext verso il livello ${g.level + 1}",
                        style = MaterialTheme.typography.bodySmall)
                }
            }

            // ---- Medaglie ----
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Medaglie conquistate", style = MaterialTheme.typography.titleMedium)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatRow("🏆 Oro", g.goldCount)
                        StatRow("🥈 Argento", g.silverCount)
                        StatRow("🥉 Bronzo", g.bronzeCount)
                        StatRow("💎 Platino", g.platinumCount)
                    }
                }
            }

            // ---- Statistiche utente integrate qui ----
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Statistiche utente", style = MaterialTheme.typography.titleMedium)

                    if (statsLoading) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    } else if (statsErr != null) {
                        Text(statsErr!!, color = MaterialTheme.colorScheme.error)
                    } else {
                        // Attività
                        Text("Attività", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatChip("Totali", stats.totalActivities)
                            StatChip("Concluse", stats.doneActivities)
                            StatChip("In corso", stats.runningActivities)
                            StatChip("Pianificate", stats.plannedActivities)
                        }

                        Spacer(Modifier.height(12.dp))

                        // Sotto-attività
                        Text("Sotto-attività", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            StatChip("Totali", stats.totalSubtasks)
                            StatChip("Completate", stats.completedSubtasks)
                            StatChipText(
                                label = "Tempo medio",
                                value = if (stats.avgSubtaskMinutes > 0) "${formatOneDecimal(stats.avgSubtaskMinutes)} min" else "—"
                            )
                        }
                    }
                }
            }
        }
    }
}

/** costo per salire da L a L+1 (100 * livello) */
private fun pointsForNextLevel(level: Int): Int = 100 * max(1, level)

/* ================== STATISTICHE ================== */

private data class UserStats(
    val totalActivities: Int = 0,
    val doneActivities: Int = 0,
    val runningActivities: Int = 0,
    val plannedActivities: Int = 0,
    val totalSubtasks: Int = 0,
    val completedSubtasks: Int = 0,
    val avgSubtaskMinutes: Double = 0.0
)

/**
 * Legge tutte le attività dell’utente e aggrega le relative sotto-attività.
 * Richiede la seguente struttura:
 *  - /activities (field userId, status in {PLANNED,RUNNING,DONE})
 *  - /activities/{id}/subtasks (field completedAt, actualMinutes opzionale)
 */
private suspend fun fetchUserStats(userId: String): UserStats {
    val db = FirebaseFirestore.getInstance()

    val actsSnap = db.collection("activities")
        .whereEqualTo("userId", userId)
        .get().await()

    var done = 0
    var running = 0
    var planned = 0

    var totalSub = 0
    var completedSub = 0
    var sumActual = 0.0
    var countActual = 0

    for (doc in actsSnap.documents) {
        when ((doc.getString("status") ?: "").uppercase()) {
            "DONE" -> done++
            "RUNNING" -> running++
            "PLANNED" -> planned++
        }

        // Subtasks della singola attività
        val subsSnap = doc.reference.collection("subtasks").get().await()
        totalSub += subsSnap.size()
        for (sd in subsSnap.documents) {
            if (sd.getTimestamp("completedAt") != null) completedSub++
            // Proviamo a leggere actualMinutes come Long/Double (fallback se il campo non esiste)
            val actualMin = sd.getLong("actualMinutes")?.toDouble()
                ?: sd.getDouble("actualMinutes")
            if (actualMin != null && actualMin > 0.0) {
                sumActual += actualMin
                countActual++
            }
        }
    }

    val avg = if (countActual > 0) sumActual / countActual else 0.0
    return UserStats(
        totalActivities = actsSnap.size(),
        doneActivities = done,
        runningActivities = running,
        plannedActivities = planned,
        totalSubtasks = totalSub,
        completedSubtasks = completedSub,
        avgSubtaskMinutes = avg
    )
}

/* ================== UI helpers ================== */

@Composable
private fun StatRow(label: String, value: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text("×$value")
    }
}

@Composable
private fun StatChip(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun StatChipText(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

private fun formatOneDecimal(v: Double): String = DecimalFormat("#0.0").format(v)