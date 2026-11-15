package io.github.brickystudios.stundenblick

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val hm = DateTimeFormatter.ofPattern("HH:mm")
private val stamp = DateTimeFormatter.ofPattern("EE d.M., HH:mm", Locale.GERMAN)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Page(title: String, onBack: (() -> Unit)?, actions: @Composable () -> Unit = {}, content: @Composable (PaddingValues) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title.uppercase()) },
                navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
                actions = { actions() },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        content = content,
    )
}

@Composable
private fun Entry(title: String, line: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp)) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        Text(line, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider(color = LocalSignals.current.line)
}

/** Everything that is not today or the plan. */
@Composable
fun MehrScreen(repo: Repo, onHistory: () -> Unit, onRooms: () -> Unit, onSettings: () -> Unit, onInfo: (Repo.Info) -> Unit) {
    LaunchedEffect(Unit) { listOf(Repo.Info.EXAMS, Repo.Info.HOMEWORK, Repo.Info.ABSENCES).forEach { repo.loadInfo(it) } }
    Page("Mehr", null) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState())) {
            // Info Center like BetterUntis: only what the school hands to this account
            fun count(k: Repo.Info, parse: (org.json.JSONObject) -> List<*>) = repo.infoOf(k).data?.let { runCatching { parse(it).size }.getOrNull() }
            if (repo.offers(Repo.Info.EXAMS)) Entry("Klausuren", count(Repo.Info.EXAMS) { InfoParse.exams(it) }?.let { if (it == 0) "keine eingetragen" else "$it bis zum Schuljahresende" } ?: "laden") { onInfo(Repo.Info.EXAMS) }
            if (repo.offers(Repo.Info.HOMEWORK)) Entry("Hausaufgaben", count(Repo.Info.HOMEWORK) { InfoParse.homework(it) }?.let { if (it == 0) "keine eingetragen" else "$it eingetragen" } ?: "laden") { onInfo(Repo.Info.HOMEWORK) }
            if (repo.offers(Repo.Info.ABSENCES)) Entry("Fehlzeiten", count(Repo.Info.ABSENCES) { InfoParse.absences(it) }?.let { "$it in diesem Schuljahr" } ?: "laden") { onInfo(Repo.Info.ABSENCES) }
            if (repo.offers(Repo.Info.OFFICE)) Entry("Sprechstunden", "der Lehrkräfte deiner Klasse") { onInfo(Repo.Info.OFFICE) }
            val unseen = repo.history.value.count { !it.seen }
            Entry("Änderungsverlauf", if (unseen > 0) "$unseen neu" else "${repo.history.value.size} Einträge der letzten 60 Tage", onHistory)
            Entry("Raumfinder", if (repo.rooms.value.isEmpty()) "Räume wählen und sehen, wann sie frei sind" else "${repo.rooms.value.size} Räume", onRooms)
            Entry("Einstellungen", "Konto, Kurse ausblenden, Meldungen, Verbindungstest", onSettings)
        }
    }
}

/** E11: every change Stundenblick noticed in the own plan, newest first. */
@Composable
fun VerlaufScreen(repo: Repo, onBack: () -> Unit) {
    val entries = repo.history.value.asReversed()
    val s = LocalSignals.current
    Page("Änderungsverlauf", onBack) { pad ->
        if (entries.isEmpty()) Box(Modifier.padding(pad).fillMaxSize().padding(24.dp)) {
            Text("Noch keine Änderungen bemerkt. Stundenblick vergleicht deinen Plan bei jedem Laden und, mit Meldungen, jede Stunde.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(entries, key = { "${it.id}${it.at}${it.text}" }) { e ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.Top) {
                    Box(Modifier.padding(top = 7.dp).size(8.dp).background(if (e.seen) MaterialTheme.colorScheme.outlineVariant else MaterialTheme.colorScheme.primary, RectangleShape)
                        .semantics { contentDescription = if (e.seen) "" else "neu" })
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(e.text, fontSize = 15.sp)
                        Text("bemerkt " + stamp.format(java.time.Instant.ofEpochMilli(e.at).atZone(java.time.ZoneId.systemDefault())), fontSize = 12.sp, color = s.muted)
                    }
                }
                HorizontalDivider(color = s.line)
            }
        }
    }
}

