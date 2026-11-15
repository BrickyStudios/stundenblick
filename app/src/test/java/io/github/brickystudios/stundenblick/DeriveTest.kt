package io.github.brickystudios.stundenblick

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class DeriveTest {
    private val monday = LocalDate.of(2026, 11, 16)
    private val master = Parse.master(Demo.master())
    private val q2 = Parse.periods(Demo.timetable(Kind.CLASS, 3, monday))
    private fun at(day: Long, h: Int, m: Int) = monday.plusDays(day).atTime(h, m)

    @Test fun doubleLessonsMergeOnlyWhenSeamless() {
        val mon = Derive.blocks(q2.filter { it.start.toLocalDate() == monday })
        val math = mon.first { it.start == at(0, 8, 0) }
        assertEquals("M 1st+2nd lesson, 5 min apart", at(0, 9, 35), math.end)
        assertEquals(2, math.ids.size)
        assertEquals("1.–2. Std.", Derive.unitLabel(math, master))
        // Wednesday: M in 3rd lesson substituted, D 4th: different courses stay apart
        val wed = Derive.blocks(q2.filter { it.start.toLocalDate() == monday.plusDays(2) })
        assertTrue(wed.any { it.start == at(2, 9, 55) && it.end == at(2, 10, 40) })
        // Tuesday Ge (cancelled) and PGW are different lessons; Ge cancelled stays alone
        val tue = Derive.blocks(q2.filter { it.start.toLocalDate() == monday.plusDays(1) })
        assertTrue(tue.single { it.start == at(1, 9, 55) }.cancelled)
    }

    @Test fun differentStatusDoesNotMerge() {
        // Friday 1st+2nd are both exams of the same course: one block; a cancelled twin would stay apart
        val fri = Derive.blocks(q2.filter { it.start.toLocalDate() == monday.plusDays(4) })
        assertEquals(at(4, 9, 35), fri.first { it.start == at(4, 8, 0) }.end)
        val split = q2.filter { it.start.toLocalDate() == monday.plusDays(4) }.mapIndexed { i, p -> if (i == 1) p.copy(states = setOf("CANCELLED")) else p }
        assertTrue(Derive.blocks(split).count { it.start.toLocalDate() == monday.plusDays(4) && it.start.toLocalTime() < LocalTime.of(10, 0) } >= 2)
    }

    @Test fun gapsBecomeBreaksAndFreePeriods() {
        fun gaps(d: Long) = Derive.dayRows(Derive.blocks(q2.filter { it.start.toLocalDate() == monday.plusDays(d) }), master)
        val mon = gaps(0).filterIsInstance<Derive.Row.Gap>().map { it.label }
        assertTrue("$mon", "20 min Pause" in mon)
        // Tuesday: Ge (3rd) is cancelled; its time shows once as a free period after the cancelled row, then 5th+6th are empty
        val tue = gaps(1)
        val gapRows = tue.filterIsInstance<Derive.Row.Gap>()
        assertEquals("one row per gap, no doubles", gapRows.size, gapRows.map { it.start to it.end }.toSet().size)
        assertEquals(listOf("Freistunde", "2 Freistunden"), gapRows.map { it.label })
        val cancelledAt = tue.indexOfFirst { it is Derive.Row.Lesson && it.period.cancelled }
        assertTrue("the free period follows the cancelled row", tue[cancelledAt + 1] is Derive.Row.Gap)
        assertTrue(gaps(2).first() is Derive.Row.Lesson)
    }

    @Test fun weekCountCountsBlocksOnce() {
        val c = Derive.weekCount(Derive.blocks(q2))
        assertEquals(1, c.cancelled)
        assertEquals(1, c.substituted)
        assertEquals(1, c.roomChanged)
        assertEquals("the double exam counts once", 1, c.exams)
    }

    @Test fun nowAndNext() {
        val blocks = Derive.blocks(q2)
        val inMath = Derive.now(blocks, at(0, 8, 30))
        assertEquals(at(0, 9, 35), inMath.current.single().end)
        assertEquals(65L, inMath.minutesLeft(at(0, 8, 30)))
        val inBreak = Derive.now(blocks, at(0, 9, 40))
        assertTrue(inBreak.current.isEmpty())
        assertEquals(at(0, 9, 55), inBreak.next.first().start)
        val after = Derive.now(blocks, at(0, 18, 0))
        assertTrue(after.current.isEmpty() && after.next.isEmpty())
        // Tuesday 3rd lesson is cancelled: next after the 2nd lesson skips it
        assertEquals(at(1, 10, 45), Derive.now(blocks, at(1, 9, 40)).next.first().start)
    }

    @Test fun nextSchoolDaySkipsWeekendAndHolidays() {
        assertEquals(monday, Derive.nextSchoolDay(monday.minusDays(3), master, false))
        assertEquals("Saturday school", monday.minusDays(2), Derive.nextSchoolDay(monday.minusDays(3), master, true))
        val md = JSONObject().put("holidays", JSONArray().put(JSONObject().put("name", "H").put("startDate", monday.toString()).put("endDate", monday.plusDays(4).toString())))
        assertEquals(monday.plusWeeks(1), Derive.nextSchoolDay(monday.minusDays(3), Parse.master(md), false))
    }

    @Test fun holidayCountdown() {
        val md = JSONObject().put("holidays", JSONArray().put(JSONObject().put("longName", "Weihnachtsferien").put("startDate", "2026-12-23").put("endDate", "2027-01-06")))
        val h = Derive.nextHoliday(monday, Parse.master(md))!!
        assertEquals("Weihnachtsferien", h.name)
        assertTrue(!h.running)
        assertTrue(Derive.nextHoliday(LocalDate.of(2026, 12, 28), Parse.master(md))!!.running)
        assertNull(Derive.nextHoliday(LocalDate.of(2027, 2, 1), Parse.master(md)))
    }

    @Test fun roomState() {
        val a101 = Parse.periods(Demo.timetable(Kind.ROOM, 31, monday))
        val busy = Derive.roomState(a101, at(0, 10, 0), master)
        assertTrue(busy.busy != null)
        val free = Derive.roomState(a101, at(0, 14, 10), master)
        assertNull(free.busy)
    }

    private fun school(): List<Period> = listOf(1L, 2L, 3L).flatMap { Parse.periods(Demo.timetable(Kind.CLASS, it, monday)) }

    @Test fun slotIndexPicksRunningOrNext() {
        assertEquals(0, Derive.slotIndex(master.units, LocalTime.of(7, 0)))
        assertEquals(2, Derive.slotIndex(master.units, LocalTime.of(9, 40)))
        assertEquals(master.units.size - 1, Derive.slotIndex(master.units, LocalTime.of(18, 0)))
    }

    @Test fun freeRoomsFromAllClassPlans() {
        val all = school()
        val rooms = Derive.teachingRooms(all, master)
        assertTrue("Aula is used once a week", rooms.any { it.name == "Aula" })
        val third = master.units[2]
        // Tuesday 3rd lesson: Q2's Ge in B205 is cancelled, so B205 is free by cancellation
        val tue = Derive.freeRooms(all, rooms, monday.plusDays(1), third, master.units)
        assertEquals("durch Entfall frei", tue.single { it.room.name == "B205" }.freedBy)
        // Thursday 1st: Q2's German moved from A101 to the Aula, and 7a's maths in A101 is the only other user
        val thu = Derive.freeRooms(all, rooms, monday.plusDays(3), master.units[0], master.units)
        assertTrue(thu.none { it.room.name == "Aula" })
        // a room busy later today says until when
        val mon = Derive.freeRooms(all, rooms, monday, master.units[0], master.units)
        val nw1 = mon.single { it.room.name == "NW1" }
        assertEquals(LocalTime.of(9, 55), nw1.until)
    }

    @Test fun lessonsAtCountsSharedLessonsOnce() {
        val l = Derive.lessonsAt(school(), monday, master.units[0])
        assertEquals(l.size, l.map { it.id }.toSet().size)
        assertTrue(l.size >= 3)
    }
}
