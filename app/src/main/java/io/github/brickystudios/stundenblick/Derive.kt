package io.github.brickystudios.stundenblick

import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * What Stundenblick computes on top of Untis' data: double lessons, breaks, the week's counts, now and next,
 * the next school day, a room's free time. Pure functions, so the JVM tests cover every rule.
 */
object Derive {
    private const val SEAMLESS_MIN = 10L
    private const val BREAK_MIN = 45L

    /** Consecutive periods of one course (same lessonId, elements and status, at most 10 min apart) become one block. */
    fun blocks(periods: List<Period>): List<Period> {
        val out = mutableListOf<Period>()
        val lastOf = HashMap<String, Int>()
        for (p in periods.sortedWith(compareBy({ it.start }, { it.id }))) {
            val key = "${p.lessonId}|${p.refs.sortedBy { "${it.kind}${it.id}${it.orgId}" }}|${p.states.sorted()}|${p.exam}"
            val i = lastOf[key]
            val prev = i?.let { out[it] }
            if (p.lessonId != 0L && prev != null && prev.end.toLocalDate() == p.start.toLocalDate() &&
                !p.start.isBefore(prev.end) && Duration.between(prev.end, p.start).toMinutes() <= SEAMLESS_MIN
            ) {
                out[i] = prev.copy(end = p.end, parts = prev.ids + p.id)
            } else {
                lastOf[key] = out.size
                out += p
            }
        }
        return out.sortedBy { it.start }
    }

    /** "1.–2. Std." for a block, "3. Std." for one lesson, null outside the grid */
    fun unitLabel(p: Period, master: Master): String? {
        val a = master.unitNumber(p.start.toLocalTime()) ?: return null
        val b = master.units.indexOfFirst { it.end == p.end.toLocalTime() }.takeIf { it >= 0 }?.plus(1)
        return if (b != null && b > a) "$a.–$b. Std." else "$a. Std."
    }

    sealed class Row {
        data class Lesson(val period: Period) : Row()
        data class Gap(val start: LocalTime, val end: LocalTime, val label: String) : Row()
    }

    /** A day's list with its breaks and free periods between the lessons that take place. */
    fun dayRows(day: List<Period>, master: Master): List<Row> {
        val rows = mutableListOf<Row>()
        var lastEnd: LocalTime? = null
        for (p in day.sortedBy { it.start }) {
            // a cancelled lesson keeps its row; the free time around it shows once, before the next lesson that takes place
            if (p.cancelled) { rows += Row.Lesson(p); continue }
            val s = p.start.toLocalTime()
            val end = lastEnd
            if (end != null && s.isAfter(end)) {
                val gap = Duration.between(end, s).toMinutes()
                val covered = master.units.count { !it.start.isBefore(end) && !it.end.isAfter(s) }
                val label = when {
                    covered == 1 -> "Freistunde"
                    covered > 1 -> "$covered Freistunden"
                    gap < BREAK_MIN -> "$gap min Pause"
                    else -> "$gap min frei"
                }
                rows += Row.Gap(end, s, label)
            }
            rows += Row.Lesson(p)
            lastEnd = maxOf(lastEnd ?: p.end.toLocalTime(), p.end.toLocalTime())
        }
        return rows
    }

    data class WeekCount(val cancelled: Int, val substituted: Int, val roomChanged: Int, val exams: Int) {
        val any get() = cancelled + substituted + roomChanged + exams > 0
    }

    /** One exam = one day and one title (the subject if Untis sends none), however many lessons and breaks it spans. */
    fun examKey(p: Period): Any = p.start.toLocalDate() to (p.exam ?: p.subjectId)

    /** Counts per block; a cancellation counts only as a cancellation, a long exam only once. */
    fun weekCount(periods: List<Period>): WeekCount {
        val held = periods.filter { !it.cancelled }
        return WeekCount(
            cancelled = periods.size - held.size,
            substituted = held.count { p -> p.of(Kind.TEACHER).any { it.changed } },
            roomChanged = held.count { p -> p.of(Kind.ROOM).any { it.changed } },
            exams = held.filter { it.isExam }.distinctBy(::examKey).size,
        )
    }

    /** current: what runs at [at] (cancelled included, to show it); next: the next lessons that take place today. */
    data class Now(val current: List<Period>, val next: List<Period>) {
        fun minutesLeft(at: LocalDateTime) = current.maxOfOrNull { it.end }?.let { Duration.between(at, it).toMinutes() }
    }

    fun now(periods: List<Period>, at: LocalDateTime): Now {
        val today = periods.filter { it.start.toLocalDate() == at.toLocalDate() }
        val current = today.filter { !it.start.isAfter(at) && it.end.isAfter(at) }
        val upcoming = today.filter { it.start.isAfter(at) && !it.cancelled }
        val first = upcoming.minOfOrNull { it.start }
        return Now(current, upcoming.filter { it.start == first })
    }

    /** The next day with school after [after]: no Sunday, no holiday, Saturday only for Saturday schools. */
    fun nextSchoolDay(after: LocalDate, master: Master?, saturday: Boolean): LocalDate {
        var d = after.plusDays(1)
        repeat(400) {
            val weekend = d.dayOfWeek == DayOfWeek.SUNDAY || (d.dayOfWeek == DayOfWeek.SATURDAY && !saturday)
            if (!weekend && master?.holiday(d) == null) return d
            d = d.plusDays(1)
        }
        return d
    }

    /** Days until the next holiday that has not started; while one runs, its end. */
    data class Holiday(val name: String, val start: LocalDate, val end: LocalDate, val running: Boolean)

