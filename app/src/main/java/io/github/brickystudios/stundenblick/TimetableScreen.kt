package io.github.brickystudios.stundenblick

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
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
private val minuteHeight = 1.3.dp
private val gutter = 38.dp
private val de = Locale.GERMAN
private val hm = DateTimeFormatter.ofPattern("HH:mm")
private val dayLong = DateTimeFormatter.ofPattern("EEEE, d. MMMM", de)
private val dm = DateTimeFormatter.ofPattern("d.M.", de)
private val dMonth = DateTimeFormatter.ofPattern("d. MMMM", de)

/** "16.–20. November", or "30. November – 4. Dezember" across a month */
private fun span(a: LocalDate, b: LocalDate) = if (a.month == b.month) "${a.dayOfMonth}.–${dMonth.format(b)}" else "${dMonth.format(a)} – ${dMonth.format(b)}"

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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TimetableScreen(repo: Repo, element: Element, onPick: () -> Unit, onSettings: () -> Unit, onOpen: (Element) -> Unit) {
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
    val snackbar = remember { SnackbarHostState() }
    val isOwn = element.key == repo.own.value?.key
    // D3: long press hides the subject in this plan, with a way back
    val hide: (Period) -> Unit = hide@{ p ->
        val sid = p.subjectId ?: return@hide
        repo.setHidden(element, repo.hidden(element) + sid)
        scope.launch {
            val name = repo.master.value?.find(Kind.SUBJECT, sid)?.name ?: "Fach"
            if (snackbar.showSnackbar("$name ausgeblendet", "Rückgängig", duration = SnackbarDuration.Short) == SnackbarResult.ActionPerformed)
                repo.setHidden(element, repo.hidden(element) - sid)
        }
    }

    fun mondayOf(weekPage: Int): LocalDate = base.plusWeeks((weekPage - CENTER).toLong())
    fun dayOf(page: Int): LocalDate = base.plusWeeks((page / 5 - CENTER).toLong()).plusDays((page % 5).toLong())

    val shownMonday = if (weekView) mondayOf(weekPager.currentPage) else Parse.monday(dayOf(dayPager.currentPage))
    val week = repo.shown(element, shownMonday)
    val subtitle = if (weekView) {
        "KW ${shownMonday.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)} · ${span(shownMonday, shownMonday.plusDays(4))}"
    } else dayLong.format(dayOf(dayPager.currentPage))

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(element.name, Modifier.clickable(onClick = onPick), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                expandedHeight = 56.dp,
                actions = {
                    TextButton(onClick = {
                        scope.launch { val ease = tween<Float>(400, easing = Ease); if (weekView) weekPager.animateScrollToPage(CENTER, animationSpec = ease) else dayPager.animateScrollToPage(CENTER * 5 + firstDay, animationSpec = ease) }
                    }) { Text("Jetzt") }
                    IconButton(onClick = { weekView = !weekView; repo.weekView = weekView }) {
                        Icon(if (weekView) Icons.AutoMirrored.Outlined.List else Icons.Outlined.DateRange, if (weekView) "Tagesansicht" else "Wochenansicht")
                    }
                    IconButton(onClick = onPick) { Icon(Icons.Outlined.Search, "Anderen Plan wählen") }
                    IconButton(onClick = onSettings) { Icon(Icons.Outlined.Settings, "Einstellungen") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            // under the bar, not in it: on a 360dp phone the actions leave the title too little room for the week
            Text(subtitle, Modifier.padding(horizontal = 16.dp), fontSize = 14.sp, lineHeight = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            StatusLine(week)
            if (!isOwn && element.kind != Kind.SUBJECT) NowLine(repo, element, master, now)
            week?.periods?.let { ps ->
                val c = Derive.weekCount(Derive.blocks(Filter.apply(ps, hidden)))
                if (c.any) Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)) { WeekLine(c) }
                ProjectedLine(ps.filter { it.projected }.map { it.start.toLocalDate() }.distinct().sorted(), weekView, if (weekView) null else dayOf(dayPager.currentPage))
            }
            PullToRefreshBox(
                isRefreshing = week?.loading == true && week.periods != null,
                onRefresh = { scope.launch { repo.load(element, shownMonday, force = true) } },
                modifier = Modifier.fillMaxSize(),
            ) {
                if (weekView) {
                    HorizontalPager(weekPager, beyondViewportPageCount = 1) { page ->
                        val monday = mondayOf(page)
                        LaunchedEffect(element.key, monday) { repo.load(element, monday); repo.loadSources(element, monday) }
                        val w = repo.shown(element, monday)
                        // E11: the dot stays for this visit; the week counts as seen once it is the current page
                        val fresh = remember(element.key, monday) { if (isOwn) repo.unseen() else emptySet() }
                        if (isOwn && weekPager.currentPage == page) LaunchedEffect(w?.periods) { w?.periods?.let { ps -> repo.markSeen(ps.filter { !it.projected }.map { it.id }) } }
                        WeekPage(w, master, monday, today, now, hidden, fresh, hide) { detail = it }
                    }
                } else {
                    HorizontalPager(dayPager, beyondViewportPageCount = 1) { page ->
                        val day = dayOf(page)
                        val monday = Parse.monday(day)
                        LaunchedEffect(element.key, monday) { repo.load(element, monday); repo.loadSources(element, monday) }
                        val w = repo.shown(element, monday)
                        val fresh = remember(element.key, monday) { if (isOwn) repo.unseen() else emptySet() }
                        if (isOwn && dayPager.currentPage == page) LaunchedEffect(w?.periods, day) {
                            w?.periods?.let { ps -> repo.markSeen(ps.filter { it.start.toLocalDate() == day }.map { it.id }) }
                        }
                        DayPage(w, master, day, element, hidden, fresh, hide) { detail = it }
                    }
                }
            }
        }
    }
    detail?.let { DetailSheet(repo, it, master, onOpen = { e -> detail = null; onOpen(e) }) { detail = null } }
}

