package com.example.tieniiltempo.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.tieniiltempo.data.ActivityTT
import com.example.tieniiltempo.data.Repo
import com.example.tieniiltempo.data.Subtask
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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
    val ctx = LocalContext.current

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    var drafts by remember { mutableStateOf(listOf<SubtaskDraft>()) }
    var showTypePicker by remember { mutableStateOf(false) }

    // orario pianificato (null = nessuna pianificazione)
    var scheduledAtMillis by remember { mutableStateOf<Long?>(null) }
    val scheduleFmt = remember { SimpleDateFormat("EEE dd MMM, HH:mm", Locale.getDefault()) }


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

    fun nextStage(): Int = (drafts.maxOfOrNull { it.stage } ?: 0) + 1

    fun addDraft(type: String) {
        val stage = nextStage()
        if (type == "CHOICE") {
            val a = SubtaskDraft(stage = stage, type = "CHOICE", title = "Scelta A")
            val b = SubtaskDraft(stage = stage, type = "CHOICE", title = "Scelta B")
            drafts = drafts + listOf(a, b)
        } else {
            drafts = drafts + SubtaskDraft(stage = stage, type = type, title = "Sotto-attività")
        }
    }

    // prim sotto-attività automatica se la lista è vuota
    LaunchedEffect(Unit) {
        if (drafts.isEmpty()) {
            drafts = listOf(SubtaskDraft(stage = 1, type = "NORMAL", title = "Sotto-attività 1"))
        }
    }

    // ——— Picker data/ora (Android standard) ———
    fun openDateTimePicker(onPicked: (Long?) -> Unit) {
        val cal = Calendar.getInstance()
        val now = cal.timeInMillis
        val dp = DatePickerDialog(
            ctx,
            { _, y, m, d ->
                cal.set(Calendar.YEAR, y)
                cal.set(Calendar.MONTH, m)
                cal.set(Calendar.DAY_OF_MONTH, d)
                val tp = TimePickerDialog(
                    ctx,
                    { _, hh, mm ->
                        cal.set(Calendar.HOUR_OF_DAY, hh)
                        cal.set(Calendar.MINUTE, mm)
                        cal.set(Calendar.SECOND, 0)
                        cal.set(Calendar.MILLISECOND, 0)
                        val picked = cal.timeInMillis
                        // evita orari nel passato
                        onPicked(if (picked < now) null else picked)
                    },
                    cal.get(Calendar.HOUR_OF_DAY),
                    cal.get(Calendar.MINUTE),
                    true
                )
                tp.show()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        )
        dp.show()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Nuova attività") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    IconButton(onClick = { showTypePicker = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Aggiungi sotto-attività")
                    }
                }
            )
        },
        // Bottone fisso in basso + pianificazione
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp)
                        .navigationBarsPadding(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (err != null) {
                        Text(
                            err!!,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            "Sotto-attività: ${drafts.size}" +
                                    (scheduledAtMillis?.let { " • Pianificata: ${scheduleFmt.format(it)}" } ?: ""),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Pulsante Pianifica/Modifica
                    OutlinedButton(
                        onClick = {
                            openDateTimePicker { picked ->
                                scheduledAtMillis = picked
                                err = if (picked == null) {
                                    "Orario non valido (nel passato)"
                                } else {
                                    null
                                }
                            }
                        },
                        enabled = !loading
                    ) {
                        Icon(Icons.Filled.Schedule, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (scheduledAtMillis == null) "Pianifica" else "Modifica")
                    }

                    // Cancella pianificazione (se presente)
                    if (scheduledAtMillis != null) {
                        TextButton(
                            onClick = { scheduledAtMillis = null },
                            enabled = !loading
                        ) { Text("Rimuovi") }
                    }

                    // Salva
                    Button(
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

                                    val subs = drafts.map {
                                        Subtask(
                                            id = "",
                                            title = it.title.ifBlank { "Sotto-attività" },
                                            description = it.description,
                                            stage = it.stage,
                                            expectedMinutes = it.expectedMinutes.coerceAtLeast(1),
                                            type = it.type,
                                            imageUrl = null
                                        )
                                    }
                                    val act = ActivityTT(
                                        id = "",
                                        userId = userId,
                                        title = title.trim(),
                                        description = description.trim(),
                                        status = "PLANNED",
                                        expectedMinutes = subs.sumOf { s -> s.expectedMinutes }
                                    )
                                    val actId = Repo.createActivity(act, subs)

                                    // Pianifica avvio della PRIMA sotto-attività (se orario scelto)
                                    // Salva l’orario pianificato dentro al documento dell’attività
                                    scheduledAtMillis?.let { whenMs ->
                                        val db = FirebaseFirestore.getInstance()
                                        db.collection("activities")
                                            .document(actId)
                                            .set(
                                                mapOf(
                                                    "scheduledAtMs" to whenMs,
                                                    "scheduledAt" to Timestamp(Date(whenMs))
                                                ),
                                                SetOptions.merge()
                                            )
                                    }

                                    // upload immagini (se presenti)
                                    val savedSubs = Repo.subtasks(actId)
                                    drafts.forEachIndexed { i, d ->
                                        d.localImage?.let { uri ->
                                            savedSubs.getOrNull(i)?.let { saved ->
                                                Repo.uploadSubtaskImage(actId, saved.id, uri)
                                            }
                                        }
                                    }

                                    onDone()
                                } catch (e: Exception) {
                                    err = e.localizedMessage
                                } finally {
                                    loading = false
                                }
                            }
                        },
                        enabled = !loading
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                strokeCap = StrokeCap.Round,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Salvataggio...")
                        } else {
                            Text("Crea attività")
                        }
                    }
                }
            }
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // campi testata sopra la lista
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("Titolo attività") }, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = description, onValueChange = { description = it },
                label = { Text("Descrizione attività") }, modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))

            // lista che occupa tutto lo spazio rimanente
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 12.dp)
            ) {
                itemsIndexed(drafts) { idx, d ->
                    val optionLabel = remember(d.stage, d.type, idx, drafts) {
                        if (d.type != "CHOICE") null
                        else {
                            val siblings = drafts.withIndex()
                                .filter { it.value.type == "CHOICE" && it.value.stage == d.stage }
                                .map { it.index }
                            if (siblings.size == 2) {
                                if (siblings.first() == idx) "Opzione A" else "Opzione B"
                            } else null
                        }
                    }

                    Card {
                        Column(
                            Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    buildString {
                                        append("Stage ${d.stage} • ")
                                        append(
                                            when (d.type) {
                                                "LOCATION" -> "Posizione"
                                                "CHOICE" -> "Scelta" + (optionLabel?.let { " ($it)" } ?: "")
                                                else -> "Normale"
                                            }
                                        )
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                TextButton(onClick = {
                                    drafts = drafts.toMutableList().also { it.removeAt(idx) }
                                }) { Text("Rimuovi") }
                            }

                            // immagine opzionale
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
                                    val m = v.filter { it.isDigit() }
                                        .ifBlank { "5" }
                                        .toInt()
                                        .coerceAtLeast(1)
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
                        addDraft("CHOICE") // crea automaticamente la coppia nello stesso stage
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