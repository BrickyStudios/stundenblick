package io.github.brickystudios.stundenblick

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.IsoFields
import java.util.Locale

private val longDay = DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)
private val shortDay = DateTimeFormatter.ofPattern("EEEE", Locale.GERMAN)
private val dm = DateTimeFormatter.ofPattern("d.M.", Locale.GERMAN)
private val hm = DateTimeFormatter.ofPattern("HH:mm")

/** H1: what matters today and next, on one screen. Untis Mobile spreads this over several tabs. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HeuteScreen(repo: Repo, onOpenPlan: () -> Unit, onHistory: () -> Unit, onSetOwn: () -> Unit, onInfo: (Repo.Info) -> Unit) {
    val own = repo.own.value
    val master = repo.master.value
    val now by produceState(LocalDateTime.now()) { while (true) { delay(30_000); value = LocalDateTime.now() } }
    val today = now.toLocalDate()
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    val s = LocalSignals.current

    if (own == null) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Spacer(Modifier.height(24.dp))
            PageTitle("Heute")
            Text("Für die Heute-Seite braucht Stundenblick deinen eigenen Plan.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(shape = ControlShape, onClick = onSetOwn) { Text("Klasse als eigenen Plan setzen") }
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
        listOf(Repo.Info.MESSAGES, Repo.Info.EXAMS, Repo.Info.HOMEWORK).forEach { repo.loadInfo(it) }
    }
    // wave B cards: the school's message, the next exam, homework due by the next school day
    val messages = repo.infoOf(Repo.Info.MESSAGES).data?.let { runCatching { InfoParse.messages(it) }.getOrNull() }.orEmpty()
    val nextExam = repo.infoOf(Repo.Info.EXAMS).data?.let { runCatching { InfoParse.exams(it) }.getOrNull() }.orEmpty()
        .filter { !it.start.toLocalDate().isBefore(today) && (it.subjectId == null || it.subjectId !in hidden) }.minByOrNull { it.start }
    val dueSoon = repo.infoOf(Repo.Info.HOMEWORK).data?.let { runCatching { InfoParse.homework(it) }.getOrNull() }.orEmpty()
        .filter { h -> !h.completed && h.id !in repo.homeworkDone.value && h.due != null && !h.due.isBefore(today) && !h.due.isAfter(nextDay) && (h.subjectId == null || h.subjectId !in hidden) }
    fun day(d: LocalDate) = week(Parse.monday(d))?.periods?.let { ps -> Derive.blocks(Filter.apply(ps, hidden).filter { it.start.toLocalDate() == d }) }
    val todays = day(today).orEmpty()
    val state = Derive.now(todays, now)
    val schoolOver = todays.none { it.end.isAfter(now) }
    val later = todays.filter { it.start.isAfter(now) }.sortedBy { it.start }
    val weekCount = week(thisMonday)?.periods?.let { Derive.weekCount(Derive.blocks(Filter.apply(it, hidden))) }
    val recent = repo.history.value.filter { System.currentTimeMillis() - it.at < 24 * 3600_000L }.asReversed()
    val holiday = master?.let { Derive.nextHoliday(today, it) }
    val error = week(thisMonday)?.error
    val running = state.current.firstOrNull()?.let { p -> master?.let { Derive.unitLabel(p, it) } }?.replace("Std.", "Stunde")

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            scope.launch {
                refreshing = true
                repo.load(own, thisMonday, force = true)
                if (Parse.monday(nextDay) != thisMonday) repo.load(own, Parse.monday(nextDay), force = true)
                listOf(Repo.Info.MESSAGES, Repo.Info.EXAMS, Repo.Info.HOMEWORK).forEach { repo.loadInfo(it, force = true) }
                refreshing = false
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            // insets outside the scroll: the cards must not slide under the status bar icons
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.padding(top = 8.dp, bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                PageTitle(longDay.format(today))
                Text("KW ${today.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)}" + (running?.let { " · $it läuft" } ?: ""), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (error != null) Text(error, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

            when {
                holiday?.running == true -> Panel {
                    Text("Ferien bis ${dm.format(holiday.end)}", fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    Text(holiday.name, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                todays.isEmpty() || schoolOver -> {}
                state.current.isNotEmpty() -> {
                    val start = state.current.minOf { it.start }
                    val end = state.current.maxOf { it.end }
                    NowCard("Jetzt · bis ${hm.format(end)}", state.minutesLeft(now) ?: 0, Duration.between(start, now).seconds / Duration.between(start, end).seconds.coerceAtLeast(1).toFloat()) {
                        state.current.forEach { LessonHead(it, master) }
                    }
                }
                state.next.isNotEmpty() -> {
                    val next = state.next.first().start
                    val pause = todays.any { !it.end.isAfter(now) }
                    NowCard((if (pause) "Pause · bis " else "Erste Stunde · um ") + hm.format(next), Duration.between(now, next).toMinutes(), null) {
                        Text(if (pause) "Pause" else "Schulbeginn", fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            if (!schoolOver && later.isNotEmpty() && holiday?.running != true) Panel {
                Label("Heute noch")
                Departures(later, master)
            }

            messages.forEach { msg ->
                Panel {
                    Label("Nachricht der Schule")
                    Text(msg.subject, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    if (msg.text.isNotBlank()) Text(msg.text, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (msg.attachments > 0) Text("${msg.attachments} " + (if (msg.attachments == 1) "Anhang" else "Anhänge") + " in WebUntis", fontSize = 13.sp, color = s.muted)
                }
            }

            if (dueSoon.isNotEmpty()) Panel(Modifier.clickable { onInfo(Repo.Info.HOMEWORK) }) {
                Label("Hausaufgaben bis " + if (ChronoUnit.DAYS.between(today, nextDay) == 1L) "morgen" else dm.format(nextDay))
                dueSoon.forEach { h ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SubjectBadge(h.subjectId, master)
                        Text(h.text)
                    }
                }
            }

            if (nextExam != null && ChronoUnit.DAYS.between(today, nextExam.start.toLocalDate()) <= 21) Panel(Modifier.clickable { onInfo(Repo.Info.EXAMS) }) {
                Label("Nächste Klausur " + daysUntil(nextExam.start.toLocalDate(), today))
                val subject = nextExam.subjectId?.let { master?.find(Kind.SUBJECT, it) }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SubjectBadge(nextExam.subjectId, master)
                    Text(subject?.let { it.longName.ifBlank { it.name } } ?: nextExam.name.ifBlank { "Klausur" }, fontSize = 20.sp, fontWeight = FontWeight.Medium)
                }
                Text(shortDay.format(nextExam.start).replaceFirstChar { it.uppercase() } + ", " + dm.format(nextExam.start) + ", " + hm.format(nextExam.start),
                    color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
            }

            if (recent.isNotEmpty()) Panel(Modifier.clickable(onClick = onHistory)) {
                Label("Neu seit gestern")
                recent.take(4).forEach { Text(it.text) }
                if (recent.size > 4) Text("und ${recent.size - 4} weitere", color = s.muted, fontSize = 13.sp)
            }

            if (schoolOver || todays.isEmpty()) {
                val next = day(nextDay)
                Panel(Modifier.clickable(onClick = onOpenPlan)) {
                    val name = if (ChronoUnit.DAYS.between(today, nextDay) == 1L) "Morgen" else shortDay.format(nextDay).replaceFirstChar { it.uppercase() }
                    Label("$name, ${dm.format(nextDay)}")
                    when {
                        next == null -> Text("Noch nicht geladen. Zieh nach unten.", color = s.muted)
                        next.isEmpty() -> Text("Keine Stunden", color = s.muted)
                        else -> {
                            val first = next.filter { !it.cancelled }.minByOrNull { it.start }
                            if (first != null) Text("Beginn ${hm.format(first.start)}, Schluss ${hm.format(next.filter { !it.cancelled }.maxOf { it.end })}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp)
                            Departures(next.sortedBy { it.start }, master)
                        }
                    }
                }
            }

            if (weekCount?.any == true) Panel(Modifier.clickable(onClick = onOpenPlan)) {
                Label("Diese Woche")
                WeekLine(weekCount)
            }

            if (holiday != null && !holiday.running) {
                val days = ChronoUnit.DAYS.between(today, holiday.start)
                Panel { Label(holiday.name); Text(if (days == 1L) "Ab morgen frei" else "Noch $days Tage", fontSize = 20.sp, fontWeight = FontWeight.Medium) }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Moment 1: what runs now on the left, the minutes left in large mono on the right, the hour as a yellow bar. */
