package com.mycompany.synapse.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mycompany.synapse.data.Profile
import com.mycompany.synapse.data.Repo
import com.mycompany.synapse.game.Domain
import com.mycompany.synapse.ui.Navigator
import com.mycompany.synapse.ui.components.GlassCard
import com.mycompany.synapse.ui.components.Pill
import com.mycompany.synapse.ui.components.PrimaryButton
import com.mycompany.synapse.ui.components.ScreenHeader
import com.mycompany.synapse.ui.theme.Synapse

@Composable
fun profileColor(index: Int): Color = Synapse.colors.domain(Domain.entries[index % Domain.entries.size])

@Composable
fun ProfileAvatar(p: Profile, size: Int = 40) {
    val col = profileColor(p.color)
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(col.copy(alpha = 0.25f)).border(2.dp, col, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(p.name.take(1).uppercase(), color = Synapse.colors.textPrimary, fontWeight = FontWeight.Black, fontSize = (size * 0.42f).sp)
    }
}

@Composable
fun ProfilesScreen(nav: Navigator) {
    val c = Synapse.colors
    var editing by remember { mutableStateOf<Profile?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Profile?>(null) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Profile", "Jede Person hat eigenen Fortschritt", onBack = { nav.pop() })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            for (p in Repo.profiles) {
                val active = p.id == Repo.profileId
                GlassCard(
                    Modifier.fillMaxWidth(), accent = if (active) profileColor(p.color) else null,
                    onClick = { if (!active) { Repo.switchProfile(p.id); nav.home() } },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ProfileAvatar(p)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                            Text(
                                if (active) "Aktiv · ${Repo.records.size} Sessions" else "Tippen zum Wechseln",
                                style = MaterialTheme.typography.labelMedium, color = c.textSecondary,
                            )
                        }
                        if (active) Pill("aktiv", profileColor(p.color))
                        Text(
                            "Bearbeiten", color = c.textSecondary, style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(start = 10.dp).clickable { editing = p }.padding(6.dp),
                        )
                    }
                }
            }
            Text(
                "Profile sind praktisch für die Familie: Jede Person hat eigene Level, Statistiken, Erfolge und Einstellungen " +
                    "wie Wochenziel oder Farbschwäche-Modus. Design, Klänge und Erinnerung gelten für das ganze Gerät.",
                style = MaterialTheme.typography.bodyMedium, color = c.textMuted,
            )
        }
        PrimaryButton("Neues Profil", Modifier.fillMaxWidth().padding(18.dp)) { creating = true }
    }

    if (creating || editing != null) {
        val original = editing
        var name by remember(original) { mutableStateOf(original?.name ?: "") }
        var color by remember(original) { mutableIntStateOf(original?.color ?: (Repo.profiles.size % 6)) }
        AlertDialog(
            onDismissRequest = { creating = false; editing = null },
            title = { Text(if (original == null) "Neues Profil" else "Profil bearbeiten") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it.take(20) }, label = { Text("Name") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (i in 0 until 6) {
                            val col = profileColor(i)
                            Box(
                                Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(col)
                                    .border(if (i == color) 3.dp else 0.dp, c.textPrimary, CircleShape)
                                    .clickable { color = i }
                                    .semantics { contentDescription = "Farbe ${i + 1}" + if (i == color) ", gewählt" else "" }
                            )
                        }
                    }
                    if (original != null && Repo.profiles.size > 1) {
                        Text(
                            "Profil löschen", color = c.bad, style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.clickable { deleting = original; editing = null }.padding(vertical = 6.dp),
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (original == null) {
                        val p = Repo.addProfile(name, color)
                        Repo.switchProfile(p.id)
                        creating = false
                        nav.home()
                    } else {
                        Repo.renameProfile(original.id, name, color)
                        editing = null
                    }
                }) { Text(if (original == null) "Anlegen & wechseln" else "Speichern") }
            },
            dismissButton = { TextButton(onClick = { creating = false; editing = null }) { Text("Abbrechen") } },
            containerColor = c.surfaceStrong, titleContentColor = c.textPrimary, textContentColor = c.textSecondary,
        )
    }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("„${p.name}“ löschen?") },
            text = { Text("Alle Sessions, Level und Erfolge dieses Profils werden endgültig gelöscht.") },
            confirmButton = { TextButton(onClick = { Repo.deleteProfile(p.id); deleting = null }) { Text("Löschen", color = c.bad) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Abbrechen") } },
            containerColor = c.surfaceStrong, titleContentColor = c.textPrimary, textContentColor = c.textSecondary,
        )
    }
}
