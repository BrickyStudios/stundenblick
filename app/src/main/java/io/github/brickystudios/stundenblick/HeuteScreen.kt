package io.github.brickystudios.stundenblick

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val longDay = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)
private val shortDay = DateTimeFormatter.ofPattern("EEEE", Locale.GERMAN)
private val dm = DateTimeFormatter.ofPattern("d.M.", Locale.GERMAN)
private val hm = DateTimeFormatter.ofPattern("HH:mm")

/** H1: what matters today and next, on one screen. Untis Mobile spreads this over several tabs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeuteScreen(repo: Repo, onOpenPlan: () -> Unit, onHistory: () -> Unit, onSetOwn: () -> Unit) {
    val own = repo.own.value
    val master = repo.master.value
    val now by produceState(LocalDateTime.now()) { while (true) { delay(30_000); value = LocalDateTime.now() } }
    val today = now.toLocalDate()
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    val s = LocalSignals.current

    if (own == null) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Spacer(Modifier.height(24.dp))
            Text("Heute", fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
            Text("Für die Heute-Seite braucht Stundenblick deinen eigenen Plan.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = onSetOwn) { Text("Klasse als eigenen Plan setzen") }
        }
        return
    }

    val hidden = remember(repo.hiddenVersion.value, own.key) { repo.hidden(own) }
    fun week(monday: LocalDate) = repo.weeks[repo.weekKey(own, monday)]
    val thisMonday = Parse.monday(today)
    val saturday = week(thisMonday)?.periods?.any { it.start.dayOfWeek == DayOfWeek.SATURDAY } == true
    val nextDay = Derive.nextSchoolDay(today, master, saturday)
    LaunchedEffect(own.key, today) {
        repo.load(own, thisMonday)
        if (Parse.monday(nextDay) != thisMonday) repo.load(own, Parse.monday(nextDay))
    }
    fun day(d: LocalDate) = week(Parse.monday(d))?.periods?.let { ps -> Derive.blocks(Filter.apply(ps, hidden).filter { it.start.toLocalDate() == d }) }
    val todays = day(today).orEmpty()
    val state = Derive.now(todays, now)
    val schoolOver = todays.none { it.end.isAfter(now) }
    val weekCount = week(thisMonday)?.periods?.let { Derive.weekCount(Derive.blocks(Filter.apply(it, hidden))) }
    val recent = repo.history.value.filter { System.currentTimeMillis() - it.at < 24 * 3600_000L }.asReversed()
    val holiday = master?.let { Derive.nextHoliday(today, it) }
    val error = week(thisMonday)?.error

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            scope.launch {
                refreshing = true
                repo.load(own, thisMonday, force = true)
                if (Parse.monday(nextDay) != thisMonday) repo.load(own, Parse.monday(nextDay), force = true)
                refreshing = false
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).safeDrawingPadding().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(longDay.format(today), fontSize = 28.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
            if (error != null) Text(error, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            when {
                holiday?.running == true -> Card { Text("Ferien bis ${dm.format(holiday.end)}", fontSize = 20.sp, fontWeight = FontWeight.SemiBold); Text(holiday.name, color = s.muted) }
                todays.isEmpty() || schoolOver -> {}
                state.current.isNotEmpty() -> Card {
                    Label("Jetzt" + (state.minutesLeft(now)?.let { ", noch $it min" } ?: ""))
                    state.current.forEach { LessonLine(it, master, big = true) }
                }
                else -> Card { Label("Pause bis ${state.next.firstOrNull()?.start?.let(hm::format) ?: ""}") }
            }
            if (!schoolOver && state.next.isNotEmpty() && holiday?.running != true) Card {
                Label("Als Nächstes um ${hm.format(state.next.first().start)}")
                state.next.forEach { LessonLine(it, master, big = state.current.isEmpty()) }
            }

            if (recent.isNotEmpty()) Card(Modifier.clickable(onClick = onHistory)) {
                Label("Neu seit gestern")
                recent.take(4).forEach { Text(it.text, fontSize = 15.sp) }
                if (recent.size > 4) Text("und ${recent.size - 4} weitere", color = s.muted, fontSize = 13.sp)
            }

            if (schoolOver || todays.isEmpty()) {
                val next = day(nextDay)
                Card(Modifier.clickable(onClick = onOpenPlan)) {
                    val name = if (ChronoUnit.DAYS.between(today, nextDay) == 1L) "Morgen" else shortDay.format(nextDay).replaceFirstChar { it.uppercase() }
                    Label("$name, ${dm.format(nextDay)}")
                    when {
                        next == null -> Text("Noch nicht geladen. Zieh nach unten.", color = s.muted)
                        next.isEmpty() -> Text("Keine Stunden.", color = s.muted)
                        else -> {
                            val first = next.filter { !it.cancelled }.minByOrNull { it.start }
                            if (first != null) Text("Beginn ${hm.format(first.start)}, Schluss ${hm.format(next.filter { !it.cancelled }.maxOf { it.end })}", color = s.muted, fontSize = 14.sp)
                            next.forEach { LessonLine(it, master, big = false) }
                        }
                    }
                }
            }

            if (weekCount?.any == true) Card(Modifier.clickable(onClick = onOpenPlan)) {
                Label("Diese Woche")
                WeekLine(weekCount)
            }

            if (holiday != null && !holiday.running) {
                val days = ChronoUnit.DAYS.between(today, holiday.start)
                Card { Label(holiday.name); Text(if (days == 1L) "Ab morgen frei" else "Noch $days Tage", fontSize = 20.sp, fontWeight = FontWeight.SemiBold) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Card(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) { content() }
}

@Composable
private fun Label(text: String) = Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)

/** One lesson: number, subject, room and teacher, changes in their colour. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LessonLine(p: Period, master: Master?, big: Boolean) {
    val s = LocalSignals.current
    val subject = p.of(Kind.SUBJECT).firstOrNull()?.let { r -> master?.find(Kind.SUBJECT, r.id)?.let { it.longName.ifBlank { it.name } } } ?: p.lessonText.ifBlank { "Stunde" }
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.width(76.dp)) {
            Text(master?.let { Derive.unitLabel(p, it) } ?: hm.format(p.start), fontSize = 13.sp, color = s.muted, modifier = Modifier.padding(top = if (big) 4.dp else 1.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(subject, fontSize = if (big) 20.sp else 16.sp, fontWeight = FontWeight.SemiBold,
                textDecoration = if (p.cancelled) TextDecoration.LineThrough else null, color = if (p.cancelled) s.muted else MaterialTheme.colorScheme.onSurface)
            val bits = buildList {
                p.of(Kind.ROOM).forEach { r -> add((master?.find(Kind.ROOM, r.id)?.name ?: "?") to r.changed) }
                p.of(Kind.TEACHER).forEach { r -> add((if (r.id == 0L) "offen" else master?.find(Kind.TEACHER, r.id)?.let { it.longName.ifBlank { it.name } } ?: "?") to r.changed) }
            }
            if (bits.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                bits.forEach { (t, changed) -> Text(t, fontSize = 14.sp, color = if (changed) s.changed else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (changed) FontWeight.SemiBold else null) }
            }
            val tags = tagsFor(p)
            if (tags.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 2.dp)) { tags.forEach { (t, c) -> Tag(t, c) } }
        }
    }
}

/** E14: the week in four numbers; only the ones that are not zero. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeekLine(c: Derive.WeekCount) {
    val s = LocalSignals.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(
            c.cancelled to ("Entfall" to s.cancelled),
            c.substituted to ((if (c.substituted == 1) "Vertretung" else "Vertretungen") to s.changed),
            c.roomChanged to ((if (c.roomChanged == 1) "Raumänderung" else "Raumänderungen") to s.changed),
            c.exams to ((if (c.exams == 1) "Klausur" else "Klausuren") to s.exam),
        ).filter { it.first > 0 }.forEach { (n, l) -> Tag("$n ${l.first}", l.second) }
    }
}