@Composable
private fun NowCard(label: String, minutes: Long, progress: Float?, left: @Composable () -> Unit) {
    val s = LocalSignals.current
    Panel {
        Label(label)
        Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) { left() }
            Column(Modifier.padding(start = 12.dp), horizontalAlignment = Alignment.End) {
                Text("$minutes", fontFamily = Mono, fontWeight = FontWeight.Medium, fontSize = 44.sp, lineHeight = 44.sp, letterSpacing = (-0.02).em)
                Text("min", fontSize = 12.sp, lineHeight = 16.sp, color = s.muted)
            }
        }
        if (progress != null) Box(Modifier.padding(top = 8.dp).fillMaxWidth().height(4.dp).background(s.raised, RoundedCornerShape(2.dp))) {
            Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).fillMaxHeight().background(s.accent, RoundedCornerShape(2.dp)))
        }
    }
}

private fun teacher(r: Ref, master: Master?) = if (r.id == 0L) "offen" else master?.find(Kind.TEACHER, r.id)?.let { it.longName.ifBlank { it.name } } ?: "?"

private fun subjectName(p: Period, master: Master?) =
    p.of(Kind.SUBJECT).firstOrNull()?.let { r -> master?.find(Kind.SUBJECT, r.id)?.let { it.longName.ifBlank { it.name } } } ?: p.lessonText.ifBlank { "Stunde" }

