package io.github.brickystudios.stundenblick

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.util.Locale

private const val CENTER = 500
private val minuteHeight = 1.15.dp
private val gutter = 30.dp
private val de = Locale.GERMAN
private val hm = DateTimeFormatter.ofPattern("HH:mm")
private val dayLong = DateTimeFormatter.ofPattern("EEEE, d. MMMM", de)
private val dm = DateTimeFormatter.ofPattern("d.M.", de)

/** Weekend: show the coming week, the past one is over. */
private fun startMonday(today: LocalDate) =
    if (today.dayOfWeek >= DayOfWeek.SATURDAY) Parse.monday(today).plusWeeks(1) else Parse.monday(today)

private fun Master?.name(r: Ref, long: Boolean = false, original: Boolean = false): String {
    val id = if (original) r.orgId else r.id
    if (id == 0L) return "—"
    val e = this?.find(r.kind, id) ?: return "?"
    return if (long && e.longName.isNotBlank() && e.longName != e.name) (if (r.kind == Kind.TEACHER) "${e.longName} (${e.name})" else e.longName) else e.name
}

private fun Master?.subject(p: Period, long: Boolean = false) =
    p.of(Kind.SUBJECT).firstOrNull()?.let { name(it, long) } ?: p.lessonText.ifBlank { p.substText.ifBlank { "Stunde" } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimetableScreen(repo: Repo, element: Element, onPick: () -> Unit, onSettings: () -> Unit) {
    val master = repo.master.value
    val today = remember { LocalDate.now() }
    val base = remember { startMonday(today) }
    var weekView by remember { mutableStateOf(repo.weekView) }
    val weekPager = rememberPagerState(initialPage = CENTER) { CENTER * 2 }
    val firstDay = if (today.dayOfWeek >= DayOfWeek.SATURDAY) 0 else today.dayOfWeek.value - 1
    val dayPager = rememberPagerState(initialPage = CENTER * 5 + firstDay) { CENTER * 10 }
    val scope = rememberCoroutineScope()
    var detail by remember { mutableStateOf<Period?>(null) }
    val hidden = remember(repo.hiddenVersion.value, element.key) { repo.hidden(element) }
    val now by produceState(LocalDateTime.now()) { while (true) { delay(30_000); value = LocalDateTime.now() } }

    fun mondayOf(weekPage: Int): LocalDate = base.plusWeeks((weekPage - CENTER).toLong())
    fun dayOf(page: Int): LocalDate = base.plusWeeks((page / 5 - CENTER).toLong()).plusDays((page % 5).toLong())

    val shownMonday = if (weekView) mondayOf(weekPager.currentPage) else Parse.monday(dayOf(dayPager.currentPage))
    val week = repo.weeks[repo.weekKey(element, shownMonday)]
    val subtitle = if (weekView) {
        "KW ${shownMonday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)} · ${dm.format(shownMonday)}–${dm.format(shownMonday.plusDays(4))}"
    } else dayLong.format(dayOf(dayPager.currentPage))

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(Modifier.clickable(onClick = onPick)) {
                        Text(if (element.name == "Mein Plan") "Mein Plan" else element.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                },
                actions = {
                    TextButton(onClick = {
                        scope.launch { if (weekView) weekPager.animateScrollToPage(CENTER) else dayPager.animateScrollToPage(CENTER * 5 + firstDay) }
                    }) { Text("Heute") }
                    IconButton(onClick = { weekView = !weekView; repo.weekView = weekView }) {
                        Icon(if (weekView) Icons.Default.List else Icons.Default.DateRange, if (weekView) "Tagesansicht" else "Wochenansicht")
                    }
                    IconButton(onClick = onPick) { Icon(Icons.Default.Search, "Anderen Plan wählen") }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Einstellungen") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            StatusLine(week)
            PullToRefreshBox(
                isRefreshing = week?.loading == true && week.periods != null,
                onRefresh = { scope.launch { repo.load(element, shownMonday, force = true) } },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (weekView) {
                    HorizontalPager(weekPager, beyondViewportPageCount = 1) { page ->
                        val monday = mondayOf(page)
                        LaunchedEffect(element.key, monday) { repo.load(element, monday) }
                        val w = repo.weeks[repo.weekKey(element, monday)]
                        WeekPage(w, master, monday, today, now, hidden) { detail = it }
                    }
                } else {
                    HorizontalPager(dayPager, beyondViewportPageCount = 1) { page ->
                        val day = dayOf(page)
                        val monday = Parse.monday(day)
                        LaunchedEffect(element.key, monday) { repo.load(element, monday) }
                        val w = repo.weeks[repo.weekKey(element, monday)]
                        DayPage(w, master, day, element, hidden) { detail = it }
                    }
                }
            }
        }
    }
    detail?.let { DetailSheet(repo, it, master) { detail = null } }
}

@Composable
private fun StatusLine(week: Week?) {
    val error = week?.error ?: return
    val s = LocalSignals.current
    val stand = if (week.fetchedAt > 0) " Stand: " + java.text.SimpleDateFormat("EE HH:mm", de).format(java.util.Date(week.fetchedAt)) else ""
    Text(
        error + stand,
        Modifier.fillMaxWidth().background(s.changed.copy(alpha = 0.12f)).padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp,
    )
}

@Composable
private fun Centered(text: String, loading: Boolean = false) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        if (loading) CircularProgressIndicator() else Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WeekPage(week: Week?, master: Master?, monday: LocalDate, today: LocalDate, now: LocalDateTime, hidden: Set<Long>, onTap: (Period) -> Unit) {
    val all = week?.periods
    if (all == null) return Centered(week?.error ?: "", loading = week?.error == null)
    val periods = Filter.apply(all, hidden)
    val units = master?.units.orEmpty()
    val s = LocalSignals.current
    val showSat = periods.any { it.start.dayOfWeek == DayOfWeek.SATURDAY }
    val days = (0 until if (showSat) 6 else 5).map { monday.plusDays(it.toLong()) }
    val first = listOfNotNull(units.firstOrNull()?.start, periods.minOfOrNull { it.start.toLocalTime() }).minOrNull() ?: LocalTime.of(8, 0)
    val last = listOfNotNull(units.lastOrNull()?.end, periods.maxOfOrNull { it.end.toLocalTime() }).maxOrNull() ?: LocalTime.of(16, 0)
    val startMin = first.toSecondOfDay() / 60
    val total = (last.toSecondOfDay() / 60 - startMin).coerceAtLeast(60)
    fun y(t: LocalTime): Dp = minuteHeight * (t.toSecondOfDay() / 60 - startMin)

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(end = 6.dp, bottom = 4.dp)) {
            Spacer(Modifier.width(gutter))
            days.forEach { d ->
                val isToday = d == today
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, de).removeSuffix("."), fontSize = 12.sp,
                        color = if (isToday) MaterialTheme.colorScheme.primary else s.muted, fontWeight = FontWeight.Medium)
                    Box(
                        Modifier.size(26.dp).background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${d.dayOfMonth}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                            color = if (isToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        HorizontalDivider(color = s.line)
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().height(minuteHeight * total + 8.dp).padding(top = 4.dp, end = 6.dp)) {
                Box(Modifier.width(gutter).fillMaxHeight()) {
                    units.forEachIndexed { i, u ->
                        Column(Modifier.offset(y = y(u.start)).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${i + 1}", fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(hm.format(u.start), fontSize = 8.sp, lineHeight = 10.sp, color = s.muted)
                        }
                    }
                }
                days.forEach { d ->
                    BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                        val w = maxWidth
                        units.forEach { u ->
                            Box(Modifier.offset(y = y(u.start)).fillMaxWidth().height(minuteHeight * ((u.end.toSecondOfDay() - u.start.toSecondOfDay()) / 60))
                                .padding(1.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(6.dp)))
                        }
                        val dayPeriods = periods.filter { it.start.toLocalDate() == d }
                        if (dayPeriods.isEmpty()) master?.holiday(d)?.let { name ->
                            Text(name, Modifier.align(Alignment.Center).padding(4.dp), fontSize = 11.sp, color = s.muted, maxLines = 4)
                        }
                        lanes(dayPeriods).forEach { pl ->
                            val p = pl.period
                            val laneW = w / pl.lanes
                            Box(
                                Modifier.offset(x = laneW * pl.lane, y = y(p.start.toLocalTime()))
                                    .width(laneW).height(minuteHeight * ((p.end.toLocalTime().toSecondOfDay() - p.start.toLocalTime().toSecondOfDay()) / 60))
                                    .padding(1.5.dp),
                            ) { Cell(p, master, onTap) }
                        }
                        if (d == today && now.toLocalTime() > first && now.toLocalTime() < last) {
                            Box(Modifier.offset(y = y(now.toLocalTime()) - 1.dp).fillMaxWidth().height(2.dp).background(s.now))
                            Box(Modifier.offset(x = (-3).dp, y = y(now.toLocalTime()) - 4.dp).size(8.dp).background(s.now, CircleShape))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun accentOf(p: Period): Color? {
    val s = LocalSignals.current
    return when {
        p.cancelled -> s.cancelled
        p.isExam -> s.exam
        p.additional -> s.extra
        p.irregular -> s.changed
        else -> null
    }
}

@Composable
private fun Cell(p: Period, master: Master?, onTap: (Period) -> Unit) {
    val s = LocalSignals.current
    val accent = accentOf(p)
    val stripe = untisColor(p.color) ?: MaterialTheme.colorScheme.outline
    val shape = RoundedCornerShape(7.dp)
    val subject = master.subject(p)
    val room = p.of(Kind.ROOM).joinToString(",") { master.name(it) }
    val teacher = p.of(Kind.TEACHER).joinToString(",") { master.name(it) }
    val tags = tagsFor(p).joinToString(", ") { it.first }
    Row(
        Modifier.fillMaxSize().clip(shape)
            .background(if (p.cancelled) s.cancelled.copy(alpha = 0.07f) else MaterialTheme.colorScheme.surface)
            .border(if (accent != null) 1.5.dp else 0.5.dp, accent ?: MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable { onTap(p) }
            .semantics { contentDescription = "$subject ${hm.format(p.start)}, Raum $room, $teacher. $tags" },
    ) {
        Box(Modifier.width(3.dp).fillMaxHeight().background(if (p.cancelled) s.cancelled.copy(alpha = 0.4f) else stripe))
        Column(Modifier.padding(horizontal = 3.dp, vertical = 2.dp)) {
            Text(subject, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Clip,
                textDecoration = if (p.cancelled) TextDecoration.LineThrough else null,
                color = if (p.cancelled) s.muted else MaterialTheme.colorScheme.onSurface)
            if (room.isNotBlank()) Text(room, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1, overflow = TextOverflow.Clip,
                color = if (p.of(Kind.ROOM).any { it.changed }) s.changed else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (p.of(Kind.ROOM).any { it.changed }) FontWeight.Bold else null)
            if (teacher.isNotBlank()) Text(teacher, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 1, overflow = TextOverflow.Clip,
                color = if (p.of(Kind.TEACHER).any { it.changed }) s.changed else s.muted,
                fontWeight = if (p.of(Kind.TEACHER).any { it.changed }) FontWeight.Bold else null)
        }
    }
}

@Composable
private fun DayPage(week: Week?, master: Master?, day: LocalDate, element: Element, hidden: Set<Long>, onTap: (Period) -> Unit) {
    val all = week?.periods
    if (all == null) return Centered(week?.error ?: "", loading = week?.error == null)
    val periods = Filter.apply(all, hidden).filter { it.start.toLocalDate() == day }
    if (periods.isEmpty()) return Centered(master?.holiday(day)?.let { "Ferien: $it" } ?: "Keine Stunden an diesem Tag.")
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(periods, key = { it.id }) { p -> PeriodRow(p, master, element, onTap) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeriodRow(p: Period, master: Master?, element: Element, onTap: (Period) -> Unit) {
    val s = LocalSignals.current
    val accent = accentOf(p)
    val shape = RoundedCornerShape(12.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface)
            .border(if (accent != null) 1.5.dp else 0.5.dp, accent ?: MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable { onTap(p) }.padding(12.dp),
    ) {
        Column(Modifier.width(58.dp)) {
            master?.unitNumber(p.start.toLocalTime())?.let { Text("$it. Std.", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) }
            Text(hm.format(p.start), fontSize = 12.sp, color = s.muted)
            Text(hm.format(p.end), fontSize = 12.sp, color = s.muted)
        }
        Box(Modifier.width(3.dp).height(44.dp).background(untisColor(p.color) ?: MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(master.subject(p, long = true), fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                textDecoration = if (p.cancelled) TextDecoration.LineThrough else null,
                color = if (p.cancelled) s.muted else MaterialTheme.colorScheme.onSurface)
            val tags = tagsFor(p)
            if (tags.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { tags.forEach { (t, c) -> Tag(t, c) } }
            RefLine(p, Kind.TEACHER, master)
            RefLine(p, Kind.ROOM, master)
            if (element.kind != Kind.CLASS && element.kind != Kind.STUDENT) RefLine(p, Kind.CLASS, master)
            // Untis often repeats the tag ("Vertretung") as substitution text: show it once
            listOf(p.substText, p.info, p.lessonText).filter { t -> t.isNotBlank() && tags.none { it.first.equals(t, true) } }.distinct().forEach {
                Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** "Anna Albers (ALB)", or for a substitution the new name plus the struck original */
@Composable
private fun RefLine(p: Period, kind: Kind, master: Master?) {
    val refs = p.of(kind)
    if (refs.isEmpty()) return
    val s = LocalSignals.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(kind.label + ": ", fontSize = 13.sp, color = s.muted)
        refs.forEachIndexed { i, r ->
            if (i > 0) Text(", ", fontSize = 13.sp)
            Text(master.name(r, long = kind == Kind.TEACHER), fontSize = 13.sp,
                color = if (r.changed) s.changed else MaterialTheme.colorScheme.onSurface, fontWeight = if (r.changed) FontWeight.SemiBold else null)
            if (r.changed) Text(" " + master.name(r, original = true), fontSize = 13.sp, color = s.muted, textDecoration = TextDecoration.LineThrough)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DetailSheet(repo: Repo, p: Period, master: Master?, onDismiss: () -> Unit) {
    val s = LocalSignals.current
    val extra by produceState<Result<Parse.Extra?>?>(null, p.id) { value = repo.extra(p) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(master.subject(p, long = true), fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                textDecoration = if (p.cancelled) TextDecoration.LineThrough else null)
            val n = master?.unitNumber(p.start.toLocalTime())?.let { "$it. Stunde · " }.orEmpty()
            Text(n + dayLong.format(p.start) + " · " + hm.format(p.start) + "–" + hm.format(p.end), color = s.muted, fontSize = 14.sp)
            val tags = tagsFor(p)
            if (tags.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { tags.forEach { (t, c) -> Tag(t, c) } }
            HorizontalDivider(color = s.line)
            RefLine(p, Kind.TEACHER, master)
            RefLine(p, Kind.ROOM, master)
            RefLine(p, Kind.CLASS, master)
            listOf(p.substText, p.info, p.lessonText).filter { t -> t.isNotBlank() && tags.none { it.first.equals(t, true) } }.distinct().forEach { Text(it, fontSize = 15.sp) }
            HorizontalDivider(color = s.line)
            when (val r = extra) {
                null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Lade Stundeninhalt und Hausaufgaben …", color = s.muted, fontSize = 14.sp)
                }
                else -> r.fold(
                    { x ->
                        if (x == null || (x.topic.isBlank() && x.homework.isEmpty() && x.info.isBlank())) {
                            Text("Keine weiteren Angaben von der Schule.", color = s.muted, fontSize = 14.sp)
                        } else {
                            if (x.topic.isNotBlank()) Labeled("Stundeninhalt", x.topic)
                            x.homework.forEach { Labeled("Hausaufgabe", it) }
                            if (x.info.isNotBlank()) Labeled("Info", x.info)
                        }
                    },
                    { Text(Errors.explain(it), color = s.muted, fontSize = 14.sp) },
                )
            }
        }
    }
}

@Composable
private fun Labeled(label: String, text: String) {
    Column {
        Text(label, fontSize = 12.sp, color = LocalSignals.current.muted, fontWeight = FontWeight.Medium)
        Text(text, fontSize = 15.sp)
    }
}
