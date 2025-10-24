package com.example.tieniiltempo.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tieniiltempo.data.ActivityTT
import com.example.tieniiltempo.data.Repo
import com.example.tieniiltempo.data.Subtask
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.ArrowBack


private data class SubtaskDraft(
    val stage: Int,
    val type: String, // "NORMAL" | "LOCATION" | "CHOICE"
    var title: String = "",
    var description: String = "",
    var expectedMinutes: Int = 5,
    var localImage: Uri? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateActivityScreen(
    userId: String,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    // sottà-attività in bozza (immutabile per evitare warning Compose)
    var drafts by remember { mutableStateOf(listOf<SubtaskDraft>()) }

    // dialog scelta tipo
    var showTypePicker by remember { mutableStateOf(false) }
    var pendingAddChoice by remember { mutableStateOf(false) }

    // image picker (assegno al draft selezionato)
    var editingIndexForImage by remember { mutableStateOf<Int?>(null) }
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        editingIndexForImage?.let { idx ->
            if (uri != null) {
                drafts = drafts.toMutableList().also { list ->
                    list[idx] = list[idx].copy(localImage = uri)
                }
            }
            editingIndexForImage = null
        }
    }

    fun nextStage(): Int {
        val max = drafts.maxOfOrNull { it.stage } ?: 0
        return max + 1
    }

    fun addDraft(type: String) {
        val stage = nextStage()
        if (type == "CHOICE") {
            // due sottà-attività parallele (stesso stage)
            val a = SubtaskDraft(stage = stage, type = "CHOICE", title = "Scelta A")
            val b = SubtaskDraft(stage = stage, type = "CHOICE", title = "Scelta B")
            drafts = drafts + listOf(a, b)
        } else {
            drafts = drafts + SubtaskDraft(stage = stage, type = type, title = "Sotto-attività")
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Nuova attività") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Indietro"
                        )
                    }
                },
                actions = {
                    // pulsante Aggiungi sotto-attività
                    IconButton(onClick = { showTypePicker = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Aggiungi sotto-attività")
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("Titolo attività") }, modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = description, onValueChange = { description = it },
                label = { Text("Descrizione attività") }, modifier = Modifier.fillMaxWidth()
            )

            if (drafts.isEmpty()) {
                Text(
                    "Aggiungi le sotto-attività con il + in alto.\n" +
                            "Per CHOICE verranno create due voci parallele nello stesso stage.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // elenco sotto-attività
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                itemsIndexed(drafts) { idx, d ->
                    Card {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "Stage ${d.stage} • ${when(d.type){
                                        "LOCATION" -> "Posizione"
                                        "CHOICE" -> "Scelta"
                                        else -> "Normale"
                                    }}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                // elimina bozza
                                TextButton(onClick = {
                                    drafts = drafts.toMutableList().also { it.removeAt(idx) }
                                }) { Text("Rimuovi") }
                            }

                            // immagine opzionale (solo caregiver → siamo in schermata caregiver)
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.25f))
                                    .clickable {
                                        editingIndexForImage = idx
                                        imagePicker.launch("image/*")
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                if (d.localImage == null) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Image, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        Text("+ immagine (opzionale)")
                                    }
                                } else {
                                    Text("Immagine selezionata")
                                }
                            }

                            OutlinedTextField(
                                value = d.title,
                                onValueChange = {
                                    drafts = drafts.toMutableList().also { list ->
                                        list[idx] = list[idx].copy(title = it)
                                    }
                                },
                                label = { Text("Nome sotto-attività") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = d.description,
                                onValueChange = {
                                    drafts = drafts.toMutableList().also { list ->
                                        list[idx] = list[idx].copy(description = it)
                                    }
                                },
                                label = { Text("Descrizione") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = d.expectedMinutes.toString(),
                                onValueChange = { v ->
                                    val m = v.filter { it.isDigit() }.ifBlank { "5" }.toInt()
                                    drafts = drafts.toMutableList().also { list ->
                                        list[idx] = list[idx].copy(expectedMinutes = m)
                                    }
                                },
                                label = { Text("Minuti previsti") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }
                    }
                }
            }

            if (err != null) Text(err!!, color = MaterialTheme.colorScheme.error)

            Button(
                enabled = !loading,
                onClick = {
                    if (title.isBlank()) {
                        err = "Inserisci un titolo"
                        return@Button
                    }
                    if (drafts.isEmpty()) {
                        err = "Aggiungi almeno una sotto-attività"
                        return@Button
                    }
                    scope.launch {
                        try {
                            loading = true
                            err = null

                            // crea attività e under-subtasks
                            val subs = drafts.map {
                                Subtask(
                                    id = "",
                                    title = it.title.ifBlank { "Sotto-attività" },
                                    description = it.description,
                                    stage = it.stage,
                                    expectedMinutes = it.expectedMinutes,
                                    type = it.type,
                                    imageUrl = null,
                                    // i campi started/completed restano null
                                )
                            }
                            val act = ActivityTT(
                                id = "",
                                userId = userId,
                                title = title.trim(),
                                description = description.trim(),
                                status = "PLANNED",
                                expectedMinutes = subs.sumOf { it.expectedMinutes }
                            )
                            val actId = Repo.createActivity(act, subs)

                            // upload immagini (se presenti)
                            // devo conoscere gli id reali delle sub salvate: ricarico le sub appena create
                            val savedSubs = Repo.subtasks(actId)
                            drafts.forEachIndexed { i, d ->
                                val local = d.localImage ?: return@forEachIndexed
                                val saved = savedSubs.getOrNull(i) ?: return@forEachIndexed
                                Repo.uploadSubtaskImage(actId, saved.id, local)
                            }

                            onDone()
                        } catch (e: Exception) {
                            err = e.localizedMessage
                        } finally {
                            loading = false
                        }
                    }
                }
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        strokeCap = StrokeCap.Round,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (loading) "Salvataggio..." else "Crea attività")
            }
        }
    }

    // Modale scelta tipo
    if (showTypePicker) {
        AlertDialog(
            onDismissRequest = { showTypePicker = false },
            title = { Text("Tipo sotto-attività") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        addDraft("NORMAL")
                        showTypePicker = false
                    }) { Text("Normale") }
                    Button(onClick = {
                        addDraft("LOCATION")
                        showTypePicker = false
                    }) { Text("Posizione (LOCATION)") }
                    Button(onClick = {
                        addDraft("CHOICE") // crea automaticamente la coppia stesso stage
                        showTypePicker = false
                    }) { Text("Scelta (CHOICE) → crea 2 nello stesso stage") }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTypePicker = false }) {
                    Text("Chiudi")
                }
            }
        )
    }
}