/** What changed about a lesson, as words in their colour: never colour alone. */
@Composable
private fun changes(p: Period, master: Master?): List<Pair<String, Color>> {
    val s = LocalSignals.current
    return buildList {
        if (p.cancelled) add("Entfall" to s.cancelled)
        if (p.isExam) add((p.exam ?: "Klausur") to s.exam)
        if (p.additional) add("Zusatzstunde" to s.extra)
        p.of(Kind.TEACHER).filter { it.changed }.takeIf { it.isNotEmpty() }?.let { rs -> add("Vertretung: " + rs.joinToString(", ") { teacher(it, master) } to s.changed) }
        if (p.of(Kind.ROOM).any { it.changed }) add("Raumänderung" to s.changed)
        if (p.irregular && isEmpty()) add("Geändert" to s.changed)
    }
}

/** The lesson in the Jetzt card: badge and name, then room, teacher and changes. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LessonHead(p: Period, master: Master?) {
    val s = LocalSignals.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SubjectBadge(p.subjectId, master, cancelled = p.cancelled)
            Text(subjectName(p, master), fontSize = 20.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                textDecoration = if (p.cancelled) TextDecoration.LineThrough else null, color = if (p.cancelled) s.muted else MaterialTheme.colorScheme.onSurface)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            p.of(Kind.ROOM).forEach { r ->
                val c = if (r.changed) s.changed else MaterialTheme.colorScheme.onSurfaceVariant
                Row {
                    Text("Raum ", fontSize = 14.sp, color = c)
                    Text(master?.find(Kind.ROOM, r.id)?.name ?: "?", fontSize = 14.sp, fontFamily = Mono, color = c)
                }
            }
            p.of(Kind.TEACHER).filter { !it.changed }.forEach { Text(teacher(it, master), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            changes(p, master).filter { it.first != "Raumänderung" }.forEach { (t, c) -> Text(t, fontSize = 14.sp, color = c, fontWeight = FontWeight.Medium) }
        }
    }
}

/** Moment 1, continued: the rest of the day as departure rows, Zeit | Fach | Raum. */
@Composable
private fun Departures(periods: List<Period>, master: Master?) {
    val s = LocalSignals.current
    Column {
        Row(Modifier.padding(top = 4.dp, bottom = 4.dp)) {
            Text("Zeit", Modifier.width(56.dp), fontSize = 12.sp, lineHeight = 16.sp, color = s.muted)
            Text("Fach", Modifier.padding(start = 12.dp).weight(1f), fontSize = 12.sp, lineHeight = 16.sp, color = s.muted)
            Text("Raum", fontSize = 12.sp, lineHeight = 16.sp, color = s.muted)
        }
        periods.forEach { p ->
            HorizontalDivider(color = s.line)
            val roomChanged = p.of(Kind.ROOM).any { it.changed }
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).alpha(if (p.cancelled) 0.55f else 1f).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(hm.format(p.start), Modifier.width(56.dp), fontFamily = Mono, fontSize = 15.sp)
                Row(Modifier.padding(start = 12.dp, end = 12.dp).weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SubjectBadge(p.subjectId, master, cancelled = p.cancelled)
                    Column {
                        Text(subjectName(p, master), maxLines = 1, overflow = TextOverflow.Ellipsis, textDecoration = if (p.cancelled) TextDecoration.LineThrough else null)
                        val c = changes(p, master)
                        if (c.isNotEmpty()) Text(c.joinToString(" · ") { it.first }, fontSize = 13.sp, color = c.first().second, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
                Text(p.of(Kind.ROOM).joinToString(",") { r -> master?.find(Kind.ROOM, r.id)?.name ?: "?" }, fontFamily = Mono, fontSize = 15.sp,
                    color = if (roomChanged) s.changed else MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/** E14: the week in four numbers; only the ones that are not zero. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeekLine(c: Derive.WeekCount) {
    val s = LocalSignals.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            c.cancelled to ("Entfall" to s.cancelled),
            c.substituted to ((if (c.substituted == 1) "Vertretung" else "Vertretungen") to s.changed),
            c.roomChanged to ((if (c.roomChanged == 1) "Raumänderung" else "Raumänderungen") to s.changed),
            c.exams to ((if (c.exams == 1) "Klausur" else "Klausuren") to s.exam),
        ).filter { it.first > 0 }.forEach { (n, l) -> Tag("$n ${l.first}", l.second) }
    }
}
