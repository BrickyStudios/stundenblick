package io.github.brickystudios.stundenblick

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * K11: an invented school in Hamburg. It answers in Untis' own JSON shape, so the demo runs through
 * the same parser as a real school. Every name here is made up.
 */
object Demo {
    val profile = Profile(Profile.DEMO, "demo", "Gymnasium Elbblick (Demo, Hamburg)", "demo", null)

    private val times = listOf("08:00" to "08:45", "08:50" to "09:35", "09:55" to "10:40", "10:45" to "11:30", "11:50" to "12:35", "12:40" to "13:25", "14:00" to "14:45", "14:50" to "15:35")
    private val classes = listOf(1L to "7a", 2L to "9b", 3L to "Q2")
    private val teachers = listOf(
        11L to ("ALB" to ("Anna" to "Albers")), 12L to ("BRA" to ("Ben" to "Brandt")), 13L to ("CEL" to ("Clara" to "Celik")),
        14L to ("DOM" to ("David" to "Domke")), 15L to ("ERT" to ("Eva" to "Ertel")), 16L to ("FRI" to ("Finn" to "Friese")),
        17L to ("GÖK" to ("Gül" to "Gökçe")), 18L to ("HAN" to ("Hanna" to "Hansen")),
    )
    private val subjects = listOf(
        21L to Triple("D", "Deutsch", "#ff7b7f"), 22L to Triple("M", "Mathematik", "#6aa0ff"), 23L to Triple("E", "Englisch", "#ffbe5c"),
        24L to Triple("Bio", "Biologie", "#74c97a"), 25L to Triple("Ph", "Physik", "#b392ff"), 26L to Triple("Ge", "Geschichte", "#c9a27e"),
        27L to Triple("Mu", "Musik", "#ff8fc7"), 28L to Triple("Sp", "Sport", "#6fd0f0"), 29L to Triple("Inf", "Informatik", "#a8b0bd"),
        30L to Triple("PGW", "Politik/Gesellschaft/Wirtschaft", "#e8d35a"),
    )
    private val rooms = listOf(31L to "A101", 32L to "A102", 33L to "B204", 34L to "B205", 35L to "NW1", 36L to "NW2", 37L to "Halle", 38L to "Aula")

    /** class, weekday 1..5, lesson 1..8, subject, teacher, room; two rows in one slot = parallel courses */
    private val plan = buildList {
        fun add(k: Long, d: Int, u: Int, s: Long, t: Long, r: Long) = add(listOf(k, d.toLong(), u.toLong(), s, t, r))
        // Q2: profile courses run in parallel, so the course filter has something to do
        for ((d, u, s, t, r) in listOf(
            listOf(1, 1, 22, 12, 33), listOf(1, 2, 22, 12, 33), listOf(1, 3, 21, 11, 31), listOf(1, 4, 21, 11, 31), listOf(1, 5, 25, 14, 35), listOf(1, 6, 25, 14, 35),
            listOf(2, 1, 23, 13, 32), listOf(2, 2, 23, 13, 32), listOf(2, 3, 26, 15, 34), listOf(2, 4, 30, 15, 34), listOf(2, 7, 28, 16, 37), listOf(2, 8, 28, 16, 37),
            listOf(3, 1, 24, 18, 36), listOf(3, 2, 24, 18, 36), listOf(3, 3, 22, 12, 33), listOf(3, 4, 21, 11, 31), listOf(3, 5, 29, 17, 34),
            listOf(4, 1, 21, 11, 31), listOf(4, 2, 23, 13, 32), listOf(4, 3, 25, 14, 35), listOf(4, 4, 25, 14, 35), listOf(4, 5, 27, 18, 38), listOf(4, 6, 27, 18, 38),
            listOf(5, 1, 22, 12, 33), listOf(5, 2, 22, 12, 33), listOf(5, 3, 26, 15, 34), listOf(5, 4, 23, 13, 32),
        )) add(3, d, u, s.toLong(), t.toLong(), r.toLong())
        // parallel to Physik: Biologie as the other profile course
        for ((d, u) in listOf(1 to 5, 1 to 6, 4 to 3, 4 to 4)) add(3, d, u, 24, 18, 36)
        for ((d, u, s, t, r) in listOf(
            listOf(1, 1, 21, 15, 31), listOf(1, 2, 23, 13, 31), listOf(1, 3, 22, 14, 31), listOf(1, 4, 28, 16, 37), listOf(2, 1, 24, 18, 36), listOf(2, 2, 22, 14, 31),
            listOf(2, 3, 27, 18, 38), listOf(3, 1, 23, 13, 31), listOf(3, 2, 21, 15, 31), listOf(3, 3, 26, 15, 31), listOf(4, 1, 22, 14, 31), listOf(4, 2, 29, 17, 34),
            listOf(4, 3, 21, 15, 31), listOf(5, 1, 28, 16, 37), listOf(5, 2, 28, 16, 37), listOf(5, 3, 23, 13, 31),
        )) add(1, d, u, s.toLong(), t.toLong(), r.toLong())
        for ((d, u, s, t, r) in listOf(
            listOf(1, 1, 23, 13, 32), listOf(1, 2, 21, 11, 32), listOf(1, 3, 25, 14, 35), listOf(1, 4, 22, 12, 32), listOf(2, 1, 22, 12, 32), listOf(2, 2, 26, 15, 32),
            listOf(2, 5, 29, 17, 34), listOf(2, 6, 29, 17, 34), listOf(3, 1, 21, 11, 32), listOf(3, 2, 28, 16, 37), listOf(3, 3, 28, 16, 37), listOf(4, 1, 24, 18, 36),
            listOf(4, 2, 22, 12, 32), listOf(5, 1, 30, 15, 32), listOf(5, 2, 23, 13, 32), listOf(5, 3, 27, 18, 38),
        )) add(2, d, u, s.toLong(), t.toLong(), r.toLong())
    }

