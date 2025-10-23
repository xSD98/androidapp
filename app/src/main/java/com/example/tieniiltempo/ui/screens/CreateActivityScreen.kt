package com.example.tieniiltempo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.tieniiltempo.data.ActivityTT
import com.example.tieniiltempo.data.Repo
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateActivityScreen(
    userId: String,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { TiTopBar(title = "Nuova attività", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Titolo") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Descrizione") },
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    if (title.isBlank()) {
                        scope.launch { snackbar.showSnackbar("Inserisci un titolo") }
                        return@Button
                    }
                    scope.launch {
                        try {
                            saving = true
                            // Attività base; potrai aggiungere sotto-attività in seguito
                            val act = ActivityTT(
                                id = "",
                                userId = userId,
                                title = title.trim(),
                                description = description.trim(),
                                status = "PLANNED",
                                expectedMinutes = 0
                            )
                            Repo.createActivity(act, subtasks = emptyList())
                            snackbar.showSnackbar("Attività creata")
                            onDone()
                        } catch (e: Exception) {
                            snackbar.showSnackbar(
                                "Errore: ${e.localizedMessage ?: "imprevisto!"}"
                            )
                        } finally {
                            saving = false
                        }
                    }
                },
                enabled = !saving
            ) {
                Text(if (saving) "Salvo..." else "Crea")
            }
        }
    }
}
