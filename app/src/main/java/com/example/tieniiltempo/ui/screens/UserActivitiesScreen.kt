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

// 👇 import aggiunti
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserActivitiesScreen(
    userId: String,
    onBack: () -> Unit,
    onCreate: () -> Unit,
    onRun: (String) -> Unit,
    openChat: () -> Unit,
    onLogout: () -> Unit,
    // nuovo opzionale per aprire la gamification (es. nav.navigate("gamification/$userId"))
    openGamification: ((String) -> Unit)? = null,
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

    // ✏️ stati per MODIFICA attività
    var showEdit by remember { mutableStateOf(false) }
    var editId by remember { mutableStateOf<String?>(null) }
    var eTitle by remember { mutableStateOf("") }
    var eDesc by remember { mutableStateOf("") }
    var editErr by remember { mutableStateOf<String?>(null) }
    var editLoading by remember { mutableStateOf(false) }

    // 🗑️ stati per ELIMINAZIONE attività
    var confirmDelete by remember { mutableStateOf(false) }
    var deleteId by remember { mutableStateOf<String?>(null) }
    var deleteLoading by remember { mutableStateOf(false) }

    // 📊 STATISTICHE attività (dialog)
    var showStats by remember { mutableStateOf(false) }
    var statsId by remember { mutableStateOf<String?>(null) }
    var statsLoading by remember { mutableStateOf(false) }
    var statsErr by remember { mutableStateOf<String?>(null) }
    var stTotal by remember { mutableStateOf(0) }
    var stCompleted by remember { mutableStateOf(0) }
    var stInProgress by remember { mutableStateOf(0) }
    var stNotStarted by remember { mutableStateOf(0) }
    var stExpectedSum by remember { mutableStateOf(0) }
    var stActualAvgMin by remember { mutableStateOf<Double?>(null) }

    // formatter per l'orario pianificato
    val fmt = remember { SimpleDateFormat("EEE dd MMM, HH:mm", Locale.getDefault()) }

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
            CenterAlignedTopAppBar(
                title = { Text("Attività utente") },
                navigationIcon = {},
                actions = {
                    TextButton(onClick = onBack) { Text("Indietro") }
                    if (openGamification != null) {
                        TextButton(onClick = { openGamification.invoke(userId) }) { Text("Premi") }
                    }
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
        Column(
            Modifier
                .padding(pad)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (err != null) Text(err!!, color = MaterialTheme.colorScheme.error)

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(acts.size) { i ->
                    val a = acts[i]

                    // 🔎 carico (se c'è) l'orario pianificato SOLO per questa card
                    var plannedText by remember(a.id) { mutableStateOf<String?>(null) }
                    LaunchedEffect(a.id) {
                        try {
                            val doc = FirebaseFirestore.getInstance()
                                .collection("activities")
                                .document(a.id)
                                .get()
                                .await()
                            val ms: Long? =
                                doc.getTimestamp("scheduledAt")?.toDate()?.time
                                    ?: doc.getLong("scheduledAtMs")
                            plannedText = ms?.let { fmt.format(Date(it)) }
                        } catch (_: Exception) {
                            plannedText = null
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRun(a.id) },
                        tonalElevation = 1.dp,
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(a.title, style = MaterialTheme.typography.titleMedium)
                            if (a.description.isNotBlank())
                                Text(a.description, style = MaterialTheme.typography.bodySmall)

                            val statusLine = buildString {
                                append("Stato: ${a.status} • Previsto: ${a.expectedMinutes} min")
                                if (a.status.equals("PLANNED", true) && plannedText != null) {
                                    append(" • Pianificata: "); append(plannedText)
                                }
                            }
                            Text(statusLine, style = MaterialTheme.typography.bodySmall)

                            // review (se presente), altrimenti bottone "Valuta" per caregiver su DONE
                            if (a.review != null) {
                                Text("Voto caregiver: ${a.review.rating}/5", style = MaterialTheme.typography.bodySmall)
                                if (a.review.comment.isNotBlank())
                                    Text("Commento: ${a.review.comment}", style = MaterialTheme.typography.bodySmall)
                            } else if (isCaregiver && a.status == "DONE") {
                                OutlinedButton(onClick = {
                                    rateForId = a.id
                                    rating = 3f
                                    rateText = ""
                                    showRate = true
                                }) { Text("Valuta") }
                            }

                            // 👇 PULSANTI extra SOLO caregiver: Modifica / Elimina / Statistiche
                            if (isCaregiver) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = {
                                        editErr = null
                                        editId = a.id
                                        eTitle = a.title
                                        eDesc = a.description
                                        showEdit = true
                                    }) { Text("Modifica") }

                                    TextButton(onClick = {
                                        deleteId = a.id
                                        confirmDelete = true
                                    }) { Text("Elimina", color = MaterialTheme.colorScheme.error) }
                                }
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
                    Slider(
                        value = rating,
                        onValueChange = { rating = it.coerceIn(1f, 5f) },
                        valueRange = 1f..5f,
                        steps = 3
                    )
                    OutlinedTextField(
                        value = rateText,
                        onValueChange = { rateText = it },
                        label = { Text("Commento (opzionale)") }
                    )
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

    // ✏️ Dialog MODIFICA attività (titolo/descrizione)
    if (showEdit && editId != null) {
        AlertDialog(
            onDismissRequest = { if (!editLoading) { showEdit = false; editId = null } },
            title = { Text("Modifica attività") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = eTitle, onValueChange = { eTitle = it },
                        label = { Text("Titolo") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = eDesc, onValueChange = { eDesc = it },
                        label = { Text("Descrizione") }, modifier = Modifier.fillMaxWidth()
                    )
                    if (editErr != null) Text(editErr!!, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (eTitle.isBlank()) { editErr = "Inserisci un titolo"; return@TextButton }
                        scope.launch {
                            try {
                                editLoading = true
                                editErr = null
                                FirebaseFirestore.getInstance()
                                    .collection("activities")
                                    .document(editId!!)
                                    .update(mapOf(
                                        "title" to eTitle.trim(),
                                        "description" to eDesc.trim()
                                    ))
                                    .await()
                                showEdit = false
                                editId = null
                                refresh()
                            } catch (e: Exception) {
                                editErr = e.localizedMessage
                            } finally {
                                editLoading = false
                            }
                        }
                    },
                    enabled = !editLoading
                ) { Text(if (editLoading) "Salvo..." else "Salva") }
            },
            dismissButton = {
                TextButton(
                    onClick = { if (!editLoading) { showEdit = false; editId = null } },
                    enabled = !editLoading
                ) { Text("Annulla") }
            }
        )
    }

    // 🗑️ Dialog ELIMINA attività (+ sotto-attività)
    if (confirmDelete && deleteId != null) {
        AlertDialog(
            onDismissRequest = { if (!deleteLoading) { confirmDelete = false; deleteId = null } },
            title = { Text("Elimina attività") },
            text = { Text("Eliminare l’attività rimuoverà anche tutte le sotto-attività. Procedere?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            try {
                                deleteLoading = true
                                val db = FirebaseFirestore.getInstance()
                                // elimina tutte le subtasks prima
                                val subSnap = db.collection("activities")
                                    .document(deleteId!!)
                                    .collection("subtasks")
                                    .get().await()
                                val batch = db.batch()
                                subSnap.documents.forEach { d -> batch.delete(d.reference) }
                                batch.commit().await()
                                // poi elimina l'attività
                                db.collection("activities").document(deleteId!!).delete().await()
                                confirmDelete = false
                                deleteId = null
                                refresh()
                            } catch (e: Exception) {
                                rateErr = e.localizedMessage
                            } finally {
                                deleteLoading = false
                            }
                        }
                    },
                    enabled = !deleteLoading
                ) { Text(if (deleteLoading) "Elimino..." else "Elimina") }
            },
            dismissButton = {
                TextButton(
                    onClick = { if (!deleteLoading) { confirmDelete = false; deleteId = null } },
                    enabled = !deleteLoading
                ) { Text("Annulla") }
            }
        )
    }
}