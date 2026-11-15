package io.github.brickystudios.stundenblick

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val hm = DateTimeFormatter.ofPattern("HH:mm")
private val dayName = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)

/**
 * The whole school at one lesson: free rooms and who is where, built from every class plan of the week.
 * Nothing loads until asked once; after that, opening the tab reloads by itself (cache 6 hours).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchoolScreen(repo: Repo, onOpen: (Element) -> Unit, onRooms: () -> Unit) {
    val master = repo.master.value ?: return
    val scope = rememberCoroutineScope()
    val now = remember { LocalDateTime.now() }
    val today = now.toLocalDate()
    // after the last lesson the next school day is the interesting one
    val lastEnd = master.units.lastOrNull()?.end
    val day = if (today.dayOfWeek >= DayOfWeek.SATURDAY || (lastEnd != null && now.toLocalTime() > lastEnd) || master.holiday(today) != null)
        Derive.nextSchoolDay(today, master, false) else today
    val monday = Parse.monday(day)
    var unit by rememberSaveable { mutableIntStateOf(if (day == today) Derive.slotIndex(master.units, now.toLocalTime()) else 0) }
    var mode by rememberSaveable { mutableStateOf("rooms") }
    var query by remember { mutableStateOf("") }
    val st = repo.school.value
    val classes = master.elements[Kind.CLASS].orEmpty().size
    LaunchedEffect(monday) { if (repo.schoolOptedIn()) repo.loadSchool(monday) }
    // only complete loads are shown: halfway through, rooms of classes not yet loaded would look free
    val last = remember { arrayOfNulls<List<Period>>(1) }
    val all = remember(monday, st.running, st.done) { when {
        st.running -> last[0]
        !repo.schoolOptedIn() -> null // the own class alone is in memory from the Heute tab: not the school
        else -> repo.schoolPeriods(monday, disk = false).also { last[0] = it }
    } }
    val loaded = remember(monday, st.running, st.done) { repo.schoolLoaded(monday) }
    val s = LocalSignals.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("SCHULE")
                        Text(dayName.format(day), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = { if (all != null && !st.running) TextButton(onClick = { scope.launch { repo.loadSchool(monday, force = true) } }) { Text("Aktualisieren") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (st.running) Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Lade Klassenpläne ${st.done} von ${st.total} …", fontSize = 13.sp, color = s.muted)
                LinearProgressIndicator({ if (st.total == 0) 0f else st.done / st.total.toFloat() }, Modifier.fillMaxWidth().padding(vertical = 6.dp))
                TextButton(onClick = repo::cancelSchool) { Text("Abbrechen") }
            }
            when {
                st.refused -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Deine Schule gibt die Pläne anderer Klassen für dein Konto nicht frei. Ohne sie kann Stundenblick nicht sehen, welche Räume frei sind.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(shape = RectangleShape, onClick = onRooms) { Text("Raumfinder mit einzelnen Räumen") }
                }
                all == null -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Freie Räume und wer wo Unterricht hat, für die ganze Schule.", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    Text("Dafür lädt Stundenblick die Wochenpläne aller $classes Klassen, eine Anfrage pro Klasse. Danach bleiben sie 6 Stunden gespeichert, auch offline.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!st.running) Button(shape = RectangleShape, onClick = { scope.launch { repo.loadSchool(monday) } }) { Text("Klassenpläne laden") }
                }
                else -> {
                    if (!st.running && loaded < classes) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Nur $loaded von $classes Klassen geladen, freie Räume können falsch sein.", Modifier.weight(1f), fontSize = 13.sp, color = s.changed)
                        TextButton(onClick = { scope.launch { repo.loadSchool(monday) } }) { Text("Weiterladen") }
                    }
                    val units = master.units
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        units.forEachIndexed { i, u ->
                            FilterChip(unit == i, { unit = i }, { Text("${i + 1}. ${hm.format(u.start)}") })
                        }
                    }
                    val slot = units.getOrNull(unit) ?: return@Column
                    val rooms = remember(all) { Derive.teachingRooms(all, master) }
                    val free = remember(all, unit, day) { Derive.freeRooms(all, rooms, day, slot, units) }
                    val lessons = remember(all, unit, day) { Derive.lessonsAt(all, day, slot) }
                    Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(mode == "rooms", { mode = "rooms" }, { Text("Freie Räume ${free.size}/${rooms.size}") })
                        FilterChip(mode == "who", { mode = "who" }, { Text("Wer ist wo ${lessons.count { !it.cancelled }}") })
                    }
                    OutlinedTextField(
                        query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), singleLine = true,
                        placeholder = { Text(if (mode == "rooms") "Raum suchen" else "Klasse, Kurs, Lehrkraft oder Raum") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    )
                    val q = query.trim()
                    if (mode == "rooms") {
                        val shown = free.filter { q.isEmpty() || it.room.name.contains(q, true) || it.room.longName.contains(q, true) }
                        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                            if (shown.isEmpty()) item { Text("Kein freier Raum.", Modifier.padding(24.dp), color = s.muted) }
                            items(shown, key = { it.room.id }) { f -> FreeRoomRow(f) { onOpen(f.room) } }
                        }
                    } else {
                        fun names(p: Period, k: Kind) = p.of(k).map { r -> master.find(k, r.id) }
                        val shown = lessons.filter { p ->
                            q.isEmpty() || listOf(Kind.CLASS, Kind.SUBJECT, Kind.TEACHER, Kind.ROOM).any { k -> names(p, k).any { it != null && (it.name.contains(q, true) || it.longName.contains(q, true)) } } ||
                                p.lessonText.contains(q, true)
                        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { p -> names(p, Kind.CLASS).firstOrNull()?.name ?: "" })
                        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                            if (shown.isEmpty()) item { Text("Keine Stunde gefunden.", Modifier.padding(24.dp), color = s.muted) }
                            items(shown, key = { it.id }) { p -> WhoRow(p, master, onOpen) }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FreeRoomRow(f: Derive.FreeRoom, onClick: () -> Unit) {
    val s = LocalSignals.current
    Row(Modifier.fillMaxWidth().clickable(onClickLabel = "Raumplan öffnen", onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(f.room.name, fontSize = 17.sp, fontWeight = FontWeight.Medium)
            if (f.room.longName.isNotBlank() && f.room.longName != f.room.name) Text(f.room.longName, fontSize = 13.sp, color = s.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(f.until?.let { "frei bis ${hm.format(it)}" } ?: "frei bis Schulschluss", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            f.freedBy?.let { FlowRow { Tag(it, s.changed) } }
        }
    }
    HorizontalDivider(color = s.line)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WhoRow(p: Period, master: Master, onOpen: (Element) -> Unit) {
    val s = LocalSignals.current
    val klass = p.of(Kind.CLASS).mapNotNull { master.find(Kind.CLASS, it.id) }
    val subject = p.of(Kind.SUBJECT).firstOrNull()?.let { master.find(Kind.SUBJECT, it.id) }
    Row(Modifier.fillMaxWidth().clickable(onClickLabel = "Klassenplan öffnen") { klass.firstOrNull()?.let(onOpen) }.padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
        Text(klass.joinToString(", ") { it.name }.ifBlank { "—" }, Modifier.width(76.dp), fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Column(Modifier.weight(1f)) {
            Text((subject?.let { it.longName.ifBlank { it.name } } ?: p.lessonText.ifBlank { "Stunde" }) + if (p.lessonText.isNotBlank() && subject != null && p.lessonText != subject.name) " (${p.lessonText})" else "",
                fontSize = 15.sp, textDecoration = if (p.cancelled) TextDecoration.LineThrough else null, color = if (p.cancelled) s.muted else MaterialTheme.colorScheme.onSurface)
            val bits = p.of(Kind.TEACHER).map { r -> (if (r.id == 0L) "offen" else master.find(Kind.TEACHER, r.id)?.name ?: "?") to r.changed } +
                p.of(Kind.ROOM).map { r -> (master.find(Kind.ROOM, r.id)?.name ?: "?") to r.changed }
            if (bits.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                bits.forEach { (t, changed) -> Text(t, fontSize = 13.sp, color = if (changed) s.changed else s.muted, fontWeight = if (changed) FontWeight.SemiBold else null) }
            }
            val tags = tagsFor(p)
            if (tags.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { tags.forEach { (t, c) -> Tag(t, c) } }
        }
    }
    HorizontalDivider(color = s.line)
}
