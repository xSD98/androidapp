package com.example.tieniiltempo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.tieniiltempo.data.AppUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.ktx.Firebase

@Composable
fun LoginScreen(onLoggedIn: () -> Unit) {
    val auth = Firebase.auth
    val db = FirebaseFirestore.getInstance()

    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") } // "CARE-2025" => caregiver
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    fun doLogin() {
        error = null; loading = true
        auth.signInWithEmailAndPassword(email.trim(), pass)
            .addOnSuccessListener { onLoggedIn() }
            .addOnFailureListener { e -> error = e.localizedMessage }
            .addOnCompleteListener { loading = false }
    }

    fun doRegister() {
        error = null; loading = true
        val CARE_TOKEN = "CARE-2025"
        auth.createUserWithEmailAndPassword(email.trim(), pass)
            .addOnSuccessListener { res ->
                val uid = res.user!!.uid
                val isCaregiver = token.equals(CARE_TOKEN, true)
                val u = AppUser(
                    uid = uid,
                    email = email.trim(),
                    displayName = email.substringBefore("@"),
                    role = if (isCaregiver) "caregiver" else "user",
                    caregiverId = "null"

                )
                db.collection("users").document(uid).set(u)
                    .addOnSuccessListener { onLoggedIn() }
                    .addOnFailureListener { e -> error = e.localizedMessage }
                    .addOnCompleteListener { loading = false }
            }
            .addOnFailureListener { e -> error = e.localizedMessage; loading = false }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Card(Modifier.padding(24.dp).fillMaxWidth(0.92f)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Tieni il tempo — accesso", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(pass, { pass = it }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(token, { token = it }, label = { Text("Token caregiver (opz.)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = { doRegister() }, enabled = !loading, modifier = Modifier.weight(1f)) { Text("Registrati") }
                    OutlinedButton(onClick = { doLogin() }, enabled = !loading, modifier = Modifier.weight(1f)) { Text("Login") }
                }
            }
        }
    }
}
