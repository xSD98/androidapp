package com.example.tieniiltempo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.tieniiltempo.data.Alert
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(onBack: () -> Unit) {
    var rows by remember { mutableStateOf(listOf<Alert>()) }

    LaunchedEffect(Unit) {
        val db = FirebaseFirestore.getInstance()
        db.collection("alerts")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snap, _ ->
                rows = (snap?.documents ?: emptyList()).mapNotNull { it.toObject(Alert::class.java) }
            }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Alert") }) }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp)) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rows.size) { i ->
                    val a = rows[i]
                    Card {
                        Column(Modifier.padding(12.dp)) {
                            Text("Attività: ${a.activityId}  •  Subtask: ${a.subtaskId}")
                            Text("Previsti: ${a.minutesExpected}  •  Effettivi: ${a.minutesActual}")
                            Text("User: ${a.userId}  •  Caregiver: ${a.caregiverId}")
                            Text(a.createdAt.toDate().toString(), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