    private operator fun <T> List<T>.component6() = this[5]

    fun master(): JSONObject = JSONObject()
        .put("klassen", JSONArray(classes.map { (id, n) -> JSONObject().put("id", id).put("name", n).put("longName", "Klasse $n").put("active", true) }))
        .put("teachers", JSONArray(teachers.map { (id, v) -> JSONObject().put("id", id).put("name", v.first).put("firstName", v.second.first).put("lastName", v.second.second).put("active", true) }))
        .put("subjects", JSONArray(subjects.map { (id, v) -> JSONObject().put("id", id).put("name", v.first).put("longName", v.second).put("backColor", v.third).put("active", true) }))
        .put("rooms", JSONArray(rooms.map { (id, n) -> JSONObject().put("id", id).put("name", n).put("longName", "Raum $n").put("active", true) }))
        .put("timeGrid", JSONObject().put("days", JSONArray((1..5).map { JSONObject().put("units", JSONArray(times.map { (a, b) -> JSONObject().put("startTime", "T$a").put("endTime", "T$b") })) })))
        .put("holidays", JSONArray().put(JSONObject().put("name", "Weihn.").put("longName", "Weihnachtsferien")
            .put("startDate", holidayStart().toString()).put("endDate", holidayStart().plusDays(15).toString())))
        .put("schoolyears", JSONArray().put(JSONObject().put("name", "Demo-Schuljahr")
            .put("startDate", LocalDate.now().minusMonths(3).toString()).put("endDate", LocalDate.now().plusMonths(8).toString())))

    /** five weeks ahead, on a Monday, so the countdown always has something to count */
    private fun holidayStart(): LocalDate = Parse.monday(LocalDate.now()).plusWeeks(5)

    fun userData(): JSONObject = JSONObject().put("masterData", master())
        .put("userData", JSONObject().put("elemType", "CLASS").put("elemId", 3).put("displayName", "Q2")
            .put("rights", JSONArray(listOf("R_MY_ABSENCES", "R_OFFICEHOURS"))).put("klassenIds", JSONArray().put(3)))

