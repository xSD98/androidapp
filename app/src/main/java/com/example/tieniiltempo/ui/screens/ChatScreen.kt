package com.example.tieniiltempo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.tieniiltempo.data.ChatMessage
import com.example.tieniiltempo.data.Repo
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    withId: String,
    onBack: () -> Unit
) {
    val me = Firebase.auth.currentUser ?: return
    val scope = rememberCoroutineScope()

    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var text by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        scope.launch {
            try {
                loading = true
                messages = Repo.loadMessages(withId)
            } catch (e: Exception) {
                error = e.localizedMessage
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(withId) { refresh() }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Chat") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                }
            )
        },
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("Messaggio") },
                    singleLine = true
                )
                Button(onClick = {
                    val txt = text.trim()
                    if (txt.isNotEmpty()) {
                        scope.launch {
                            try {
                                Repo.sendMessage(fromId = me.uid, toId = withId, text = txt)
                                text = ""
                                refresh()
                            } catch (e: Exception) {
                                error = e.localizedMessage
                            }
                        }
                    }
                }) {
                    Text("Invia")
                }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).padding(16.dp)) {
            if (loading) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
            }
            if (error != null) {
                Text(error!!, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                reverseLayout = true // messaggi più recenti in basso, ma lista parte dal fondo
            ) {
                items(messages.size) { idx ->
                    val m = messages[messages.lastIndex - idx] // mostra in ordine cronologico
                    val mine = m.fromId == me.uid
                    Surface(
                        color = if (mine)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Column(Modifier.fillMaxWidth().padding(10.dp)) {
                            Text(m.text, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                m.createdAt.toDate().toString(),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}
