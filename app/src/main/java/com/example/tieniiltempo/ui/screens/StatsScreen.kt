package com.example.tieniiltempo.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun StatsScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = { TiTopBar(title = "Statistiche", onBack = onBack) }
    ) { pad ->
        Column(
            Modifier
                .padding(pad)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                "Qui potresti mostrare medie, tempo speso, trend settimanali…",
                style = MaterialTheme.typography.bodyMedium
            )

            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("Totale attività completate: (placeholder)")
                }
            }

            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("Media sforamento: (placeholder)")
                }
            }
        }
    }
}