    /** The same week rhythm every week, with deviations tied to weekdays so the current week always shows each kind. */
    fun timetable(kind: Kind, id: Long, monday: LocalDate): JSONObject {
        val periods = JSONArray()
        plan.forEachIndexed { i, row ->
            val (k, d, u, s, t, r) = row
            val match = when (kind) {
                Kind.CLASS, Kind.STUDENT -> k == id
                Kind.TEACHER -> t == id
                Kind.SUBJECT -> s == id
                Kind.ROOM -> r == id
            }
            if (!match) return@forEachIndexed
            val day = monday.plusDays(d - 1)
            if (day.isAfter(LocalDate.now().plusDays(7))) return@forEachIndexed // like schools that show only the next days
            val (a, b) = times[(u - 1).toInt()]
            var teacher = t to t
            var room = r to r
            val states = mutableListOf("REGULAR")
            var exam: JSONObject? = null
            var subst = ""
            when {
                k == 3L && d == 2L && u == 3L -> states[0] = "CANCELLED"
                k == 3L && d == 3L && u == 3L -> { teacher = 14L to t; states[0] = "IRREGULAR"; subst = "Vertretung" }
                k == 3L && d == 4L && u == 1L -> { room = 38L to r; states[0] = "IRREGULAR" }
                k == 3L && d == 5L && (u == 1L || u == 2L) -> { states[0] = "EXAM"; exam = JSONObject().put("examtype", "Klausur").put("name", "M-LK 2").put("text", "") }
                k == 1L && d == 1L && u == 4L -> states[0] = "CANCELLED"
            }
            periods.put(
                JSONObject().put("id", day.toEpochDay() * 10_000 + i).put("lessonId", k * 10_000 + s * 100 + t)
                    .put("startDateTime", "${day}T$a" + "Z").put("endDateTime", "${day}T$b" + "Z")
                    .put("backColor", "#f49f25") // like real schools: one lesson colour for all, the subject colour is in the master data
                    .put("text", JSONObject().put("lesson", "").put("substitution", subst).put("info", if (exam != null) "Hilfsmittel: Taschenrechner" else ""))
                    .put("elements", JSONArray(listOf(
                        JSONObject().put("type", "CLASS").put("id", k).put("orgId", k),
                        JSONObject().put("type", "TEACHER").put("id", teacher.first).put("orgId", teacher.second),
                        JSONObject().put("type", "SUBJECT").put("id", s).put("orgId", s),
                        JSONObject().put("type", "ROOM").put("id", room.first).put("orgId", room.second),
                    )))
                    .put("is", JSONArray(states)).put("exam", exam ?: JSONObject.NULL),
            )
        }
        // an extra lesson on Monday afternoon for Q2
        if (((kind == Kind.CLASS && id == 3L) || (kind == Kind.TEACHER && id == 12L)) && !monday.isAfter(LocalDate.now().plusDays(7))) {
            val (a, b) = times[6]
            periods.put(
                JSONObject().put("id", monday.toEpochDay() * 10_000 + 9_999).put("lessonId", 999)
                    .put("startDateTime", "${monday}T$a" + "Z").put("endDateTime", "${monday}T$b" + "Z").put("backColor", "#9ec5fe")
                    .put("text", JSONObject().put("lesson", "").put("substitution", "Zusatzstunde: Klausurvorbereitung").put("info", ""))
                    .put("elements", JSONArray(listOf(
                        JSONObject().put("type", "CLASS").put("id", 3).put("orgId", 3),
                        JSONObject().put("type", "TEACHER").put("id", 12).put("orgId", 12),
                        JSONObject().put("type", "SUBJECT").put("id", 22).put("orgId", 22),
                        JSONObject().put("type", "ROOM").put("id", 33).put("orgId", 33),
                    )))
                    .put("is", JSONArray(listOf("ADDITIONAL"))).put("exam", JSONObject.NULL),
            )
        }
        return JSONObject().put("timetable", JSONObject().put("periods", periods))
    }

    fun periodData(ids: Collection<Long>): JSONObject {
        val by = JSONObject()
        ids.forEach { id ->
            by.put(id.toString(), JSONObject().put("ttId", id)
                .put("topic", JSONObject().put("text", if (id % 2 == 0L) "Wiederholung: Ableitungsregeln" else "Gedichtanalyse: Expressionismus"))
                .put("homeWorks", JSONArray(listOf(JSONObject().put("text", "Aufgaben 4–7 auf S. 112"))))
                .put("text", JSONObject().put("lesson", "").put("info", "")))
        }
        return JSONObject().put("dataByTTId", by)
    }


