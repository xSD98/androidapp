package com.example.tieniiltempo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.tieniiltempo.core.RealtimeWatchers
import com.example.tieniiltempo.data.ChatMessage
import com.example.tieniiltempo.data.Repo
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

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

    val cId = remember(me.uid, withId) { Repo.chatId(me.uid, withId) }
    val listState = rememberLazyListState()
    val timeFmt = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    // Listener realtime + soppressione notifiche quando la chat è aperta
    DisposableEffect(cId) {
        val db = FirebaseFirestore.getInstance()
        loading = true
        error = null

        // Chat aperta nn notificare nuovi messaggi di questa conversazione
        RealtimeWatchers.setOpenChat(withId)

        val reg = db.collection("chats").document(cId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING) // ordine lato server
            .limit(200)
            .addSnapshotListener { qs, e ->
                if (e != null) {
                    error = e.localizedMessage
                    loading = false
                    return@addSnapshotListener
                }
                if (qs != null) {
                    // Ordina lato client usando fallback su createdAtClient per i messaggi "nuovi"
                    val mapped = qs.documents.mapNotNull { d ->
                        val serverMs = d.getTimestamp("createdAt")?.toDate()?.time
                        val clientMs = d.getTimestamp("createdAtClient")?.toDate()?.time
                        val sortMs = serverMs ?: clientMs ?: 0L
                        val msg = d.toObject(ChatMessage::class.java)?.copy(id = d.id)
                        if (msg != null) sortMs to msg else null
                    }.sortedWith(
                        compareBy<Pair<Long, ChatMessage>> { it.first }
                            .thenBy { it.second.id }
                    ).map { it.second }

                    messages = mapped
                    loading = false
                }
            }

        onDispose {
            reg.remove()
            // Chat chiusa  riattiva le notifiche
            RealtimeWatchers.setOpenChat(null)
        }
    }

    // Auto-scroll all’ultimo messaggio quando cambia la lista
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

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
                Button(
                    onClick = {
                        val txt = text.trim()
                        if (txt.isNotEmpty()) {
                            scope.launch {
                                try {
                                    Repo.sendMessage(fromId = me.uid, toId = withId, text = txt)
                                    text = "" // il listener aggiorna la lista
                                } catch (e: Exception) {
                                    error = e.localizedMessage
                                }
                            }
                        }
                    },
                    enabled = text.isNotBlank()
                ) {
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
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(messages, key = { it.id }) { m ->
                    val mine = m.fromId == me.uid
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
                    ) {
                        Surface(
                            tonalElevation = if (mine) 2.dp else 1.dp,
                            color = if (mine)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                                Text(m.text, style = MaterialTheme.typography.bodyMedium)
                                val whenText = m.createdAt
                                    ?.toDate()
                                    ?.let(timeFmt::format)
                                    .orEmpty() // resta vuoto finché il server non setta createdAt
                                if (whenText.isNotBlank()) {
                                    Text(
                                        whenText,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}