package com.example.tieniiltempo.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.tieniiltempo.data.Repo

@Composable
fun RoleGateScreen(onCaregiver: () -> Unit, onUser: (String) -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            val me = Repo.currentUser()
            when {
                me == null -> err = "Profilo non trovato"
                me.role.equals("caregiver", true) -> onCaregiver()
                else -> onUser(me.uid)
            }
        } catch (e: Exception) {
            err = e.localizedMessage
        } finally {
            loading = false
        }
    }

    Box(Modifier, Alignment.Center) {
        if (loading) CircularProgressIndicator() else Text(err ?: "")
    }
}
