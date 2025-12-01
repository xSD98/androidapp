package com.example.tieniiltempo.ui.screens

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.tieniiltempo.data.Repo
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditActivityScreen(
    activityId: String,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var desc  by remember { mutableStateOf("") }
    var scheduledMs by remember { mutableStateOf<Long?>(null) }
    var loading by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf<String?>(null) }

    // load current
    LaunchedEffect(activityId) {
        try {
            loading = true; err = null
            val d = FirebaseFirestore.getInstance().collection("activities").document(activityId).get().await()
            title = d.getString("title").orEmpty()
            desc  = d.getString("description").orEmpty()
            scheduledMs = d.getTimestamp("scheduledAt")?.toDate()?.time ?: d.getLong("scheduledAtMs")
        } catch (e: Exception) {
            err = e.localizedMessage
        } finally { loading = false }
    }

    fun pickSchedule() {
        val cal = Calendar.getInstance()
        val now = cal.timeInMillis
        DatePickerDialog(ctx, { _, y,m,day ->
            cal.set(y,m,day)
            TimePickerDialog(ctx, {_,hh,mm ->
                cal.set(Calendar.HOUR_OF_DAY, hh)
                cal.set(Calendar.MINUTE, mm)
                cal.set(Calendar.SECOND, 0)
                val picked = cal.timeInMillis
                scheduledMs = if (picked < now) null else picked
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Modifica attività") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Indietro") } },
                actions = {
                    TextButton(onClick = {
                        scope.launch {
                            try {
                                loading = true; err = null
                                Repo.updateActivity(activityId, title.trim(), desc.trim(), scheduledMs)
                                onSaved()
                            } catch (e: Exception) { err = e.localizedMessage }
                            finally { loading = false }
                        }
                    }, enabled = !loading) { Text("Salva") }
                }
            )
        }
    ) { pad ->
        Column(Modifier.padding(pad).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            err?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            OutlinedTextField(title, { title = it }, label = { Text("Titolo") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(desc,  { desc  = it }, label = { Text("Descrizione") }, modifier = Modifier.fillMaxWidth())

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { pickSchedule() }) { Text(if (scheduledMs==null) "Pianifica" else "Modifica orario") }
                if (scheduledMs != null) {
                    TextButton(onClick = { scheduledMs = null }) { Text("Rimuovi pianificazione") }
                }
            }
        }
    }
}