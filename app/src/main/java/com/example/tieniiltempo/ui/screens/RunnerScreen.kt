package com.example.tieniiltempo.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.location.Location
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.example.tieniiltempo.NavIntents
import com.example.tieniiltempo.data.ActivityTT
import com.example.tieniiltempo.data.Repo
import com.example.tieniiltempo.data.Subtask
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunnerScreen(
    activityId: String,
    onFinished: () -> Unit,
    onBack: () -> Unit
) {
    val ctx = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var act by remember { mutableStateOf<ActivityTT?>(null) }
    var subs by remember { mutableStateOf(listOf<Subtask>()) }
    var loading by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf<String?>(null) }
    var isCaregiver by remember { mutableStateOf(false) }
    val isUser = remember(isCaregiver) { !isCaregiver }

    // ticker per cronometro (ricompone ogni secondo)
    var tick by remember { mutableStateOf(0L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            tick++
        }
    }

    // refresh (serve anche al picker immagine)
    fun refresh() = scope.launch {
        try {
            loading = true
            err = null
            act = Repo.activity(activityId)
            subs = Repo.subtasks(activityId)
            isCaregiver = Repo.currentUser()?.role.equals("caregiver", ignoreCase = true)
        } catch (e: Exception) {
            err = e.localizedMessage
        } finally {
            loading = false
        }
    }

    // stato dialog “nuova sotto-attività”
    var showAdd by remember { mutableStateOf(false) }
    var stTitle by remember { mutableStateOf("") }
    var stDesc by remember { mutableStateOf("") }
    var stStageText by remember { mutableStateOf("1") }
    var stMinutesText by remember { mutableStateOf("5") }
    var stType by remember { mutableStateOf("NORMAL") }
    var addErr by remember { mutableStateOf<String?>(null) }
    var addLoading by remember { mutableStateOf(false) }

    // picker immagine (solo caregiver)
    var pickForSubtaskId by remember { mutableStateOf<String?>(null) }
    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        val stId = pickForSubtaskId ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            scope.launch {
                try {
                    Repo.uploadSubtaskImage(activityId, stId, uri)
                    snackbar.showSnackbar("Immagine caricata")
                    refresh()
                } catch (e: Exception) {
                    snackbar.showSnackbar(e.localizedMessage ?: "Errore upload")
                } finally {
                    pickForSubtaskId = null
                }
            }
        } else {
            pickForSubtaskId = null
        }
    }

    fun prevStagesCompleted(stage: Int): Boolean =
        subs.filter { it.stage < stage }.all { it.completedAt != null }

    LaunchedEffect(activityId) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(act?.title ?: "Attività") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
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
                        stStageText = ((subs.maxOfOrNull { it.stage } ?: 0) + 1).toString()
                        stMinutesText = "5"
                        stType = "NORMAL"
                        showAdd = true
                    },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Sotto-attività") }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (err != null) Text(err!!, color = MaterialTheme.colorScheme.error)

            Text(act?.description.orEmpty(), style = MaterialTheme.typography.bodyMedium)

            // prossimo stage incompleto (per abilitare avvio al caregiver solo lì)
            val nextStage = remember(subs, tick) {
                subs.filter { it.completedAt == null }.minOfOrNull { it.stage } ?: Int.MAX_VALUE
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(subs.size) { i ->
                    val st = subs[i]

                    Card {
                        Column(
                            Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text("${st.title} • Stage ${st.stage}")
                            if (st.description.isNotBlank())
                                Text(st.description, style = MaterialTheme.typography.bodySmall)

                            // rettangolo immagine
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(140.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .let {
                                        if (isCaregiver) it.clickable {
                                            pickForSubtaskId = st.id
                                            imagePicker.launch("image/*")
                                        } else it
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                val url = st.imageUrl
                                if (!url.isNullOrBlank()) {
                                    AsyncImage(
                                        model = url,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                } else {
                                    Text(
                                        if (isCaregiver) "Tocca per aggiungere immagine"
                                        else "Nessuna immagine",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }

                            // tempi con cronometro hh:mm:ss
                            @Suppress("UNUSED_VARIABLE")
                            val tickRef = tick // legge lo state per aggiornarsi
                            val startedAt = st.startedAt?.toDate()?.time
                            val completedAt = st.completedAt?.toDate()?.time
                            val now = System.currentTimeMillis()
                            val end = completedAt ?: now
                            val start = startedAt ?: now
                            val elapsed = (end - start).coerceAtLeast(0L)

                            Text(
                                "Previsti: ${st.expectedMinutes} min • Attuali: ${formatHms(elapsed)}",
                                style = MaterialTheme.typography.bodySmall
                            )

                            // abilitazioni & azioni
                            val stageReady = prevStagesCompleted(st.stage)
                            val userCanStart = !isCaregiver && stageReady && st.startedAt == null
                            val caregiverCanStart =
                                isCaregiver && st.startedAt == null && st.stage == nextStage && stageReady
                            val canStart = userCanStart || caregiverCanStart
                            val canComplete = st.startedAt != null && st.completedAt == null

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        if (canStart) {
                                            scope.launch {
                                                Repo.markSubtaskStarted(activityId, st.id)
                                                // TODO: in futuro inviare notifica allo user
                                                refresh()
                                            }
                                        }
                                    },
                                    enabled = canStart
                                ) {
                                    Text(if (isCaregiver) "Avvia (per utente)" else "Avvia")
                                }

                                Button(
                                    onClick = {
                                        if (canComplete) {
                                            scope.launch {
                                                val actual = (((System.currentTimeMillis())
                                                        - (st.startedAt!!.toDate().time)) / 60000.0).toInt()
                                                val me = Repo.currentUser()!!
                                                Repo.markSubtaskCompleted(
                                                    activityId = activityId,
                                                    subtaskId = st.id,
                                                    expectedMin = st.expectedMinutes,
                                                    actualMin = actual,
                                                    caregiverId = me.caregiverId.ifBlank { me.uid },
                                                    userId = me.uid
                                                )
                                                refresh()
                                                if (subs.all { it.completedAt != null }) onFinished()
                                            }
                                        }
                                    },
                                    enabled = canComplete
                                ) { Text("Completa") }

                                TextButton(onClick = { NavIntents.navToComments(activityId, st.id) }) {
                                    Text("Commenti")
                                }
                            }

                            // posizione
                            if (st.type.equals("LOCATION", true)) {
                                if (!isCaregiver) {
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                val loc = getCurrentLocationOrNull(ctx, snackbar)
                                                if (loc != null) {
                                                    Repo.updateSubtaskLocation(
                                                        activityId, st.id, loc.latitude, loc.longitude
                                                    )
                                                    snackbar.showSnackbar("Posizione inviata")
                                                    refresh()
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Invia posizione") }
                                } else {
                                    val hasLoc = st.location != null
                                    Button(
                                        onClick = {
                                            if (!hasLoc) {
                                                scope.launch {
                                                    snackbar.showSnackbar("Posizione non ancora abilitata")
                                                }
                                            } else {
                                                val geo = st.location!!
                                                openMaps(ctx, geo.latitude, geo.longitude)
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (hasLoc) MaterialTheme.colorScheme.primary else Color.Gray
                                        )
                                    ) { Text("Visualizza posizione") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // dialog: nuova sotto-attività (solo caregiver)
    if (showAdd) {
        AlertDialog(
            onDismissRequest = { if (!addLoading) showAdd = false },
            title = { Text("Nuova sotto-attività") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = stTitle, onValueChange = { stTitle = it },
                        label = { Text("Titolo") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = stDesc, onValueChange = { stDesc = it },
                        label = { Text("Descrizione") }, modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = stStageText,
                            onValueChange = { stStageText = it.filter(Char::isDigit).ifBlank { "1" } },
                            label = { Text("Stage") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = stMinutesText,
                            onValueChange = { stMinutesText = it.filter(Char::isDigit).ifBlank { "5" } },
                            label = { Text("Minuti previsti") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    var showType by remember { mutableStateOf(false) }
                    OutlinedTextField(
                        value = stType,
                        onValueChange = {},
                        label = { Text("Tipo (NORMAL / LOCATION / CHOICE)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showType = true },
                        enabled = false
                    )
                    if (showType) {
                        AlertDialog(
                            onDismissRequest = { showType = false },
                            title = { Text("Scegli tipologia") },
                            text = {
                                Column {
                                    TextButton(onClick = { stType = "NORMAL"; showType = false }) { Text("NORMAL") }
                                    TextButton(onClick = { stType = "LOCATION"; showType = false }) { Text("LOCATION") }
                                    TextButton(onClick = { stType = "CHOICE"; showType = false }) { Text("CHOICE (A/B)") }
                                }
                            },
                            confirmButton = {}
                        )
                    }

                    if (addErr != null) Text(addErr!!, color = MaterialTheme.colorScheme.error)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val titleOk = stTitle.isNotBlank()
                        val stage = stStageText.toIntOrNull() ?: 1
                        val mins = stMinutesText.toIntOrNull() ?: 5
                        if (!titleOk) { addErr = "Inserisci il titolo"; return@TextButton }

                        scope.launch {
                            try {
                                addLoading = true
                                addErr = null
                                val base = Subtask(
                                    title = stTitle.trim(),
                                    description = stDesc.trim(),
                                    stage = stage,
                                    expectedMinutes = mins,
                                    type = stType.ifBlank { "NORMAL" }.uppercase()
                                )
                                if (base.type == "CHOICE") {
                                    Repo.addSubtask(activityId, base.copy(title = "${base.title} (A)"))
                                    Repo.addSubtask(activityId, base.copy(title = "${base.title} (B)"))
                                } else {
                                    Repo.addSubtask(activityId, base)
                                }
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
                TextButton(onClick = { if (!addLoading) showAdd = false }, enabled = !addLoading) { Text("Annulla") }
            }
        )
    }
}

@SuppressLint("MissingPermission")
private suspend fun getCurrentLocationOrNull(
    ctx: Context,
    snackbar: SnackbarHostState
): Location? {
    val fine = androidx.core.content.ContextCompat.checkSelfPermission(
        ctx, Manifest.permission.ACCESS_FINE_LOCATION
    )
    val coarse = androidx.core.content.ContextCompat.checkSelfPermission(
        ctx, Manifest.permission.ACCESS_COARSE_LOCATION
    )
    if (fine != android.content.pm.PackageManager.PERMISSION_GRANTED &&
        coarse != android.content.pm.PackageManager.PERMISSION_GRANTED
    ) {
        snackbar.showSnackbar("Concedi i permessi di localizzazione")
        return null
    }
    return try {
        LocationServices.getFusedLocationProviderClient(ctx).lastLocation.await()
    } catch (e: Exception) { null }
}

private fun openMaps(ctx: Context, lat: Double, lng: Double) {
    val uri = "geo:$lat,$lng?q=$lat,$lng".toUri()
    val i = Intent(Intent.ACTION_VIEW, uri).apply {
        setPackage("com.google.android.apps.maps")
    }
    ctx.startActivity(i)
}

private fun formatHms(ms: Long): String {
    val totalSec = ms / 1000
    val s = (totalSec % 60).toInt()
    val m = ((totalSec / 60) % 60).toInt()
    val h = (totalSec / 3600).toInt()
    return "%02d:%02d:%02d".format(h, m, s)
}