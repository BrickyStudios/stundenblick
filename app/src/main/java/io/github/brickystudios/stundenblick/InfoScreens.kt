package io.github.brickystudios.stundenblick

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val day = DateTimeFormatter.ofPattern("EE d.M.", Locale.GERMAN)
private val hm = DateTimeFormatter.ofPattern("HH:mm")

fun daysUntil(d: LocalDate, today: LocalDate = LocalDate.now()): String = when (val n = ChronoUnit.DAYS.between(today, d)) {
    0L -> "heute"; 1L -> "morgen"; else -> if (n < 0) "vor ${-n} Tagen" else "in $n Tagen"
}

/** One info list: title, pull to refresh, loading, error and empty states (R4), then the rows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> InfoPage(repo: Repo, kind: Repo.Info, title: String, empty: String, onBack: () -> Unit, parse: (org.json.JSONObject) -> List<T>, header: @Composable ((List<T>) -> Unit)? = null, row: @Composable (T) -> Unit) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(kind) { repo.loadInfo(kind) }
    val st = repo.infoOf(kind)
    val items = st.data?.let { runCatching { parse(it) }.getOrNull() }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        PullToRefreshBox(st.loading && items != null, { scope.launch { repo.loadInfo(kind, force = true) } }, Modifier.padding(pad).fillMaxSize()) {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
                st.error?.let { e -> item { Text(e, Modifier.padding(16.dp), fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                when {
                    st.noRight -> item { Text("Deine Schule gibt das für dein Konto nicht frei.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    items == null -> item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { if (st.error == null) CircularProgressIndicator() } }
                    items.isEmpty() -> item { Text(empty, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    else -> {
                        header?.let { h -> item { h(items) } }
                        items(items) { row(it); HorizontalDivider(color = LocalSignals.current.line) }
                    }
                }
            }
        }
    }
}

@Composable
fun ExamsScreen(repo: Repo, onBack: () -> Unit) {
    val m = repo.master.value
    val hidden = repo.own.value?.let { repo.hidden(it) }.orEmpty()
    InfoPage(repo, Repo.Info.EXAMS, "Klausuren", "Bis zum Schuljahresende ist keine Klausur eingetragen.", onBack,
        { InfoParse.exams(it).filter { e -> e.subjectId == null || e.subjectId !in hidden } }) { e ->
        val subject = e.subjectId?.let { m?.find(Kind.SUBJECT, it) }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SubjectBadge(e.subjectId, m)
                Text(subject?.let { it.longName.ifBlank { it.name } } ?: e.name.ifBlank { "Klausur" }, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Tag(daysUntil(e.start.toLocalDate()), LocalSignals.current.exam)
            }
            Text("${day.format(e.start)}, ${hm.format(e.start)}–${hm.format(e.end)}" + e.roomIds.joinToString { m?.find(Kind.ROOM, it)?.name ?: "" }.let { if (it.isBlank()) "" else ", Raum $it" },
                fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(e.type, e.name.takeIf { it != subject?.longName }, e.text).filter { !it.isNullOrBlank() }.distinct().joinToString(", ").takeIf { it.isNotBlank() }
                ?.let { Text(it, fontSize = 13.sp, color = LocalSignals.current.muted) }
        }
    }
}

@Composable
fun HomeworkScreen(repo: Repo, onBack: () -> Unit) {
    val m = repo.master.value
    val hidden = repo.own.value?.let { repo.hidden(it) }.orEmpty()
    val done = repo.homeworkDone.value
    InfoPage(repo, Repo.Info.HOMEWORK, "Hausaufgaben", "Keine Hausaufgaben eingetragen", onBack,
        { InfoParse.homework(it).filter { h -> h.subjectId == null || h.subjectId !in hidden } }) { h ->
        val finished = h.completed || h.id in done
        Row(Modifier.fillMaxWidth().clickable { repo.toggleDone(h.id) }.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
            Checkbox(finished, { repo.toggleDone(h.id) })
            Column(Modifier.padding(top = 10.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SubjectBadge(h.subjectId, m)
                    Text(h.subjectId?.let { m?.find(Kind.SUBJECT, it)?.let { s -> s.longName.ifBlank { s.name } } } ?: "Hausaufgabe", fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    h.due?.let { Tag("bis " + daysUntil(it), if (finished) LocalSignals.current.muted else MaterialTheme.colorScheme.onSurface) }
                }
                Text(h.text, fontSize = 15.sp, textDecoration = if (finished) TextDecoration.LineThrough else null,
                    color = if (finished) LocalSignals.current.muted else MaterialTheme.colorScheme.onSurface)
                if (h.remark.isNotBlank()) Text(h.remark, fontSize = 13.sp, color = LocalSignals.current.muted)
                h.due?.let { Text("fällig ${day.format(it)}" + (h.given?.let { g -> ", aufgegeben ${day.format(g)}" } ?: ""), fontSize = 12.sp, color = LocalSignals.current.muted) }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AbsencesScreen(repo: Repo, onBack: () -> Unit) {
    val s = LocalSignals.current
    InfoPage(repo, Repo.Info.ABSENCES, "Fehlzeiten", "In diesem Schuljahr sind keine Fehlzeiten eingetragen.", onBack, InfoParse::absences,
        header = { list ->
            val open = list.count { !it.excused }
            FlowRow(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tag("${list.size} eingetragen", MaterialTheme.colorScheme.onSurface)
                if (open > 0) Tag("$open nicht entschuldigt", s.cancelled) else Tag("alle entschuldigt", s.muted)
            }
        }) { a ->
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(day.format(a.start) + ", " + hm.format(a.start) + "–" + hm.format(a.end) + if (a.end.toLocalDate() != a.start.toLocalDate()) " (bis ${day.format(a.end)})" else "",
                    fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Tag(if (a.excused) "entschuldigt" else "offen", if (a.excused) s.muted else s.cancelled)
            }
            listOf(a.reason, a.text).filter(String::isNotBlank).joinToString(", ").takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 13.sp, color = s.muted) }
        }
    }
}

@Composable
fun OfficeHoursScreen(repo: Repo, onBack: () -> Unit) {
    val ctx = LocalContext.current
    InfoPage(repo, Repo.Info.OFFICE, "Sprechstunden", "In den nächsten Tagen sind keine Sprechstunden eingetragen.", onBack, InfoParse::officeHours) { o ->
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(o.teacher, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text("${day.format(o.start)}, ${hm.format(o.start)}–${hm.format(o.end)}" + if (o.rooms.isNotBlank()) ", ${o.rooms}" else "", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row {
                o.email?.let { m -> TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$m"))) }) { Text("E-Mail") } }
                o.phone?.let { p -> TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$p"))) }) { Text("Anrufen") } }
            }
        }
    }
}