    // ---- Info Center answers in Untis' shape; dates relative to today so the demo always has something ahead
    private fun at(days: Long, time: String) = "${weekday(LocalDate.now().plusDays(days))}T$time" + "Z"

    /** demo dates never land on a weekend */
    private fun weekday(d: LocalDate): LocalDate = when (d.dayOfWeek) {
        java.time.DayOfWeek.SATURDAY -> d.plusDays(2)
        java.time.DayOfWeek.SUNDAY -> d.plusDays(1)
        else -> d
    }

    fun exams(): JSONObject = JSONObject().put("type", "CLASS").put("id", 3).put("exams", JSONArray(listOf(
        Triple(3L, 22L, "Klausur Mathematik LK") to "08:00",
        Triple(12L, 23L, "Klausur Englisch") to "09:55",
        Triple(30L, 24L, "Klausur Biologie") to "08:00",
    ).mapIndexed { i, (e, time) ->
        JSONObject().put("id", 9100 + i).put("examType", "Klausur").put("name", e.third).put("text", "")
            .put("startDateTime", at(e.first, time)).put("endDateTime", at(e.first, if (time == "08:00") "09:35" else "11:30"))
            .put("subjectId", e.second).put("roomIds", JSONArray().put(33)).put("teacherIds", JSONArray().put(12)).put("klasseIds", JSONArray().put(3))
    }))

    fun homework(): JSONObject = JSONObject()
        .put("homeWorks", JSONArray()
            .put(JSONObject().put("id", 9201).put("lessonId", 32212).put("startDate", LocalDate.now().minusDays(2).toString())
                .put("endDate", LocalDate.now().plusDays(1).toString()).put("text", "Aufgaben 4–7 auf S. 112").put("remark", JSONObject.NULL).put("completed", false))
            .put(JSONObject().put("id", 9202).put("lessonId", 32111).put("startDate", LocalDate.now().minusDays(1).toString())
                .put("endDate", LocalDate.now().plusDays(6).toString()).put("text", "Gedicht von Georg Heym analysieren, eine Seite").put("remark", "auf Papier abgeben").put("completed", false)))
        .put("lessonsById", JSONObject()
            .put("32212", JSONObject().put("id", 32212).put("subjectId", 22))
            .put("32111", JSONObject().put("id", 32111).put("subjectId", 21)))

    fun absences(): JSONObject = JSONObject().put("absences", JSONArray()
        .put(JSONObject().put("id", 9301).put("startDateTime", at(-9, "08:00")).put("endDateTime", at(-9, "13:25"))
            .put("absenceReason", "krank").put("excused", true).put("text", ""))
        .put(JSONObject().put("id", 9302).put("startDateTime", at(-2, "14:00")).put("endDateTime", at(-2, "15:35"))
            .put("absenceReason", "Arzttermin").put("excused", false).put("text", "Entschuldigung folgt")))

    fun messagesOfDay(): JSONObject = JSONObject().put("messages", JSONArray().put(JSONObject().put("id", 9401).put("subject", "Wandertag am Freitag")
        .put("body", "<p>Am Freitag ist für alle Klassen <b>Wandertag</b>.<br/>Treffpunkt 8:00 Uhr am Haupteingang.</p>").put("attachments", JSONArray())))

    fun officeHours(): JSONObject = JSONObject().put("officeHours", JSONArray()
        .put(JSONObject().put("id", 9501).put("startDateTime", at(1, "10:45")).put("endDateTime", at(1, "11:30"))
            .put("displayNameTeacher", "Albers, Anna (ALB)").put("displayNameRooms", "A101").put("email", "albers@example.com").put("phone", JSONObject.NULL))
        .put(JSONObject().put("id", 9502).put("startDateTime", at(2, "12:40")).put("endDateTime", at(2, "13:25"))
            .put("displayNameTeacher", "Brandt, Ben (BRA)").put("displayNameRooms", "B204").put("email", JSONObject.NULL).put("phone", JSONObject.NULL)))
}
