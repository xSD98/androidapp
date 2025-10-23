package com.example.tieniiltempo.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.tieniiltempo.data.AppUser
import com.example.tieniiltempo.data.Repo
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// icone corrette (material, non material3)
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onBack: () -> Unit,
    openChat: (String) -> Unit
) {
    val me = Firebase.auth.currentUser!!
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var rows by remember { mutableStateOf(listOf<AppUser>()) }
    var isCaregiver by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            loading = true
            error = null
            try {
                val myProfile = Repo.currentUser()
                // role è una String nel DB
                isCaregiver = myProfile?.role.equals("caregiver", ignoreCase = true)

                rows = if (isCaregiver) {
                    // caregiver: mostra tutti gli utenti assegnati
                    Repo.caregiverUsers(me.uid)
                } else {
                    // utente: mostra solo il caregiver (se presente)
                    val cgId = myProfile?.caregiverId
                    if (!cgId.isNullOrBlank()) {
                        val snap = FirebaseFirestore.getInstance()
                            .collection("users").document(cgId).get().await()
                        snap.toObject(AppUser::class.java)?.copy(uid = cgId)?.let { listOf(it) }
                            ?: emptyList()
                    } else emptyList()
                }
            } catch (e: Exception) {
                error = e.localizedMessage
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(Unit) { load() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Indietro"
                        )
                    }
                },
                actions = {
                    if (isCaregiver) {
                        IconButton(onClick = { load() /* qui in futuro apri un picker utente */ }) {
                            Icon(imageVector = Icons.Filled.Add, contentDescription = "Nuova chat")
                        }
                    }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)

            if (rows.isEmpty() && !loading && error == null) {
                Text(
                    if (isCaregiver) "Nessun utente assegnato."
                    else "Nessun caregiver collegato al tuo profilo."
                )
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(rows) { u ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        tonalElevation = 1.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { openChat(u.uid) }
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(u.displayName.ifBlank { u.email })
                            Text(
                                if (isCaregiver) "uid: ${u.uid}" else "Caregiver",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}
