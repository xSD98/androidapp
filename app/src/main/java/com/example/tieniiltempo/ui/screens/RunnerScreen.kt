package com.example.tieniiltempo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.tieniiltempo.data.ActivityTT
import com.example.tieniiltempo.data.Repo
import com.example.tieniiltempo.data.Subtask
import kotlinx.coroutines.launch
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RunnerScreen(
    activityId: String,
    onFinished: () -> Unit,
    onBack: () -> Unit,               // <-- aggiunto
) {
    val scope = rememberCoroutineScope()

    var act by remember { mutableStateOf<ActivityTT?>(null) }
    var subs by remember { mutableStateOf(listOf<Subtask>()) }
    var loading by remember { mutableStateOf(true) }
    var err by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(activityId) {
        try {
            loading = true
            act = Repo.activity(activityId)
            subs = Repo.subtasks(activityId)
        } catch (e: Exception) {
            err = e.localizedMessage
        } finally {
            loading = false
        }
    }

    fun canStart(st: Subtask): Boolean {
        // consenti l'avvio solo se nella stessa stage non c'è un’altra subtask già avviata
        val sameStage = subs.filter { it.stage == st.stage }
        return sameStage.all { it.startedAt == null || it.id == st.id }
    }

    fun canComplete(st: Subtask): Boolean =
        st.startedAt != null && st.completedAt == null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(act?.title ?: "Runner") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("←") }
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
            err?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Text(act?.description.orEmpty())

            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(subs.size) { i ->
                    val st = subs[i]
                    Card {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("${st.title} • Stage ${st.stage}")
                            if (st.description.isNotBlank()) {
                                Text(st.description, style = MaterialTheme.typography.bodySmall)
                            }

                            val startedAt = st.startedAt?.toDate()?.time
                            val completedAt = st.completedAt?.toDate()?.time
                            val now = System.currentTimeMillis()
                            val actualMin = ((if (completedAt != null) completedAt else now) - (startedAt ?: now)) / 60000.0
                            Text(
                                "Previsti: ${st.expectedMinutes} min • Attuali: ${max(0.0, actualMin).toInt()} min",
                                style = MaterialTheme.typography.bodySmall
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        if (st.startedAt == null && canStart(st)) {
                                            scope.launch {
                                                Repo.markSubtaskStarted(activityId, st.id)
                                                subs = Repo.subtasks(activityId)
                                            }
                                        }
                                    },
                                    enabled = st.startedAt == null && canStart(st)
                                ) { Text("Avvia") }

                                Button(
                                    onClick = {
                                        if (canComplete(st)) {
                                            scope.launch {
                                                val actual = (
                                                        (System.currentTimeMillis() - (st.startedAt!!.toDate().time)) / 60000.0
                                                        ).toInt()
                                                val me = Repo.currentUser()!!
                                                me.caregiverId?.let {
                                                    Repo.markSubtaskCompleted(
                                                        activityId = activityId,
                                                        subtaskId = st.id,
                                                        expectedMin = st.expectedMinutes,
                                                        actualMin = actual,
                                                        caregiverId = it.ifBlank { me.uid },
                                                        userId = me.uid
                                                    )
                                                }
                                                subs = Repo.subtasks(activityId)
                                                val allDone = subs.all { it.completedAt != null }
                                                if (allDone) onFinished()
                                            }
                                        }
                                    },
                                    enabled = canComplete(st)
                                ) { Text("Completa") }
                            }
                        }
                    }
                }
            }
        }
    }
}
