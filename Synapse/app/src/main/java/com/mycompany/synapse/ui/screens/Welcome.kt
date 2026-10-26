package com.mycompany.synapse.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mycompany.synapse.game.Domain
import com.mycompany.synapse.ui.components.Dot
import com.mycompany.synapse.ui.components.GlassCard
import com.mycompany.synapse.ui.components.PrimaryButton
import com.mycompany.synapse.ui.theme.Synapse

@Composable
fun WelcomeScreen(onStart: () -> Unit) {
    val c = Synapse.colors
    val demo = Domain.entries.associateWith { 0.35f + it.ordinal * 0.11f }
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            NeuralNetwork(
                vitality = demo, indices = emptyMap(),
                workoutColors = listOf(c.domain(Domain.RECHNEN), c.domain(Domain.LOGIK), c.domain(Domain.GEDAECHTNIS)),
                workoutDone = listOf(false, false, false),
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                onDomain = {}, onCenter = {},
            )
            Text("Synapse", style = MaterialTheme.typography.displayMedium, color = c.textPrimary)
            Text(
                "Dein Gehirn-Fitnessstudio – ohne Werbung, ohne Abo, ohne Internet.",
                style = MaterialTheme.typography.bodyLarge, color = c.textSecondary, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            GlassCard(Modifier.fillMaxWidth()) {
                Bullet(c.domain(Domain.RECHNEN), "15 Spiele in 6 Bereichen", "Rechnen, Logik, Gedächtnis, Fokus, Raumdenken und Sprache.")
                Bullet(c.domain(Domain.LOGIK), "Passt sich dir an", "50 Stufen pro Spiel – immer am Rand deines Könnens, nie langweilig, nie frustrierend.")
                Bullet(c.domain(Domain.GEDAECHTNIS), "Fortschritt sichtbar", "Gehirn-Index, Profil, Verlaufskurven und Kalender zeigen, wie du besser wirst.")
                Bullet(c.domain(Domain.FOKUS), "Dein Netz wächst", "Jeder Bereich ist ein Knoten. Trainierst du ihn, leuchtet er heller.")
            }
        }
        PrimaryButton("Los geht's", Modifier.fillMaxWidth().padding(22.dp), onClick = onStart)
    }
}

@Composable
private fun Bullet(color: Color, title: String, text: String) {
    val c = Synapse.colors
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.padding(top = 6.dp)) { Dot(color, 10.dp) }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        }
    }
}
