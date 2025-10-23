package com.example.tieniiltempo.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.tieniiltempo.data.ActivityTT
import com.example.tieniiltempo.data.Repo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserActivitiesScreen(
    userId: String,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onRun: (String) -> Unit,
    openChat: () -> Unit,
    onLogout: () -> Unit
) {
    var acts by remember { mutableStateOf(emptyList<ActivityTT>()) }
    var loading by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf<String?>(null) }
    var isCaregiver by remember { mutableStateOf(false) }

    LaunchedEffect(userId) {
        try {
            loading = true
            acts = Repo.userActivities(userId)
            isCaregiver = Repo.currentUser()?.role.equals("caregiver", ignoreCase = true)
        } catch (e: Exception) {
            err = e.localizedMessage
        } finally {
            loading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Attività utente") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("←") }
                },
                actions = {
                    TextButton(onClick = openChat) { Text("Chat") }
                    TextButton(onClick = onLogout) { Text("Logout") }
                }
            )
        },
        floatingActionButton = {
            if (isCaregiver) {
                // Niente Extended* (che cambia firma tra versioni).
                FloatingActionButton(onClick = onCreate) {
                    Text("+")
                }
            }
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
            err?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(acts.size) { i ->
                    val a = acts[i]
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRun(a.id) },
                        tonalElevation = 1.dp,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(a.title, style = MaterialTheme.typography.titleMedium)
                            if (a.description.isNotBlank()) {
                                Text(a.description, style = MaterialTheme.typography.bodySmall)
                            }
                            Text(
                                "Stato: ${a.status} • Previsto: ${a.expectedMinutes} min",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}
