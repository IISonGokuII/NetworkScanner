package com.mycompany.synapse.ui.screens

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mycompany.synapse.data.Repo
import com.mycompany.synapse.game.Domain
import com.mycompany.synapse.reminder.Reminders
import com.mycompany.synapse.ui.Navigator
import com.mycompany.synapse.ui.Screen
import com.mycompany.synapse.ui.components.GlassCard
import com.mycompany.synapse.ui.components.ScreenHeader
import com.mycompany.synapse.ui.components.SecondaryButton
import com.mycompany.synapse.ui.components.SectionTitle
import com.mycompany.synapse.ui.components.SelectChip
import com.mycompany.synapse.ui.theme.Synapse
import com.mycompany.synapse.widget.DailyPuzzleWidget
import java.time.LocalDate

@Composable
fun MoreScreen(nav: Navigator) {
    val c = Synapse.colors
    val context = LocalContext.current
    val accent = c.domain(Domain.RECHNEN)
    var confirmReset by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<String?>(null) }

    var permissionFor by remember { mutableStateOf("daily") }
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (permissionFor == "daily") Repo.setReminder(granted) else Repo.setReviewNotif(granted)
        Reminders.schedule(context)
        if (!granted) Toast.makeText(context, "Ohne Erlaubnis kann keine Benachrichtigung angezeigt werden", Toast.LENGTH_LONG).show()
    }
    fun needsPermission() = Build.VERSION.SDK_INT >= 33 &&
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    val autoBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        Repo.setAutoBackup(uri.toString())
        val ok = Repo.writeAutoBackup(context)
        Toast.makeText(context, if (ok) "Automatische Sicherung eingerichtet" else "Datei nicht beschreibbar", Toast.LENGTH_SHORT).show()
        if (!ok) Repo.setAutoBackup(null)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val ok = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { it.write(Repo.exportJson().toByteArray()) }
        }.isSuccess
        Toast.makeText(context, if (ok) "Sicherung gespeichert" else "Speichern fehlgeschlagen", Toast.LENGTH_SHORT).show()
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        pendingImport = runCatching {
            context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
        }.getOrNull()
        if (pendingImport == null) Toast.makeText(context, "Datei konnte nicht gelesen werden", Toast.LENGTH_SHORT).show()
    }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Mehr", "Einstellungen, Duell & Daten")
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GlassCard(Modifier.fillMaxWidth(), accent = profileColor(Repo.profile.color), onClick = { nav.push(Screen.Profiles) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProfileAvatar(Repo.profile)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Profil: ${Repo.profile.name}", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                        Text(
                            if (Repo.profiles.size > 1) "${Repo.profiles.size} Profile · tippen zum Wechseln" else "Weitere Profile für Familie oder Freunde anlegen",
                            style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
                        )
                    }
                }
            }
            GlassCard(Modifier.fillMaxWidth(), accent = c.domain(Domain.SPRACHE), onClick = { nav.push(Screen.Duel) }) {
                Text("Duell-Modus", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                Text(
                    "Zu zweit an einem Handy: Wer rechnet schneller? Das Handy liegt zwischen euch.",
                    style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
                )
            }

            SectionTitle("Darstellung")
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Design", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("System", "Dunkel", "Hell").forEachIndexed { i, s ->
                        SelectChip(s, Repo.themeMode == i, accent) { Repo.setTheme(i) }
                    }
                }
            }
            GlassCard(Modifier.fillMaxWidth()) {
                SettingSwitch("Klänge", "Sanfte Töne bei richtig und falsch", Repo.soundOn) { Repo.setSound(it) }
                SettingSwitch("Vibration", "Kurzer Impuls als Rückmeldung", Repo.hapticsOn) { Repo.setHaptics(it) }
                SettingSwitch("Bewegung reduzieren", "Keine Partikel, kein Wackeln, ruhiges Neuronen-Netz", Repo.reduceMotion) { Repo.changeReduceMotion(it) }
                SettingSwitch("Farbschwäche-Modus", "Farben, die auch bei Rot-Grün-Schwäche unterscheidbar sind (pro Profil)", Repo.cvdMode) { Repo.changeCvdMode(it) }
            }

            SectionTitle("Training")
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Wochenziel", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                Text("An wie vielen Tagen pro Woche willst du trainieren?", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (d in 2..7) SelectChip("$d", Repo.weeklyGoal == d, accent) { Repo.changeWeeklyGoal(d) }
                }
            }
            GlassCard(Modifier.fillMaxWidth(), onClick = { nav.push(Screen.Test(Repo.KIND_PLACEMENT)) }) {
                Text(if (Repo.placementDone) "Einstufung wiederholen" else "Einstufungstest", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                Text("5 Minuten · setzt die Start-Level aller Spiele passend", style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
            }
            GlassCard(Modifier.fillMaxWidth(), onClick = { nav.push(Screen.Test(Repo.KIND_BENCHMARK)) }) {
                Text("Leistungstest", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                Text(
                    Repo.benchmarkRuns.lastOrNull()?.let { "Zuletzt ${relativeDay(it.run)}: ${it.total} Punkte · empfohlen alle 4 Wochen" }
                        ?: "Feste Schwierigkeit – zeigt über Monate, ob du besser wirst",
                    style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
                )
            }
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Länge des Tagestrainings", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SelectChip("3 Spiele · ~7 Min.", Repo.workoutSize == 3, accent) { Repo.changeWorkoutSize(3) }
                    SelectChip("5 Spiele · ~12 Min.", Repo.workoutSize == 5, accent) { Repo.changeWorkoutSize(5) }
                }
            }
            GlassCard(Modifier.fillMaxWidth()) {
                SettingSwitch(
                    "Tägliche Erinnerung",
                    "Nur wenn du an dem Tag noch nicht trainiert hast",
                    Repo.reminderOn,
                ) { on ->
                    if (on && needsPermission()) {
                        permissionFor = "daily"
                        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        Repo.setReminder(on)
                        Reminders.schedule(context)
                    }
                }
                if (Repo.reminderOn) {
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Uhrzeit", style = MaterialTheme.typography.bodyLarge, color = c.textSecondary, modifier = Modifier.weight(1f))
                        TimeStepper(Repo.reminderHour, Repo.reminderMinute) { h, m ->
                            Repo.setReminder(true, h, m)
                            Reminders.schedule(context)
                        }
                    }
                }
                SettingSwitch("Wochenrückblick", "Sonntags um 19 Uhr eine kurze Zusammenfassung deiner Woche", Repo.reviewNotifOn) { on ->
                    if (on && needsPermission()) {
                        permissionFor = "weekly"
                        notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        Repo.setReviewNotif(on)
                        Reminders.schedule(context)
                    }
                }
            }
            GlassCard(Modifier.fillMaxWidth(), onClick = {
                val manager = context.getSystemService(AppWidgetManager::class.java)
                val provider = ComponentName(context, DailyPuzzleWidget::class.java)
                if (manager != null && manager.isRequestPinAppWidgetSupported) {
                    manager.requestPinAppWidget(provider, null, null)
                } else {
                    Toast.makeText(context, "Startbildschirm lange drücken → Widgets → Synapse", Toast.LENGTH_LONG).show()
                }
            }) {
                Text("Widget „Aufgabe des Tages“", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                val p = DailyPuzzleWidget.puzzleFor(LocalDate.now())
                Text(
                    "Heute: ${p.shown.joinToString(", ")}, ? · Tippen, um es zum Startbildschirm hinzuzufügen",
                    style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
                )
            }

            SectionTitle("Deine Daten")
            GlassCard(Modifier.fillMaxWidth()) {
                Text(
                    "Alles bleibt auf deinem Handy – auch das Android-Cloud-Backup ist abgeschaltet. Mit einer Sicherungsdatei " +
                        "nimmst du den Fortschritt dieses Profils auf ein neues Gerät mit.",
                    style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Sichern", Modifier.weight(1f)) { exportLauncher.launch("synapse-backup-${LocalDate.now()}.json") }
                    SecondaryButton("Wiederherstellen", Modifier.weight(1f)) { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) }
                }
                Spacer(Modifier.height(10.dp))
                SecondaryButton("Fortschritt zurücksetzen", Modifier.fillMaxWidth(), color = c.bad) { confirmReset = true }
            }
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Automatische Sicherung", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                Text(
                    if (Repo.autoBackupUri == null) "Einmal eine Datei wählen – danach aktualisiert die App sie täglich beim Öffnen. " +
                        "Liegt die Datei in einem synchronisierten Ordner, ist sie auch außerhalb des Handys sicher."
                    else "Aktiv · zuletzt: " + if (Repo.autoBackupLast > 0) relativeDay(Repo.autoBackupLast) else "noch nie",
                    style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
                )
                Spacer(Modifier.height(10.dp))
                if (Repo.autoBackupUri == null) {
                    SecondaryButton("Datei wählen", Modifier.fillMaxWidth()) { autoBackupLauncher.launch("synapse-auto-${Repo.profile.name}.json") }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton("Jetzt sichern", Modifier.weight(1f)) {
                            val ok = Repo.writeAutoBackup(context)
                            Toast.makeText(context, if (ok) "Gesichert" else "Kein Zugriff mehr – bitte neu einrichten", Toast.LENGTH_SHORT).show()
                        }
                        SecondaryButton("Ausschalten", Modifier.weight(1f)) { Repo.setAutoBackup(null) }
                    }
                }
            }

            SectionTitle("Über Synapse")
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Synapse ${com.mycompany.synapse.BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
                Spacer(Modifier.height(4.dp))
                Text(
                    "100 % kostenlos · keine Werbung · kein Konto · keine Tracker.\n" +
                        "Die App hat keine Internet-Berechtigung – sie kann technisch gar nichts senden.",
                    style = MaterialTheme.typography.bodyMedium, color = c.textSecondary,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Hinweis: Gehirntraining macht dich vor allem in den geübten Aufgaben besser. Ob das auf den Alltag " +
                        "übertragbar ist, ist wissenschaftlich umstritten. Was nachweislich hilft: Abwechslung, Aufgaben am " +
                        "Rand des eigenen Könnens, Regelmäßigkeit – sowie Schlaf und Bewegung.",
                    style = MaterialTheme.typography.bodyMedium, color = c.textMuted,
                )
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Fortschritt löschen?") },
            text = { Text("Alle Sessions, Level, Erfolge und Tests des Profils „${Repo.profile.name}“ werden gelöscht. Einstellungen bleiben erhalten. Das kann nicht rückgängig gemacht werden.") },
            confirmButton = {
                TextButton(onClick = { Repo.resetProgress(); confirmReset = false }) { Text("Löschen", color = c.bad) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Abbrechen") } },
            containerColor = c.surfaceStrong, titleContentColor = c.textPrimary, textContentColor = c.textSecondary,
        )
    }
    pendingImport?.let { text ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Sicherung wiederherstellen?") },
            text = { Text("Der Fortschritt des Profils „${Repo.profile.name}“ wird durch die Sicherung ersetzt.") },
            confirmButton = {
                TextButton(onClick = {
                    val result = runCatching { Repo.importJson(text) }
                    pendingImport = null
                    Reminders.schedule(context)
                    Toast.makeText(
                        context,
                        result.fold({ "$it Sessions wiederhergestellt" }, { "Keine gültige Synapse-Sicherung" }),
                        Toast.LENGTH_LONG,
                    ).show()
                }) { Text("Wiederherstellen") }
            },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text("Abbrechen") } },
            containerColor = c.surfaceStrong, titleContentColor = c.textPrimary, textContentColor = c.textSecondary,
        )
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Synapse.colors
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = c.textPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.textSecondary)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = c.domain(Domain.RECHNEN),
                checkedThumbColor = c.surfaceStrong,
                uncheckedTrackColor = c.gridLine,
                uncheckedBorderColor = c.stroke,
                uncheckedThumbColor = c.textMuted,
            ),
        )
    }
}

@Composable
private fun TimeStepper(hour: Int, minute: Int, onChange: (Int, Int) -> Unit) {
    val c = Synapse.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        TextButton(onClick = {
            val t = (hour * 60 + minute - 15 + 1440) % 1440
            onChange(t / 60, t % 60)
        }) { Text("−", color = c.textPrimary, style = MaterialTheme.typography.titleLarge) }
        Text(
            "%02d:%02d".format(hour, minute), color = c.textPrimary,
            style = MaterialTheme.typography.titleLarge.copy(fontFeatureSettings = "tnum"),
        )
        TextButton(onClick = {
            val t = (hour * 60 + minute + 15) % 1440
            onChange(t / 60, t % 60)
        }) { Text("+", color = c.textPrimary, style = MaterialTheme.typography.titleLarge) }
    }
}
