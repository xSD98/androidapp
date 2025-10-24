package com.example.tieniiltempo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.tieniiltempo.data.Gamification
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamificationScreen(
    userId: String,
    onBack: () -> Unit
) {
    var g by remember { mutableStateOf(Gamification(userId = userId)) }

    // live update Firestore
    LaunchedEffect(userId) {
        FirebaseFirestore.getInstance().collection("gamification").document(userId)
            .addSnapshotListener { snap, _ ->
                val data = snap?.toObject(Gamification::class.java)
                if (data != null) g = data
            }
    }

    // progress verso il prossimo livello (costo = 100 * livello)
    val costToNext = pointsForNextLevel(g.level)
    val progress   = (g.xpInLevel.toFloat() / costToNext).coerceIn(0f, 1f)

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
            // Livello + XP
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Livello ${g.level}", style = MaterialTheme.typography.titleLarge)
                    Text("XP totale: ${g.totalXp}")
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("${g.xpInLevel}/$costToNext verso il livello ${g.level + 1}",
                        style = MaterialTheme.typography.bodySmall)
                }
            }

            // Medaglie con contatori
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Medaglie conquistate", style = MaterialTheme.typography.titleMedium)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("🏆 Oro")
                            Text("×${g.goldCount}")
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("🥈 Argento")
                            Text("×${g.silverCount}")
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("🥉 Bronzo")
                            Text("×${g.bronzeCount}")
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("💎 Platino")
                            Text("×${g.platinumCount}")
                        }
                    }
                }
            }
        }
    }
}

/** costo per salire da L a L+1 (100 * livello) */
private fun pointsForNextLevel(level: Int): Int = 100 * max(1, level)