package io.github.brickystudios.stundenblick

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.LocalDate

const val REPO_URL = "https://github.com/BrickyStudios/stundenblick"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(repo: Repo, current: Element?, onBack: () -> Unit, onSetOwn: () -> Unit, onLoggedOut: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val p = repo.profile.value
    val master = repo.master.value
    var notify by remember { mutableStateOf(repo.notify) }
    var updates by remember { mutableStateOf(repo.updates) }
    var diag by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    val note by Updater.note.collectAsState()
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notify = granted
        repo.notify = granted
        ChangeJob.schedule(ctx, granted || repo.alarm)
    }
    var alarm by remember { mutableStateOf(repo.alarm) }
    val askAlarm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        alarm = granted
        repo.alarm = granted
        ChangeJob.schedule(ctx, granted || repo.notify)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("EINSTELLUNGEN") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Section("Konto") {
                Text(p?.schoolName?.ifBlank { p.school } ?: "—", fontWeight = FontWeight.SemiBold)
                Text(
                    when {
                        p == null -> ""
                        p.demo -> "Demo-Schule mit erfundenen Daten"
                        p.anonymous -> "Ohne Anmeldung: nur öffentliche Pläne"
                        else -> "Angemeldet als ${p.user}"
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp,
                )
                OutlinedButton(shape = RectangleShape, onClick = { repo.logout(); ChangeJob.schedule(ctx, false); DayWidget.update(ctx); onLoggedOut() }) { Text("Abmelden und alles löschen") }
            }

            Section("Mein Plan") {
                val own = repo.own.value
                Text(own?.let { "${it.kind.label} ${it.longName.ifBlank { "" }}".trim() } ?: "Keiner gesetzt", fontSize = 15.sp)
                TextButton(onClick = onSetOwn) { Text("Klasse als eigenen Plan setzen") }
            }

            if (current != null) Section("Kurse ausblenden: ${current.name}") {
                // subjects that actually occur in the loaded weeks of this plan, so the list stays short
                val monday = Parse.monday(LocalDate.now())
                val ids = listOf(monday, monday.plusWeeks(1)).flatMap { (repo.weeks[repo.weekKey(current, it)]?.periods ?: repo.cachedPeriods(current, it)).orEmpty() }
                    .mapNotNull { it.subjectId }.toSet()
                val subjects = ids.mapNotNull { master?.find(Kind.SUBJECT, it) }.sortedBy { it.name }
                var hidden by remember(current.key) { mutableStateOf(repo.hidden(current)) }
                if (subjects.isEmpty()) Text("Öffne den Plan einmal, dann stehen hier seine Fächer.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                else Text("Abgehakt = sichtbar. Für die Oberstufe: Kurse, die du nicht hast, abwählen.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Column {
                    subjects.forEach { sub ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable {
                            hidden = if (sub.id in hidden) hidden - sub.id else hidden + sub.id
                            repo.setHidden(current, hidden)
                        }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(sub.id !in hidden, null, Modifier.padding(end = 12.dp))
                            Text(sub.title, fontSize = 15.sp)
                        }
                    }
                }
                if (subjects.isNotEmpty() && subjects.all { it.id in hidden }) Text("Alles ausgeblendet: der Plan bleibt leer.", color = LocalSignals.current.changed, fontSize = 13.sp)
            }

            Section("Benachrichtigungen") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Bei Ausfall, Vertretung, Raumänderung und Klausur im eigenen Plan melden", Modifier.weight(1f), fontSize = 15.sp)
                    Switch(notify, { on ->
                        if (on && Build.VERSION.SDK_INT >= 33) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else { notify = on; repo.notify = on; ChangeJob.schedule(ctx, on || repo.alarm) }
                    }, enabled = repo.own.value != null)
                }
                Text("Prüft einmal pro Stunde die nächsten 7 Tage, nur mit Netz.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }

            Section("Alarm") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Lauter Alarm bei Entfall, Foyer oder EVA im eigenen Plan", Modifier.weight(1f), fontSize = 15.sp)
                    Switch(alarm, { on ->
                        if (on && Build.VERSION.SDK_INT >= 33) askAlarm.launch(Manifest.permission.POST_NOTIFICATIONS)
                        else { alarm = on; repo.alarm = on; ChangeJob.schedule(ctx, on || repo.notify) }
                    }, enabled = repo.own.value != null)
                }
                Text("EVA heißt eigenverantwortliches Arbeiten. Klingelt wie ein Wecker, auch auf lautlos, bis du die Meldung antippst oder wegwischst. Prüft einmal pro Stunde, nur mit Netz.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                if (alarm) {
                    var from by remember { mutableStateOf(repo.alarmFrom) }
                    var to by remember { mutableStateOf(repo.alarmTo) }
                    var quiet by remember { mutableStateOf(repo.alarmQuiet) }
                    fun hm(m: Int) = "%02d:%02d".format(m / 60, m % 60)
                    fun pick(start: Int, set: (Int) -> Unit) =
                        android.app.TimePickerDialog(ctx, { _, h, m -> set(h * 60 + m); AlarmLater.fire(ctx) }, start / 60, start % 60, true).show()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Nur von", fontSize = 15.sp)
                        TextButton(onClick = { pick(from) { from = it; repo.alarmFrom = it } }) { Text(hm(from), fontSize = 16.sp) }
                        Text("bis", fontSize = 15.sp)
                        TextButton(onClick = { pick(to) { to = it; repo.alarmTo = it } }) { Text(hm(to), fontSize = 16.sp) }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Nicht während des Unterrichts", Modifier.weight(1f), fontSize = 15.sp)
                        Switch(quiet, { quiet = it; repo.alarmQuiet = it; AlarmLater.fire(ctx) })
                    }
                    Text("Was außerhalb davon kommt, klingelt, sobald es wieder darf (nach der letzten Stunde oder morgens), falls es dann noch gilt. Gleiche Uhrzeit bei von und bis: rund um die Uhr.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    TextButton(onClick = { ChangeJob.ring(ctx, listOf("Probealarm: so klingt es bei Entfall, Foyer oder EVA.")) }) { Text("Probealarm") }
                }
            }

            Section("Pausen-Info") {
                var breaks by remember { mutableStateOf(repo.breakInfo) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("In jeder Pause still die nächste Stunde mit Raum anzeigen", Modifier.weight(1f), fontSize = 15.sp)
                    Switch(breaks, { on ->
                        if (on && Build.VERSION.SDK_INT >= 33) ask.launch(Manifest.permission.POST_NOTIFICATIONS)
                        breaks = on; repo.breakInfo = on; BreakInfo.schedule(ctx)
                    }, enabled = repo.own.value != null)
                }
                Text("Nur aus dem Speicher, ohne Abruf. Die Meldung verschwindet zu Stundenbeginn.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }

            Section("Updates") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Beim Start auf GitHub nach neuen Versionen sehen", Modifier.weight(1f), fontSize = 15.sp)
                    Switch(updates, { updates = it; repo.updates = it })
                }
                TextButton(onClick = { scope.launch { Updater.check(ctx, manual = true) } }) { Text("Jetzt prüfen") }
                note?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
            }

            Section("Verbindungstest") {
                Text("Prüft Schritt für Schritt, was Untis für dein Konto liefert. Das Ergebnis enthält keinen Namen, kein Passwort und keinen Schlüssel, du kannst es in ein GitHub-Issue kopieren.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Button(shape = RectangleShape, onClick = { testing = true; scope.launch { diag = repo.connectionTest(); testing = false } }, enabled = !testing) {
                    Text(if (testing) "Prüfe …" else "Verbindung testen")
                }
                diag?.let { text ->
                    SelectionContainer { Text(text, fontFamily = FontFamily.Monospace, fontSize = 12.sp) }
                    TextButton(onClick = {
                        ctx.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Stundenblick Verbindungstest", text))
                    }) { Text("Kopieren") }
                }
            }

            Section("Über") {
                Text("Stundenblick ${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.SemiBold)
                Text("Nicht mit der Untis GmbH verbunden. Stundenblick zeigt alles, was deine Schule für dein Konto freigibt, aber nicht mehr. Daten gehen nur an den WebUntis-Server deiner Schule und, für Updates, an GitHub.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Text("Freie Software (GPL-3.0)", fontSize = 13.sp)
                TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL))) }) { Text("Quellcode und Fehler melden") }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RectangleShape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
        content()
    }
}
