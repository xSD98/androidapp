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
import com.google.firebase.ktx.Firebase
import com.google.firebase.auth.ktx.auth
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()
    val me = Firebase.auth.currentUser

    var acts by remember { mutableStateOf(listOf<ActivityTT>()) }
    var loading by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf<String?>(null) }
    var isCaregiver by remember { mutableStateOf(false) }

    // dialog rating
    var showRate by remember { mutableStateOf(false) }
    var rateForId by remember { mutableStateOf<String?>(null) }
    var rating by remember { mutableFloatStateOf(3f) }
    var rateText by remember { mutableStateOf("") }
    var rateErr by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            try {
                loading = true; err = null
                acts = Repo.userActivities(userId)
                isCaregiver = (Repo.currentUser()?.role ?: "user").equals("caregiver", true)
            } catch (e: Exception) {
                err = e.localizedMessage
            } finally { loading = false }
        }
    }

    LaunchedEffect(userId) { refresh() }

    Scaffold(
        topBar = {
            // tua TiTopBar se l'hai; altrimenti un topbar base:
            CenterAlignedTopAppBar(
                title = { Text("Attività utente") },
                navigationIcon = {},
                actions = {
                    TextButton(onClick = onBack) { Text("Indietro") }
                    TextButton(onClick = openChat) { Text("Chat") }
                    TextButton(onClick = onLogout) { Text("Logout") }
                }
            )
        },
        floatingActionButton = {
            if (isCaregiver) {
                ExtendedFloatingActionButton(onClick = onCreate) { Text("Nuova attività") }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (err != null) Text(err!!, color = MaterialTheme.colorScheme.error)

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
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(a.title, style = MaterialTheme.typography.titleMedium)
                            if (a.description.isNotBlank()) Text(a.description, style = MaterialTheme.typography.bodySmall)
                            Text("Stato: ${a.status} • Previsto: ${a.expectedMinutes} min", style = MaterialTheme.typography.bodySmall)

                            // review (se c'è, la mostro; se manca ed è caregiver + DONE, mostro "Valuta")
                            if (a.review != null) {
                                Text("Voto caregiver: ${a.review.rating}/5", style = MaterialTheme.typography.bodySmall)
                                if (a.review.comment.isNotBlank()) Text("Commento: ${a.review.comment}", style = MaterialTheme.typography.bodySmall)
                            } else if (isCaregiver && a.status == "DONE") {
                                OutlinedButton(onClick = {
                                    rateForId = a.id
                                    rating = 3f
                                    rateText = ""
                                    showRate = true
                                }) { Text("Valuta") }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showRate && rateForId != null) {
        AlertDialog(
            onDismissRequest = { showRate = false },
            title = { Text("Valuta attività") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Voto: ${rating.toInt()} / 5")
                    Slider(value = rating, onValueChange = { rating = it.coerceIn(1f, 5f) }, valueRange = 1f..5f, steps = 3)
                    OutlinedTextField(value = rateText, onValueChange = { rateText = it }, label = { Text("Commento (opzionale)") })
                    if (rateErr != null) Text(rateErr!!, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val caregiverId = me?.uid ?: return@TextButton
                    val id = rateForId!!
                    scope.launch {
                        try {
                            Repo.saveActivityReview(id, rating.toInt(), rateText.trim(), caregiverId)
                            showRate = false
                            refresh()
                        } catch (e: Exception) {
                            rateErr = e.localizedMessage
                        }
                    }
                }) { Text("Salva") }
            },
            dismissButton = {
                TextButton(onClick = { showRate = false }) { Text("Annulla") }
            }
        )
    }
}