/** J2, like BetterUntis' RoomFinder: rooms the user picked, free or busy now, from week plans loaded at most weekly. */
@Composable
fun RoomFinderScreen(repo: Repo, onBack: () -> Unit) {
    val master = repo.master.value
    val now by produceState(LocalDateTime.now()) { while (true) { delay(30_000); value = LocalDateTime.now() } }
    var picking by remember { mutableStateOf(false) }
    val chosen = repo.rooms.value.mapNotNull { master?.find(Kind.ROOM, it) }.sortedBy { it.name }
    val monday = Parse.monday(now.toLocalDate())
    LaunchedEffect(chosen.map { it.id }, monday) { chosen.forEach { repo.load(it, monday, maxAgeMs = 7 * 24 * 3600_000L) } }
    val s = LocalSignals.current
    Page("Raumfinder", onBack, actions = { TextButton(onClick = { picking = true }) { Text("Räume wählen") } }) { pad ->
        if (chosen.isEmpty()) Column(Modifier.padding(pad).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Wähl die Räume, die dich interessieren, zum Beispiel Lernräume oder Fachräume. Stundenblick lädt jeden höchstens einmal pro Woche.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { picking = true }) { Text("Räume wählen") }
        } else LazyColumn(Modifier.padding(pad), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(chosen, key = { it.id }) { room ->
                val w = repo.weeks[repo.weekKey(room, monday)]
                val st = w?.periods?.let { master?.let { m -> Derive.roomState(it, now, m) } }
                val (line, free) = when {
                    w == null || (w.loading && w.periods == null) -> "lädt …" to null
                    w.periods == null -> (w.error ?: "Plan nicht verfügbar") to null
                    st?.busy != null -> ("belegt bis ${hm.format(st.busy.end)}" + (st.nextFree?.let { ", frei ab ${hm.format(it)}" } ?: ", heute nicht mehr frei")) to false
                    st?.freeUntil != null -> "frei bis ${hm.format(st.freeUntil)}" to true
                    else -> "heute frei" to true
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).background(when (free) { true -> MaterialTheme.colorScheme.primary; false -> s.cancelled; null -> MaterialTheme.colorScheme.outlineVariant }, RectangleShape))
                    Column(Modifier.padding(start = 14.dp).weight(1f)) {
                        Text(room.name, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                        Text(line, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider(color = s.line)
            }
        }
    }
    if (picking && master != null) RoomPicker(master, repo.rooms.value, onDone = { repo.setRooms(it); picking = false })
}

@Composable
private fun RoomPicker(master: Master, initial: Set<Long>, onDone: (Set<Long>) -> Unit) {
    var sel by remember { mutableStateOf(initial) }
    var q by remember { mutableStateOf("") }
    val rooms = master.elements[Kind.ROOM].orEmpty().filter { q.isBlank() || it.name.contains(q.trim(), true) || it.longName.contains(q.trim(), true) }
    AlertDialog(
        onDismissRequest = { onDone(sel) },
        title = { Text("Räume wählen") },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), placeholder = { Text("Suchen") }, singleLine = true)
                LazyColumn(Modifier.padding(top = 8.dp)) {
                    items(rooms.take(300), key = { it.id }) { r ->
                        Row(Modifier.fillMaxWidth().clickable { sel = if (r.id in sel) sel - r.id else sel + r.id }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(r.id in sel, null)
                            Text(r.title, fontSize = 15.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onDone(sel) }) { Text("Fertig (${sel.size})") } },
    )
}

