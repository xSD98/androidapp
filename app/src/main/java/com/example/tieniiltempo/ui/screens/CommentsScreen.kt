package com.example.tieniiltempo.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.tieniiltempo.data.Repo
import com.example.tieniiltempo.data.SubtaskComment
import com.google.firebase.ktx.Firebase
import com.google.firebase.auth.ktx.auth
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsScreen(
    activityId: String,
    subtaskId: String,
    onBack: () -> Unit
) {
    val me = Firebase.auth.currentUser ?: return
    val scope = rememberCoroutineScope()

    var rows by remember { mutableStateOf(listOf<SubtaskComment>()) }
    var text by remember { mutableStateOf("") }
    var pickedImage by remember { mutableStateOf<Uri?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    val pickImageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> pickedImage = uri }

    fun refresh() {
        scope.launch {
            try {
                loading = true
                rows = Repo.listSubtaskComments(activityId, subtaskId)
            } catch (e: Exception) {
                error = e.localizedMessage
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(activityId, subtaskId) { refresh() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Commenti") },
                navigationIcon = {},
                actions = { TextButton(onClick = onBack) { Text("Indietro") } }
            )
        },
        bottomBar = {
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Scrivi un commento") }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        pickImageLauncher.launch(
                            androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }) { Text(if (pickedImage == null) "Aggiungi immagine" else "Immagine selezionata ✓") }

                    Button(onClick = {
                        val t = text.trim()
                        if (t.isEmpty() && pickedImage == null) return@Button
                        scope.launch {
                            try {
                                error = null
                                Repo.addSubtaskComment(
                                    activityId = activityId,
                                    subtaskId = subtaskId,
                                    authorId = me.uid,
                                    authorRole = if ((Repo.currentUser()?.role ?: "user") == "caregiver") "caregiver" else "user",
                                    text = t,
                                    imageUri = pickedImage
                                )
                                text = ""
                                pickedImage = null
                                refresh()
                            } catch (e: Exception) {
                                error = e.localizedMessage
                            }
                        }
                    }) { Text("Invia") }
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f, fill = true)) {
                items(rows.size) { i ->
                    val c = rows[i]
                    Card {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${if (c.authorRole == "caregiver") "Caregiver" else "Utente"}:", style = MaterialTheme.typography.bodySmall)
                            if (c.text.isNotBlank()) Text(c.text)
                            if (!c.imageUrl.isNullOrBlank()) {
                                AsyncImage(model = c.imageUrl, contentDescription = null, modifier = Modifier.fillMaxWidth().height(180.dp))
                            }
                            Text(c.createdAt?.toDate()?.toString() ?: "", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}