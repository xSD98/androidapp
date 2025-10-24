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
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaregiverDashboardScreen(
    openUser: (String) -> Unit,
    openChatList: () -> Unit,
    onLogout: () -> Unit,
    // nuovo: apri la schermata premi/streak dell’utente selezionato
    openGamification: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val meUid = Firebase.auth.currentUser?.uid

    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var assigned by remember { mutableStateOf(listOf<AppUser>()) }

    // dialog “assegna”
    var showAssign by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf(listOf<AppUser>()) }
    var searching by remember { mutableStateOf(false) }
    var assignError by remember { mutableStateOf<String?>(null) }

    fun refresh() {
        if (meUid == null) {
            onLogout()
            return
        }
        scope.launch {
            loading = true
            error = null
            try {
                // Mostra solo utenti (role=user), mai caregiver
                assigned = Repo.caregiverUsers(meUid).filter { it.role.equals("user", true) }
            } catch (e: Exception) {
                error = e.localizedMessage ?: "Errore di caricamento"
            } finally {
                loading = false
            }
        }
    }

    LaunchedEffect(meUid) { refresh() }

    Scaffold(
        topBar = {
            TiTopBar(
                title = "Dashboard caregiver",
                onBack = null,
                onLogout = onLogout,
                actions = {
                    TextButton(onClick = openChatList) { Text("Chat") }
                    TextButton(
                        onClick = {
                            assignError = null
                            search = ""
                            searchResults = emptyList()
                            showAssign = true
                        }
                    ) { Text("Assegna utente") }
                }
            )
        }
    ) { pad ->
        Column(
            Modifier.padding(pad).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Utenti assegnati", style = MaterialTheme.typography.titleMedium)
                    if (assigned.isEmpty() && !loading) {
                        Text("Nessun utente assegnato.")
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(assigned) { u ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { openUser(u.uid) },
                                    tonalElevation = 1.dp,
                                    shape = MaterialTheme.shapes.medium
                                ) {
                                    Row(
                                        Modifier.padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(u.displayName.ifBlank { u.email })
                                            Text("uid: ${u.uid}", style = MaterialTheme.typography.bodySmall)
                                        }
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            OutlinedButton(
                                                onClick = { openGamification(u.uid) }
                                            ) { Text("Premi") }

                                            OutlinedButton(
                                                onClick = {
                                                    scope.launch {
                                                        try {
                                                            Repo.assignUserToCaregiver(u.uid, "")
                                                            refresh()
                                                        } catch (e: Exception) {
                                                            error = e.localizedMessage
                                                        }
                                                    }
                                                }
                                            ) { Text("Dissocia") }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- DIALOG ASSEGNA UTENTE ---------------------------------------------------
    if (showAssign) {
        AlertDialog(
            onDismissRequest = { showAssign = false },
            title = { Text("Assegna utente") },
            text = {
                // Contenitore scrollabile con altezza max così la lista non viene tagliata
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 0.dp, max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = search,
                        onValueChange = { search = it },
                        label = { Text("Cerca utente (nome o email)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (meUid == null) return@Button
                                scope.launch {
                                    searching = true
                                    assignError = null
                                    searchResults = emptyList()
                                    try {
                                        // Repo.searchUnassignedUsers supporta già displayName; se vuoi filtrare per email
                                        // puoi aggiungerlo lato Repo. Qui filtriamo solo utenti (role=user).
                                        searchResults = Repo.searchUnassignedUsers(search)
                                            .filter { it.role.equals("user", true) }
                                    } catch (e: Exception) {
                                        assignError = e.localizedMessage
                                    } finally {
                                        searching = false
                                    }
                                }
                            },
                            enabled = !searching
                        ) {
                            if (searching) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Cerca")
                            }
                        }

                        TextButton(
                            onClick = {
                                search = ""
                                searchResults = emptyList()
                                assignError = null
                            }
                        ) { Text("Pulisci") }
                    }

                    if (assignError != null) {
                        Text(assignError!!, color = MaterialTheme.colorScheme.error)
                    }

                    when {
                        searching -> Text("Ricerca in corso…")
                        search.isNotBlank() && searchResults.isEmpty() && assignError == null ->
                            Text("Nessun utente trovato.")
                    }

                    if (searchResults.isNotEmpty()) {
                        HorizontalDivider()
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(searchResults) { u ->
                                Surface(
                                    tonalElevation = 1.dp,
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(u.displayName.ifBlank { u.email })
                                            Text("uid: ${u.uid}", style = MaterialTheme.typography.bodySmall)
                                        }
                                        Button(
                                            onClick = {
                                                val caregiverId = meUid ?: return@Button
                                                scope.launch {
                                                    try {
                                                        assignError = null
                                                        Repo.assignUserToCaregiver(u.uid, caregiverId)
                                                        showAssign = false
                                                        refresh()
                                                    } catch (e: Exception) {
                                                        assignError = e.localizedMessage
                                                    }
                                                }
                                            }
                                        ) { Text("Assegna") }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showAssign = false }) { Text("Chiudi") }
            }
        )
    }
}