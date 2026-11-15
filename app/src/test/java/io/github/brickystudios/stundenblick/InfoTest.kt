package io.github.brickystudios.stundenblick

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class InfoTest {
    // synthetic, in the field layout the live answers of 15.11.2026 showed
    @Test fun examsInLiveLayout() {
        val r = JSONObject().put("type", "STUDENT").put("id", 1).put("exams", JSONArray()
            .put(JSONObject().put("id", 2).put("examType", "Schularbeit").put("startDateTime", "2026-11-20T08:50Z").put("endDateTime", "2026-11-20T09:40Z")
                .put("departmentId", 0).put("subjectId", 22).put("klasseIds", JSONArray().put(3)).put("roomIds", JSONArray().put(33)).put("teacherIds", JSONArray().put(12))
                .put("invigilators", JSONArray()).put("name", "M-2").put("text", ""))
            .put(JSONObject().put("id", 1).put("examType", "Test").put("startDateTime", "2026-11-18T08:00Z").put("endDateTime", "2026-11-18T08:45Z").put("subjectId", 0)))
        val e = InfoParse.exams(r)
        assertEquals("sorted by start", listOf(1L, 2L), e.map { it.id })
        assertEquals(22L, e[1].subjectId)
        assertEquals(null, e[0].subjectId)
        assertEquals(listOf(33L), e[1].roomIds)
    }

    @Test fun homeworkFindsItsSubjectThroughTheLesson() {
        val h = InfoParse.homework(Demo.homework())
        assertEquals(2, h.size)
        assertEquals(22L, h.first().subjectId)
        assertTrue(h.first().due!! <= h.last().due!!)
        assertEquals("", h.first().remark)
        assertEquals("auf Papier abgeben", h.last().remark)
    }

    @Test fun absencesNewestFirst() {
        val a = InfoParse.absences(Demo.absences())
        assertTrue(a.first().start > a.last().start)
        assertEquals(1, a.count { !it.excused })
    }

    @Test fun htmlBecomesText() {
        assertEquals("Am Freitag ist Wandertag.\nTreffpunkt 8:00 Uhr & Co. <b>",
            InfoParse.html("<div><p>Am Freitag ist <b>Wandertag</b>.<br/>Treffpunkt 8:00&nbsp;Uhr &amp; Co. &lt;b&gt;</p></div>"))
        assertEquals("Grüße", InfoParse.html("Gr&#252;&#223;e"))
        assertFalse(InfoParse.messages(Demo.messagesOfDay()).single().text.contains("<"))
    }

    @Test fun fetchOncePerDayMessageEveryTwoHours() {
        val today = LocalDate.of(2026, 11, 16)
        assertTrue(Fetch.daily(null, today, false))
        assertFalse(Fetch.daily(today, today, false))
        assertTrue(Fetch.daily(today.minusDays(1), today, false))
        assertTrue("pull to refresh", Fetch.daily(today, today, true))
        assertFalse(Fetch.message(1_000_000, 1_000_000 + 3600_000, false))
        assertTrue(Fetch.message(1_000_000, 1_000_000 + 3 * 3600_000, false))
    }

    @Test fun officeHoursKeepContacts() {
        val o = InfoParse.officeHours(Demo.officeHours())
        assertEquals("albers@example.com", o.first().email)
        assertEquals(null, o.last().email)
    }
}
