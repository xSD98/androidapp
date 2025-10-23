package com.example.tieniiltempo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.tieniiltempo.data.Gamification
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamificationScreen(userId: String, onBack: () -> Unit) {
    var g by remember { mutableStateOf(Gamification(userId)) }

    LaunchedEffect(userId) {
        val ref = FirebaseFirestore.getInstance().collection("gamification").document(userId)
        ref.addSnapshotListener { snap, _ ->
            g = snap?.toObject(Gamification::class.java) ?: Gamification(userId)
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Premi & Streak") }) }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card { Column(Modifier.padding(16.dp)) {
                Text("Streak giorni", style = MaterialTheme.typography.titleMedium)
                Text("${g.streakDays}", style = MaterialTheme.typography.displaySmall)
            } }
            Card { Column(Modifier.padding(16.dp)) {
                Text("Completate in orario", style = MaterialTheme.typography.titleMedium)
                Text("${g.onTimeCount}", style = MaterialTheme.typography.headlineMedium)
            } }
            Text("Badge", style = MaterialTheme.typography.titleMedium)
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(g.badges.size) { i ->
                    AssistChip(onClick = {}, label = { Text(g.badges[i]) })
                }
            }
        }
    }
}
