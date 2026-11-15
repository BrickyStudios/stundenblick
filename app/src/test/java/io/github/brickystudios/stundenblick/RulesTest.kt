package io.github.brickystudios.stundenblick

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class RulesTest {
    private val monday = LocalDate.of(2026, 11, 16)
    private val master = Parse.master(Demo.master())
    private fun q2() = Parse.periods(Demo.timetable(Kind.CLASS, 3, monday))

    // RFC 6238 appendix B, SHA1 secret "12345678901234567890", last 6 of the 8 digits
    @Test fun totpMatchesRfcVectors() {
        val secret = "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ"
        assertEquals(287082L, Totp.code(secret, 59_000))
        assertEquals(81804L, Totp.code(secret, 1_111_111_109_000))
        assertEquals(5924L, Totp.code(secret, 1_234_567_890_000))
        assertEquals("lower case works", 287082L, Totp.code(secret.lowercase(), 59_000))
    }

    @Test fun totpWithoutSecretIsZero() {
        assertEquals(0L, Totp.code(null, 59_000))
        assertEquals(0L, Totp.code("", 59_000))
        assertEquals("not base32", 0L, Totp.code("not-valid!", 59_000))
    }

    @Test fun setSchoolLinkIsReadAndChecked() {
        val ok = Parse.setSchool("untis://setschool?url=thalia.webuntis.com&school=gym%20test&user=max.m&key=ABCDEF234567")
        assertEquals(SetSchool("thalia.webuntis.com", "gym test", "max.m", "ABCDEF234567"), ok)
        assertEquals("https form", "x.webuntis.com", Parse.setSchool("untis://setschool?url=https://x.webuntis.com/WebUntis&school=s")?.server)
        assertNull("foreign host", Parse.setSchool("untis://setschool?url=evil.example.com&school=s&user=u&key=k"))
        assertNull("look-alike host", Parse.setSchool("untis://setschool?url=webuntis.com.evil.io&school=s"))
        assertNull("bare domain", Parse.setSchool("untis://setschool?url=.webuntis.com&school=s"))
        assertNull("no school", Parse.setSchool("untis://setschool?url=thalia.webuntis.com&user=u"))
        assertNull("other scheme", Parse.setSchool("https://setschool?url=thalia.webuntis.com&school=s"))
        assertNull("garbage", Parse.setSchool("hello"))
    }

    @Test fun everyKnownErrorHasItsOwnSentence() {
        val texts = Errors.known.keys.map { Errors.explain(UntisException(it, "x")) }
        assertEquals("one sentence per code, except the two 'plan gone' codes", Errors.known.size - 1, texts.toSet().size)
        assertTrue(Errors.explain(UntisException(Errors.TWO_FACTOR, "")).contains("QR"))
        assertTrue(Errors.explain(UntisException(-8509, "")).contains("nicht frei"))
        assertTrue("unknown code keeps the number", Errors.explain(UntisException(-1234, "boom")).contains("-1234"))
        assertTrue(Errors.explain(java.net.UnknownHostException("h")).contains("letzte Stand"))
    }

    @Test fun masterHasElementsUnitsAndNames() {
        assertEquals(3, master.elements[Kind.CLASS]!!.size)
        assertEquals(8, master.units.size)
        assertEquals("Anna Albers", master.find(Kind.TEACHER, 11)!!.longName)
        assertEquals(3, master.unitNumber(java.time.LocalTime.of(9, 55)))
        assertNull(master.unitNumber(java.time.LocalTime.of(9, 56)))
    }

    @Test fun periodsCarryStates() {
        val ps = q2()
        assertTrue(ps.isNotEmpty())
        assertEquals("sorted by start", ps.sortedBy { it.start }, ps)
        val tue3 = ps.single { it.start == monday.plusDays(1).atTime(9, 55) }
        assertTrue(tue3.cancelled)
        assertFalse("cancelled is not also irregular", tue3.irregular)
        val sub = ps.single { it.start == monday.plusDays(2).atTime(9, 55) }
        assertTrue(sub.irregular)
        assertEquals(14L, sub.of(Kind.TEACHER).single().id)
        assertEquals(12L, sub.of(Kind.TEACHER).single().orgId)
        assertTrue(ps.filter { it.start.toLocalDate() == monday.plusDays(4) && it.start.toLocalTime() < java.time.LocalTime.of(9, 40) }.let { it.size == 2 && it.all { p -> p.isExam } })
        assertTrue(ps.any { it.additional && it.irregular })
        assertEquals("parallel courses on Monday 5th lesson", 2, ps.count { it.start == monday.atTime(11, 50) })
    }

    @Test fun ownPlanFromUserData() {
        assertEquals(Element(Kind.CLASS, 3, "Mein Plan", "Q2"), Parse.own(Demo.userData().getJSONObject("userData")))
        val anon = JSONObject().put("elemType", JSONObject.NULL).put("elemId", -1).put("children", JSONArray())
        assertNull("F3: no own plan", Parse.own(anon))
        val parent = JSONObject().put("elemType", "LEGAL_GUARDIAN").put("elemId", 0)
            .put("children", JSONArray().put(JSONObject().put("id", 77).put("displayName", "Kind")))
        assertEquals(Element(Kind.STUDENT, 77, "Mein Plan", "Kind"), Parse.own(parent))
    }

    @Test fun filterHidesSubjects() {
        val ps = q2()
        val bio = 24L
        assertTrue(ps.any { it.subjectId == bio })
        assertFalse(Filter.apply(ps, setOf(bio)).any { it.subjectId == bio })
        assertEquals(ps, Filter.apply(ps, emptySet()))
        assertTrue("all hidden", Filter.apply(ps, master.elements[Kind.SUBJECT]!!.map { it.id }.toSet()).isEmpty())
    }

    @Test fun changesNotifyOnceAndNotOnFirstRun() {
        val now = q2()
        assertTrue("first run fills the cache only", Changes.between(null, now, master, emptySet()).isEmpty())
        assertTrue("same data twice", Changes.between(now, now, master, emptySet()).isEmpty())
        // before: the Tuesday lesson was normal; now it is cancelled
        val before = now.map { if (it.cancelled) it.copy(states = setOf("REGULAR")) else it }
        val msgs = Changes.between(before, now, master, emptySet())
        assertEquals(1, msgs.size)
        assertEquals("Di. 17.11., 3. Std.: Ge entfällt", msgs.single())
        assertTrue("hidden subject stays quiet", Changes.between(before, now, master, setOf(26L)).isEmpty())
        // a new special period that was not there before
        val withoutExtra = now.filterNot { it.additional }
        assertTrue(Changes.between(withoutExtra, now, master, emptySet()).single().contains("Zusatzstunde"))
    }

    @Test fun describeNamesSubstituteAndRoom() {
        val ps = q2()
        val sub = ps.single { it.start == monday.plusDays(2).atTime(9, 55) }
        assertTrue(Changes.describe(sub, master).endsWith("M: Vertretung DOM"))
        val room = ps.single { it.start == monday.plusDays(3).atTime(8, 0) }
        assertTrue(Changes.describe(room, master).endsWith("D: Raum Aula statt A101"))
    }

    @Test fun diagnosticReportHasNoSecrets() {
        val steps = listOf(Diag.Step("Benutzerdaten", true, "Rolle STUDENT"), Diag.Step("REST", false, Diag.detail(UntisException(-8520, "eyJsecrettoken"))))
        val text = Diag.report("0.9.0", "15", false, steps)
        assertTrue(text.contains("2. REST: Fehler (Untis -8520)"))
        assertFalse(text.contains("eyJ"))
        assertNotNull(text.lines().first().contains("mit Konto"))
    }

    @Test fun schoolSearchDropsForeignServers() {
        val r = JSONObject().put("schools", JSONArray()
            .put(JSONObject().put("displayName", "A").put("loginName", "a").put("server", "thalia.webuntis.com"))
            .put(JSONObject().put("displayName", "B").put("loginName", "b").put("server", "evil.example.com")))
        assertEquals(listOf("a"), Parse.schools(r).map { it.login })
    }

    @Test fun parallelCoursesShareTheWidth() {
        val mon = q2().filter { it.start.toLocalDate() == monday }
        val placed = lanes(mon)
        assertEquals(mon.size, placed.size)
        val fifth = placed.filter { it.period.start == monday.atTime(11, 50) }
        assertEquals(listOf(2, 2), fifth.map { it.lanes })
        assertEquals(setOf(0, 1), fifth.map { it.lane }.toSet())
        assertTrue("single lessons keep the full width", placed.filter { it.period.start == monday.atTime(8, 0) }.all { it.lanes == 1 })
    }

    @Test fun releaseVersionsCompare() {
        assertTrue(Releases.isNewer("v1.0.0", "0.9.0"))
        assertFalse(Releases.isNewer("v0.9.0", "0.9.0"))
        assertTrue(Releases.isNewer("0.10.0", "0.9.3"))
    }
}