    fun nextHoliday(today: LocalDate, master: Master): Holiday? =
        master.holidays.filter { !it.second.endInclusive.isBefore(today) }.minByOrNull { it.second.start }
            ?.let { (name, r) -> Holiday(name, r.start, r.endInclusive, !today.isBefore(r.start)) }

    /** A room's day: busy now or free, until when, and the next free unit. */
    data class RoomState(val busy: Period?, val freeUntil: LocalTime?, val nextFree: LocalTime?)

    fun roomState(periods: List<Period>, at: LocalDateTime, master: Master): RoomState {
        val day = periods.filter { it.start.toLocalDate() == at.toLocalDate() && !it.cancelled }.sortedBy { it.start }
        val busy = day.firstOrNull { !it.start.isAfter(at) && it.end.isAfter(at) }
        if (busy == null) return RoomState(null, day.firstOrNull { it.start.isAfter(at) }?.start?.toLocalTime(), null)
        // first grid unit after now that no lesson touches
        val t = at.toLocalTime()
        val free = master.units.firstOrNull { u -> u.start.isAfter(t) && day.none { it.start.toLocalTime() < u.end && it.end.toLocalTime() > u.start } }
        return RoomState(busy, null, free?.start)
    }

    // ---- the whole school (from all class plans): which rooms are free, who is where

    private fun overlaps(p: Period, u: Slot) = p.start.toLocalTime() < u.end && p.end.toLocalTime() > u.start

    /** the unit running at [t], else the next one, else the last */
    fun slotIndex(units: List<Slot>, t: LocalTime): Int =
        units.indexOfFirst { t < it.end }.takeIf { it >= 0 } ?: (units.size - 1).coerceAtLeast(0)

    /** rooms that appear in any lesson of the week: teaching rooms, without staff room or office */
    fun teachingRooms(all: List<Period>, master: Master): List<Element> {
        val used = all.flatMap { it.of(Kind.ROOM) }.flatMap { listOf(it.id, it.orgId) }.toSet()
        return master.elements[Kind.ROOM].orEmpty().filter { it.id in used }
    }

    data class FreeRoom(val room: Element, val until: LocalTime?, val freedBy: String?)

    fun freeRooms(all: List<Period>, rooms: List<Element>, day: LocalDate, unit: Slot, units: List<Slot>): List<FreeRoom> {
        val dayPs = all.filter { it.start.toLocalDate() == day }
        fun busyIn(u: Slot) = dayPs.filter { !it.cancelled && overlaps(it, u) }.flatMap { it.of(Kind.ROOM) }.map { it.id }.toSet()
        val busy = busyIn(unit)
        val cancelled = dayPs.filter { it.cancelled && overlaps(it, unit) }.flatMap { it.of(Kind.ROOM) }.flatMap { listOf(it.id, it.orgId) }.toSet()
        val movedAway = dayPs.filter { !it.cancelled && overlaps(it, unit) }.flatMap { it.of(Kind.ROOM) }.filter { it.changed }.map { it.orgId }.toSet()
        val later = units.filter { it.start >= unit.end }
        return rooms.filter { it.id !in busy }.map { r ->
            val until = later.firstOrNull { u -> r.id in busyIn(u) }?.start
            FreeRoom(r, until, when (r.id) { in cancelled -> "durch Entfall frei"; in movedAway -> "Unterricht verlegt"; else -> null })
        }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.room.name })
    }

    /** every lesson in [unit], once (a lesson of several classes appears in each class plan) */
    fun lessonsAt(all: List<Period>, day: LocalDate, unit: Slot): List<Period> =
        all.filter { it.start.toLocalDate() == day && overlaps(it, unit) }.distinctBy { it.id }

    // ---- schools that show only today and tomorrow: the rest of the week, as it will probably be

    /** school days of the week after [today] and after the last day the school shows: the days a projection fills */
    fun openDays(week: List<Period>, monday: LocalDate, today: LocalDate, master: Master?): List<LocalDate> {
        val shownUntil = week.maxOfOrNull { it.start.toLocalDate() }
        return (0..5L).map { monday.plusDays(it) }
            .filter { d -> d.isAfter(today) && (shownUntil == null || d.isAfter(shownUntil)) && master?.holiday(d) == null }
    }

    /**
     * Fills each open day with the same weekday of the nearest earlier week that has lessons on it ([earlier]: weeks back to
     * periods, nearest first), as the regular plan: a cancelled lesson or an exam counts as the normal lesson, a substitute or
     * room swap goes back to the original, extra and moved lessons are left out.
     * shortcut: A/B-week schools get the nearest week's pattern; compare two weeks back once such a school reports it.
     */
    fun project(week: List<Period>, monday: LocalDate, earlier: List<Pair<Long, List<Period>?>>, today: LocalDate, master: Master?): List<Period> {
        val add = openDays(week, monday, today, master).flatMap { d ->
            earlier.firstNotNullOfOrNull { (back, ps) ->
                ps?.filter { it.start.toLocalDate() == d.minusWeeks(back) }?.takeIf { it.isNotEmpty() }?.let { back to it }
            }?.let { (back, src) -> src.mapNotNull { regular(it, back) } }.orEmpty()
        }
        return if (add.isEmpty()) week else week + add
    }

    private fun regular(p: Period, weeks: Long): Period? {
        if (p.additional || ("IRREGULAR" in p.states && p.refs.none { it.changed })) return null
        return p.copy(
            id = -p.id, start = p.start.plusWeeks(weeks), end = p.end.plusWeeks(weeks),
            refs = p.refs.map { if (it.changed) Ref(it.kind, it.orgId, 0) else it },
            states = setOf("REGULAR"), substText = "", info = "", exam = null, parts = emptyList(), projected = true,
        )
    }
}
