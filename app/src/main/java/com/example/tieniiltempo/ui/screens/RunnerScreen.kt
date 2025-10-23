package com.example.tieniiltempo.ui.screens

// CORRETTI per il campo di testo

// Icone
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.tieniiltempo.data.ActivityTT
import com.example.tieniiltempo.data.Repo
import com.example.tieniiltempo.data.Subtask
import kotlinx.coroutines.launch
import kotlin.math.max
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunnerScreen(
    activityId: String,
    onFinished: () -> Unit,
    onBack: () -> Unit
) {
    var act by remember { mutableStateOf<ActivityTT?>(null) }
    var subs by remember { mutableStateOf(listOf<Subtask>()) }
    var loading by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf<String?>(null) }
    var isCaregiver by remember { mutableStateOf(false) }

    // stato dialog "nuova sotto-attività"
    var showAdd by remember { mutableStateOf(false) }
    var stTitle by remember { mutableStateOf("") }
    var stDesc by remember { mutableStateOf("") }
    var stStageText by remember { mutableStateOf("1") }
    var stMinutesText by remember { mutableStateOf("5") }
    var stType by remember { mutableStateOf("NORMAL") }
    var addErr by remember { mutableStateOf<String?>(null) }
    var addLoading by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    fun refresh() = scope.launch {
        try {
            loading = true
            err = null
            act = Repo.activity(activityId)
            subs = Repo.subtasks(activityId)
            isCaregiver = Repo.currentUser()?.role.equals("caregiver", ignoreCase = true)
        } catch (e: Exception) {
            err = e.localizedMessage
        } finally { loading = false }
    }

    LaunchedEffect(activityId) { refresh() }

    Scaffold(
        topBar = {
            // Se usi un tuo TiTopBar, puoi sostituire questo TopAppBar
            TopAppBar(
                title = { Text(act?.title ?: "Attività") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                }
            )
        },
        floatingActionButton = {
            if (isCaregiver) {
                ExtendedFloatingActionButton(
                    onClick = {
                        addErr = null
                        stTitle = ""
                        stDesc = ""
                        stStageText = "1"
                        stMinutesText = "5"
                        stType = "NORMAL"
                        showAdd = true
                    },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Sotto-attività") }
                )
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

            Text(act?.description.orEmpty(), style = MaterialTheme.typography.bodyMedium)

            // elenco sotto-attività
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(subs.size) { i ->
                    val st = subs[i]
                    Card {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${st.title} • Stage ${st.stage}")
                            if (st.description.isNotBlank())
                                Text(st.description, style = MaterialTheme.typography.bodySmall)

                            val startedAt = st.startedAt?.toDate()?.time
                            val completedAt = st.completedAt?.toDate()?.time
                            val now = System.currentTimeMillis()
                            val actualMin = ((completedAt ?: now) - (startedAt ?: now)) / 60000.0
                            Text(
                                "Previsti: ${st.expectedMinutes} min • Attuali: ${max(0.0, actualMin).toInt()} min",
                                style = MaterialTheme.typography.bodySmall
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val canStart = st.startedAt == null &&
                                        subs.filter { it.stage == st.stage }
                                            .all { it.startedAt == null || it.id == st.id }
                                val canComplete = st.startedAt != null && st.completedAt == null

                                Button(
                                    onClick = {
                                        if (canStart) {
                                            scope.launch {
                                                Repo.markSubtaskStarted(activityId, st.id)
                                                refresh()
                                            }
                                        }
                                    },
                                    enabled = canStart
                                ) { Text("Avvia") }

                                Button(
                                    onClick = {
                                        if (canComplete) {
                                            scope.launch {
                                                val actual = (((System.currentTimeMillis()) - (st.startedAt!!.toDate().time)) / 60000.0).toInt()
                                                val me = Repo.currentUser()!!
                                                Repo.markSubtaskCompleted(
                                                    activityId = activityId,
                                                    subtaskId = st.id,
                                                    expectedMin = st.expectedMinutes,
                                                    actualMin = actual,
                                                    caregiverId = me.caregiverId?.ifBlank { me.uid } ?: me.uid,
                                                    userId = me.uid
                                                )
                                                refresh()
                                                // se tutte complete → chiudi schermata
                                                val allDone = subs.all { it.completedAt != null }
                                                if (allDone) onFinished()
                                            }
                                        }
                                    },
                                    enabled = canComplete
                                ) { Text("Completa") }

                                // Commenti: porta alla screen commenti (se l'hai già agganciata)
                                TextButton(onClick = {
                                    // Se hai NavIntents/nav dedicata
                                    // NavIntents.navToComments(activityId, st.id)
                                }) { Text("Commenti") }
                            }
                        }
                    }
                }
            }
        }
    }

    // Dialog: nuova sotto-attività (solo caregiver)
    if (showAdd) {
        AlertDialog(
            onDismissRequest = { if (!addLoading) showAdd = false },
            title = { Text("Nuova sotto-attività") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = stTitle,
                        onValueChange = { stTitle = it },
                        label = { Text("Titolo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = stDesc,
                        onValueChange = { stDesc = it },
                        label = { Text("Descrizione") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = stStageText,
                            onValueChange = { stStageText = it.filter { c -> c.isDigit() }.ifBlank { "1" } },
                            label = { Text("Stage") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = stMinutesText,
                            onValueChange = { stMinutesText = it.filter { c -> c.isDigit() }.ifBlank { "1" } },
                            label = { Text("Minuti previsti") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    // Se vuoi, puoi fare un dropdown; per semplicità testo libero con 3 valori consigliati
                    OutlinedTextField(
                        value = stType,
                        onValueChange = { stType = it.uppercase() },
                        label = { Text("Tipo (NORMAL / LOCATION / CHOICE)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (addErr != null) {
                        Text(addErr!!, color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val titleOk = stTitle.isNotBlank()
                        val stage = stStageText.toIntOrNull() ?: 1
                        val mins = stMinutesText.toIntOrNull() ?: 1
                        if (!titleOk) {
                            addErr = "Inserisci il titolo"
                            return@TextButton
                        }
                        scope.launch {
                            try {
                                addLoading = true
                                addErr = null
                                val sub = Subtask(
                                    title = stTitle.trim(),
                                    description = stDesc.trim(),
                                    stage = stage,
                                    expectedMinutes = mins,
                                    type = stType.ifBlank { "NORMAL" }.uppercase()
                                )
                                Repo.addSubtask(activityId, sub)
                                showAdd = false
                                refresh()
                            } catch (e: Exception) {
                                addErr = e.localizedMessage
                            } finally {
                                addLoading = false
                            }
                        }
                    },
                    enabled = !addLoading
                ) { Text(if (addLoading) "Salvo..." else "Salva") }
            },
            dismissButton = {
                TextButton(
                    onClick = { if (!addLoading) showAdd = false },
                    enabled = !addLoading
                ) { Text("Annulla") }
            }
        )
    }
}