/** which days of the week are projected, in one line above the plan */
@Composable
private fun ProjectedLine(days: List<LocalDate>, weekView: Boolean, day: LocalDate?) {
    if (days.isEmpty() || (!weekView && day !in days)) return
    val names = days.joinToString(", ") { it.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, de).removeSuffix(".") }
    Text(
        (if (weekView) "$names: voraussichtlich" else "Voraussichtlich") + ", aus einer früheren Woche. Die Schule zeigt " +
            (if (weekView && days.size > 1) "diese Tage" else "den Tag") + " noch nicht, Ausfälle und Vertretungen fehlen.",
        Modifier.fillMaxWidth().background(LocalSignals.current.raised).padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp,
    )
}

@Composable
private fun StatusLine(week: Week?) {
    val error = week?.error ?: return
    val s = LocalSignals.current
    val stand = if (week.fetchedAt > 0) " Stand: " + java.text.SimpleDateFormat("EE HH:mm", de).format(java.util.Date(week.fetchedAt)) else ""
    Text(
        error + stand,
        Modifier.fillMaxWidth().background(s.raised).padding(horizontal = 16.dp, vertical = 8.dp),
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
private fun WeekPage(week: Week?, master: Master?, monday: LocalDate, today: LocalDate, now: LocalDateTime, hidden: Set<Long>, fresh: Set<Long>, onHide: (Period) -> Unit, onTap: (Period) -> Unit) {
    val all = week?.periods
    if (all == null) return Centered(week?.error ?: "", loading = week?.error == null)
    val periods = Derive.blocks(Filter.apply(all, hidden))
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
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, top = 4.dp, end = 12.dp, bottom = 8.dp)) {
            Spacer(Modifier.width(gutter))
            days.forEach { d ->
                val isToday = d == today
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, de).removeSuffix("."), fontSize = 12.sp, lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(
                        Modifier.size(32.dp, 28.dp).background(if (isToday) s.accent else Color.Transparent, RoundedCornerShape(6.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${d.dayOfMonth}", fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium,
                            color = if (isToday) s.onAccent else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        HorizontalDivider(color = s.line)
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().height(minuteHeight * total + 12.dp).padding(start = 16.dp, top = 8.dp, end = 12.dp)) {
                Box(Modifier.width(gutter).fillMaxHeight()) {
                    units.forEachIndexed { i, u ->
                        Column(Modifier.offset(y = y(u.start))) {
                            Text("${i + 1}", fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(hm.format(u.start), fontFamily = Mono, fontSize = 11.sp, lineHeight = 14.sp, color = s.muted)
                        }
                    }
                }
                days.forEach { d ->
                    BoxWithConstraints(Modifier.weight(1f).fillMaxHeight()) {
                        val w = maxWidth
                        val dayPeriods = periods.filter { it.start.toLocalDate() == d }
                        if (dayPeriods.isEmpty()) master?.holiday(d)?.let { name ->
                            Text(name, Modifier.align(Alignment.Center).padding(4.dp), fontSize = 11.sp, lineHeight = 14.sp, color = s.muted, maxLines = 4)
                        }
                        lanes(dayPeriods).forEach { pl ->
                            val p = pl.period
                            val laneW = w / pl.lanes
                            Box(
                                Modifier.offset(x = laneW * pl.lane, y = y(p.start.toLocalTime()))
                                    .width(laneW).height(minuteHeight * ((p.end.toLocalTime().toSecondOfDay() - p.start.toLocalTime().toSecondOfDay()) / 60))
                                    .padding(1.5.dp),
                            ) { Cell(p, master, pl.lanes == 1, p.ids.any { it in fresh }, onHide, onTap) }
                        }
                    }
                }
            }
            // the Jetzt line runs across the whole week, its dot on the edge of the time column
            if (today in days && now.toLocalTime() > first && now.toLocalTime() < last) {
                val at = 8.dp + y(now.toLocalTime())
                Box(Modifier.padding(start = 16.dp + gutter, end = 12.dp).offset(y = at - 1.dp).fillMaxWidth().height(2.dp).background(s.now))
                Box(Modifier.offset(x = 16.dp + gutter - 5.dp, y = at - 5.dp).size(10.dp).background(s.now, CircleShape))
            }
        }
    }
}

/** the block's frame: yellow for a change, light for an exam, a hairline otherwise; Entfall is told by the badge and the fade */
@Composable
private fun frameOf(p: Period): Color {
    val s = LocalSignals.current
    return when {
        p.cancelled -> s.line
        p.isExam -> s.exam
        p.additional || p.irregular -> s.changed
        else -> s.line
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Cell(p: Period, master: Master?, wide: Boolean, isNew: Boolean, onHide: (Period) -> Unit, onTap: (Period) -> Unit) {
    val s = LocalSignals.current
    val shape = RoundedCornerShape(6.dp)
    val subject = master.subject(p)
    val room = p.of(Kind.ROOM).joinToString(",") { master.name(it) }
    val teacher = p.of(Kind.TEACHER).joinToString(",") { master.name(it) }
    val tags = tagsFor(p).joinToString(", ") { it.first }
    val frame = frameOf(p)
    Box(
        Modifier.fillMaxSize().alpha((if (p.projected) 0.5f else 1f) * (if (p.cancelled) 0.55f else 1f)).clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, frame, shape)
            .combinedClickable(onClick = { onTap(p) }, onLongClick = { onHide(p) }, onLongClickLabel = "Fach ausblenden")
            .semantics { contentDescription = "$subject ${hm.format(p.start)}, Raum $room, $teacher. $tags" + (if (isNew) ", neu" else "") + if (p.projected) ", voraussichtlich" else "" },
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = if (wide) 4.dp else 2.dp, vertical = 3.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (p.subjectId != null) SubjectBadge(p.subjectId, master, cancelled = p.cancelled, small = true)
            else Text(subject, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Clip)
            Column {
                if (room.isNotBlank()) Text(room, fontFamily = Mono, fontSize = 11.sp, lineHeight = 13.sp, maxLines = 1, overflow = TextOverflow.Clip, softWrap = false,
                    color = if (p.of(Kind.ROOM).any { it.changed }) s.changed else MaterialTheme.colorScheme.onSurfaceVariant)
                if (teacher.isNotBlank()) Text(teacher, fontFamily = Mono, fontSize = 11.sp, lineHeight = 13.sp, maxLines = 1, overflow = TextOverflow.Clip, softWrap = false,
                    color = if (p.of(Kind.TEACHER).any { it.changed }) s.changed else MaterialTheme.colorScheme.onSurfaceVariant)
                // E5: the course or group name fits in a single-lane block of 56 minutes or more
                val course = p.lessonText.takeIf { it.isNotBlank() && it != subject }
                if (wide && course != null && p.end.toLocalTime().toSecondOfDay() - p.start.toLocalTime().toSecondOfDay() >= 56 * 60)
                    Text(course, fontSize = 10.sp, lineHeight = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, color = s.muted)
            }
        }
        if (isNew) Box(Modifier.align(Alignment.TopEnd).padding(4.dp).size(6.dp).background(s.accent, CircleShape))
    }
}

@Composable
private fun DayPage(week: Week?, master: Master?, day: LocalDate, element: Element, hidden: Set<Long>, fresh: Set<Long>, onHide: (Period) -> Unit, onTap: (Period) -> Unit) {
    val all = week?.periods
    if (all == null) return Centered(week?.error ?: "", loading = week?.error == null)
    val periods = Derive.blocks(Filter.apply(all, hidden).filter { it.start.toLocalDate() == day })
    if (periods.isEmpty()) return Centered(master?.holiday(day)?.let { "Ferien: $it" } ?: "Keine Stunden an diesem Tag")
    val rows = if (master != null) Derive.dayRows(periods, master) else periods.map { Derive.Row.Lesson(it) }
    // fill the page: a pager centres pages that are shorter than itself
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(rows, key = { r -> if (r is Derive.Row.Lesson) r.period.id else (r as Derive.Row.Gap).let { "gap${it.start}-${it.end}" } }) { r ->
            when (r) {
                is Derive.Row.Lesson -> PeriodRow(r.period, master, element, r.period.ids.any { it in fresh }, onHide, onTap)
                is Derive.Row.Gap -> Text("${r.label}, ${hm.format(r.start)}–${hm.format(r.end)}", Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    fontSize = 13.sp, color = LocalSignals.current.muted, textAlign = TextAlign.Center)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun PeriodRow(p: Period, master: Master?, element: Element, isNew: Boolean, onHide: (Period) -> Unit, onTap: (Period) -> Unit) {
    val s = LocalSignals.current
    val shape = MaterialTheme.shapes.medium
    Row(
        Modifier.fillMaxWidth().alpha(if (p.projected) 0.55f else 1f).clip(shape).background(MaterialTheme.colorScheme.surface)
            .border(1.dp, frameOf(p), shape)
            .combinedClickable(onClick = { onTap(p) }, onLongClick = { onHide(p) }, onLongClickLabel = "Fach ausblenden").padding(12.dp),
    ) {
        Column(Modifier.width(64.dp)) {
            master?.let { Derive.unitLabel(p, it) }?.let { Text(it, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (isNew) Text("neu", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = s.accent)
            Text(hm.format(p.start), fontFamily = Mono, fontSize = 12.sp, color = s.muted)
            Text(hm.format(p.end), fontFamily = Mono, fontSize = 12.sp, color = s.muted)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SubjectBadge(p.subjectId, master, cancelled = p.cancelled)
                Text(master.subject(p, long = true), fontSize = 16.sp, fontWeight = FontWeight.Medium,
                    textDecoration = if (p.cancelled) TextDecoration.LineThrough else null,
                    color = if (p.cancelled) s.muted else MaterialTheme.colorScheme.onSurface)
            }
            val tags = tagsFor(p)
            if (tags.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { tags.forEach { (t, c) -> Tag(t, c) } }
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
private fun RefLine(p: Period, kind: Kind, master: Master?, onOpen: ((Element) -> Unit)? = null) {
    val refs = p.of(kind)
    if (refs.isEmpty()) return
    val s = LocalSignals.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(kind.label + ": ", fontSize = 13.sp, color = s.muted)
        refs.forEachIndexed { i, r ->
            if (i > 0) Text(", ", fontSize = 13.sp)
            val target = master?.find(kind, r.id)?.takeIf { r.id != 0L && onOpen != null }
            Text(master.name(r, long = kind == Kind.TEACHER), fontSize = 13.sp,
                color = if (r.changed) s.changed else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (r.changed) FontWeight.Medium else null,
                textDecoration = if (target != null) TextDecoration.Underline else null,
                modifier = if (target != null) Modifier.clickable(onClickLabel = "Plan öffnen") { onOpen!!(target) } else Modifier)
            if (r.changed) Text(" " + master.name(r, original = true), fontSize = 13.sp, color = s.muted, textDecoration = TextDecoration.LineThrough)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DetailSheet(repo: Repo, p: Period, master: Master?, onOpen: (Element) -> Unit, onDismiss: () -> Unit) {
    val s = LocalSignals.current
    val extra by produceState<Result<Parse.Extra?>?>(null, p.id) { value = if (p.projected) Result.success(null) else repo.extra(p) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SubjectBadge(p.subjectId, master, cancelled = p.cancelled)
                Text(master.subject(p, long = true), fontSize = 20.sp, fontWeight = FontWeight.Medium,
                    textDecoration = if (p.cancelled) TextDecoration.LineThrough else null)
            }
            val n = master?.let { Derive.unitLabel(p, it) }?.let { "$it, " }.orEmpty()
            Text(n + dayLong.format(p.start) + ", " + hm.format(p.start) + "–" + hm.format(p.end), color = s.muted, fontSize = 14.sp)
            val tags = tagsFor(p)
            if (tags.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { tags.forEach { (t, c) -> Tag(t, c) } }
            if (p.projected) Text("Voraussichtlich: so lag die Stunde in einer früheren Woche. Deine Schule zeigt diesen Tag noch nicht, Ausfälle und Vertretungen fehlen also noch.",
                fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HorizontalDivider(color = s.line)
            RefLine(p, Kind.TEACHER, master, onOpen)
            RefLine(p, Kind.ROOM, master, onOpen)
            RefLine(p, Kind.CLASS, master, onOpen)
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
                            Text("Keine weiteren Angaben von der Schule", color = s.muted, fontSize = 14.sp)
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
        Text(label, fontSize = 12.sp, color = LocalSignals.current.muted)
        Text(text, fontSize = 15.sp)
    }
}

/** E12: over a teacher's, room's or class's plan, where they are now (from their current week). */
@Composable
private fun NowLine(repo: Repo, element: Element, master: Master?, now: LocalDateTime) {
    val ps = repo.weeks[repo.weekKey(element, Parse.monday(now.toLocalDate()))]?.periods ?: return
    val st = Derive.now(Derive.blocks(ps), now)
    fun where(p: Period) = when (element.kind) {
        Kind.TEACHER -> p.of(Kind.ROOM).joinToString(", ") { "Raum " + master.name(it) } + " (" + master.subject(p) + ")"
        Kind.ROOM -> (p.of(Kind.CLASS).joinToString(", ") { master.name(it) } + " " + master.subject(p) + " " + p.of(Kind.TEACHER).joinToString(", ") { master.name(it) }).trim()
        else -> master.subject(p) + ", " + p.of(Kind.ROOM).joinToString(", ") { "Raum " + master.name(it) }
    }
    val current = st.current.firstOrNull { !it.cancelled }
    val text = when {
        current != null -> "Jetzt: " + where(current) + ", bis " + hm.format(current.end)
        st.next.isNotEmpty() -> "Jetzt frei, um " + hm.format(st.next.first().start) + ": " + where(st.next.first())
        else -> return
    }
    Text(text, Modifier.fillMaxWidth().background(LocalSignals.current.raised).padding(horizontal = 16.dp, vertical = 8.dp),
        fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
}
