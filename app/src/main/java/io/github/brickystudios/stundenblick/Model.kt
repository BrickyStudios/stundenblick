package io.github.brickystudios.stundenblick

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

// Everything here is pure (no Android types), so the JVM tests cover the parsing and the rules.

enum class Kind(val label: String) { CLASS("Klasse"), TEACHER("Lehrkraft"), SUBJECT("Fach"), ROOM("Raum"), STUDENT("Schüler:in") }

data class Element(val kind: Kind, val id: Long, val name: String, val longName: String = name, val color: String? = null) {
    val key get() = "${kind.name}:$id"
    /** "M (Mathematik)" reads better than "M" twice */
    val title get() = if (longName.isBlank() || longName == name) name else "$name ($longName)"

    companion object {
        fun fromKey(key: String, name: String): Element? {
            val (k, id) = key.split(':').takeIf { it.size == 2 } ?: return null
            return Element(runCatching { Kind.valueOf(k) }.getOrNull() ?: return null, id.toLongOrNull() ?: return null, name)
        }
    }
}

data class Slot(val start: LocalTime, val end: LocalTime)

data class Master(val elements: Map<Kind, List<Element>>, val units: List<Slot>, val holidays: List<Pair<String, ClosedRange<LocalDate>>>) {
    private val byKey = elements.values.flatten().associateBy { it.key }
    fun find(kind: Kind, id: Long): Element? = byKey["${kind.name}:$id"]

    /** 1-based lesson number whose start matches, else null (lessons outside the grid) */
    fun unitNumber(start: LocalTime): Int? = units.indexOfFirst { it.start == start }.takeIf { it >= 0 }?.plus(1)
    fun holiday(day: LocalDate): String? = holidays.firstOrNull { day in it.second }?.first
}

/** One element of a period. Untis sends the original next to the current id: they differ for a substitution. */
data class Ref(val kind: Kind, val id: Long, val orgId: Long) {
    val changed get() = orgId != 0L && orgId != id
}

data class Period(
    val id: Long,
    val lessonId: Long,
    val start: LocalDateTime,
    val end: LocalDateTime,
    val refs: List<Ref>,
    val states: Set<String>,
    val lessonText: String = "",
    val substText: String = "",
    val info: String = "",
    val exam: String? = null,
    /** the period ids a double-lesson block stands for (empty: just [id]) */
    val parts: List<Long> = emptyList(),
    /** not from Untis: the school does not show this day yet, so Derive.project filled it from an earlier week */
    val projected: Boolean = false,
) {
    val ids get() = parts.ifEmpty { listOf(id) }
    val cancelled get() = "CANCELLED" in states
    val isExam get() = exam != null || "EXAM" in states
    val additional get() = "ADDITIONAL" in states
    /** substitution, room swap, moved lesson: anything Untis marks as not the normal plan */
    val irregular get() = !cancelled && ("IRREGULAR" in states || additional || refs.any { it.changed })
    val special get() = cancelled || irregular || isExam

    fun of(kind: Kind) = refs.filter { it.kind == kind }
    val subjectId get() = of(Kind.SUBJECT).firstOrNull()?.id
}

data class School(val name: String, val login: String, val server: String, val address: String)

data class Profile(val server: String, val school: String, val schoolName: String, val user: String?, val secret: String?) {
    val anonymous get() = user == null
    val demo get() = server == DEMO
    val rpcUrl get() = "https://$server/WebUntis/jsonrpc_intern.do?school=" + URLEncoder.encode(school, "UTF-8")

    fun toJson(): JSONObject = JSONObject().put("server", server).put("school", school).put("schoolName", schoolName)
        .put("user", user ?: JSONObject.NULL).put("secret", secret ?: JSONObject.NULL)

    companion object {
        const val DEMO = "demo"
        fun fromJson(o: JSONObject) = Profile(
            o.getString("server"), o.getString("school"), o.optString("schoolName"),
            o.optString("user").takeIf { !o.isNull("user") && it.isNotEmpty() },
            o.optString("secret").takeIf { !o.isNull("secret") && it.isNotEmpty() },
        )
    }
}

/** The content of untis://setschool?url=…&school=…&user=…&key=… from the WebUntis QR code. */
data class SetSchool(val server: String, val school: String, val user: String?, val key: String?)

object Parse {
    fun monday(day: LocalDate): LocalDate = day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** "thalia.webuntis.com", "https://x.webuntis.com/WebUntis/?school=y" → host; null unless it is a webuntis.com host */
    fun untisHost(raw: String?): String? {
        val host = raw?.trim()?.lowercase()?.removePrefix("https://")?.removePrefix("http://")
            ?.substringBefore('/')?.substringBefore('?')?.substringBefore(':') ?: return null
        return host.takeIf { it.endsWith(".webuntis.com") && it.length > ".webuntis.com".length && it.all { c -> c.isLetterOrDigit() || c == '.' || c == '-' } }
    }

    /** null for anything that is not a well-formed setschool link to a webuntis.com server */
    fun setSchool(link: String): SetSchool? {
        val uri = runCatching { URI(link.trim()) }.getOrNull() ?: return null
        if (uri.scheme != "untis" || uri.host != "setschool") return null
        val q = (uri.rawQuery ?: return null).split('&').mapNotNull {
            val i = it.indexOf('=')
            if (i <= 0) null else it.substring(0, i) to URLDecoder.decode(it.substring(i + 1), "UTF-8")
        }.toMap()
        val server = untisHost(q["url"]) ?: return null
        val school = q["school"]?.takeIf { it.isNotBlank() } ?: return null
        return SetSchool(server, school, q["user"]?.takeIf { it.isNotBlank() }, q["key"]?.takeIf { it.isNotBlank() })
    }

    fun schools(result: JSONObject): List<School> = result.optJSONArray("schools").objects().mapNotNull {
        val server = untisHost(it.optString("server")) ?: return@mapNotNull null
        School(it.optString("displayName"), it.optString("loginName"), server, it.optString("address"))
    }

    private fun time(t: String): LocalTime = LocalTime.parse(t.removePrefix("T"))
    private fun color(o: JSONObject) = o.optString("backColor").takeIf { !o.isNull("backColor") && it.startsWith("#") }

    fun master(md: JSONObject): Master {
        fun list(key: String, kind: Kind, long: (JSONObject) -> String) = md.optJSONArray(key).objects()
            .filter { it.optBoolean("active", true) }
            .map { Element(kind, it.getLong("id"), it.optString("name"), long(it), color(it)) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        val elements = mapOf(
            Kind.CLASS to list("klassen", Kind.CLASS) { it.optString("longName") },
            Kind.TEACHER to list("teachers", Kind.TEACHER) { "${it.optString("firstName")} ${it.optString("lastName")}".trim() },
            Kind.SUBJECT to list("subjects", Kind.SUBJECT) { it.optString("longName") },
            Kind.ROOM to list("rooms", Kind.ROOM) { it.optString("longName") },
        )
        // the day with the most lessons defines the rows; schools rarely differ per day
        val units = md.optJSONObject("timeGrid")?.optJSONArray("days").objects()
            .map { d -> d.optJSONArray("units").objects().map { Slot(time(it.getString("startTime")), time(it.getString("endTime"))) } }
            .maxByOrNull { it.size }.orEmpty().sortedBy { it.start }
        val holidays = md.optJSONArray("holidays").objects().mapNotNull {
            runCatching { it.optString("longName").ifBlank { it.optString("name") } to (LocalDate.parse(it.getString("startDate"))..LocalDate.parse(it.getString("endDate"))) }.getOrNull()
        }
        return Master(elements, units, holidays)
    }

    /** "2026-11-18T14:25Z": Untis writes school-local time with a Z that does not mean UTC */
    private fun dateTime(s: String): LocalDateTime = LocalDateTime.parse(s.removeSuffix("Z"))

    fun periods(result: JSONObject): List<Period> = result.getJSONObject("timetable").optJSONArray("periods").objects().map { p ->
        val text = p.optJSONObject("text")
        Period(
            id = p.getLong("id"),
            lessonId = p.optLong("lessonId"),
            start = dateTime(p.getString("startDateTime")),
            end = dateTime(p.getString("endDateTime")),
            refs = p.optJSONArray("elements").objects().mapNotNull { e ->
                val kind = runCatching { Kind.valueOf(e.getString("type")) }.getOrNull() ?: return@mapNotNull null
                Ref(kind, e.optLong("id"), e.optLong("orgId"))
            },
            states = p.optJSONArray("is").strings().toSet(),
            lessonText = text?.optString("lesson").orEmpty(),
            substText = text?.optString("substitution").orEmpty(),
            info = text?.optString("info").orEmpty(),
            exam = p.optJSONObject("exam")?.let { listOf(it.optString("examtype"), it.optString("name")).filter(String::isNotBlank).joinToString(" ").ifBlank { "Klausur" } },
        )
    }.sortedBy { it.start }.let(Derive::withoutCovered)

    /** What getPeriodData2017 knows about one period beyond the timetable: topic and homework. */
    data class Extra(val topic: String, val homework: List<String>, val info: String)

    fun extra(result: JSONObject, id: Long): Extra? {
        val d = result.optJSONObject("dataByTTId")?.optJSONObject(id.toString()) ?: return null
        val hw = d.optJSONArray("homeWorks").objects().map { it.optString("text") }.filter(String::isNotBlank)
        val t = d.optJSONObject("text")
        val info = listOfNotNull(t?.optString("lesson"), t?.optString("info"), t?.optString("substitution")).filter(String::isNotBlank).distinct().joinToString("\n")
        return Extra(d.optJSONObject("topic")?.optString("text").orEmpty(), hw, info)
    }

    /** Start and end of the school year containing [day], from masterData.schoolyears; null outside every year. */
    fun schoolYear(md: JSONObject, day: LocalDate): ClosedRange<LocalDate>? = md.optJSONArray("schoolyears").objects().mapNotNull {
        runCatching { LocalDate.parse(it.getString("startDate"))..LocalDate.parse(it.getString("endDate")) }.getOrNull()
    }.firstOrNull { day in it }

    /** The account's own timetable. Parents get their first child. Null when Untis names none (#503-like accounts). */
    fun own(userData: JSONObject): Element? {
        val type = userData.optString("elemType")
        val id = userData.optLong("elemId")
        if (!userData.isNull("elemType") && id > 0) {
            val kind = runCatching { Kind.valueOf(type) }.getOrNull()
            if (kind != null) return Element(kind, id, "Mein Plan", userData.optString("displayName"))
        }
        val child = userData.optJSONArray("children").objects().firstOrNull { it.optLong("id") > 0 } ?: return null
        return Element(Kind.STUDENT, child.getLong("id"), "Mein Plan", child.optString("displayName"))
    }
}

fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else (0 until length()).map { optString(it) }

/** Side-by-side placement for overlapping periods (parallel courses): lane index and lane count per period. */
data class Placed(val period: Period, val lane: Int, val lanes: Int)

fun lanes(day: List<Period>): List<Placed> {
    val out = mutableListOf<Placed>()
    var cluster = mutableListOf<Pair<Period, Int>>()
    var clusterEnd: LocalDateTime? = null
    val laneEnds = mutableListOf<LocalDateTime>()
    fun flush() {
        out += cluster.map { (p, l) -> Placed(p, l, laneEnds.size) }
        cluster = mutableListOf()
        laneEnds.clear()
    }
    for (p in day.sortedWith(compareBy({ it.start }, { it.end }))) {
        if (clusterEnd != null && !p.start.isBefore(clusterEnd)) flush()
        val free = laneEnds.indexOfFirst { !it.isAfter(p.start) }
        val lane = if (free >= 0) free.also { laneEnds[it] = p.end } else laneEnds.size.also { laneEnds += p.end }
        cluster += p to lane
        clusterEnd = if (clusterEnd == null || p.end.isAfter(clusterEnd)) p.end else clusterEnd
    }
    flush()
    return out
}

// ---- Info Center (wave B): what Untis lists beside the timetable

data class Exam(val id: Long, val type: String, val name: String, val text: String, val start: LocalDateTime, val end: LocalDateTime, val subjectId: Long?, val roomIds: List<Long>, val teacherIds: List<Long>)
data class Homework(val id: Long, val subjectId: Long?, val given: LocalDate?, val due: LocalDate?, val text: String, val remark: String, val completed: Boolean)
data class Absence(val id: Long, val start: LocalDateTime, val end: LocalDateTime, val reason: String, val excused: Boolean, val text: String)
data class DayMessage(val id: Long, val subject: String, val text: String, val attachments: Int)
data class OfficeHour(val id: Long, val start: LocalDateTime, val end: LocalDateTime, val teacher: String, val rooms: String, val email: String?, val phone: String?)

object InfoParse {
    /** "2026-11-18T08:00Z", "2026-11-18" or "20261118" */
    private fun date(s: String?): LocalDate? {
        val t = s?.trim().orEmpty()
        return runCatching { LocalDate.parse(t.take(10)) }.getOrNull()
            ?: runCatching { LocalDate.parse(t.take(8), java.time.format.DateTimeFormatter.BASIC_ISO_DATE) }.getOrNull()
    }
    private fun dateTime(s: String?): LocalDateTime? = runCatching { LocalDateTime.parse(s!!.removeSuffix("Z")) }.getOrNull()
    private fun longs(o: JSONObject, key: String) = o.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.optLong(it) } }.orEmpty()

    fun exams(r: JSONObject): List<Exam> = r.optJSONArray("exams").objects().mapNotNull {
        Exam(
            it.optLong("id"), it.optString("examType"), it.optString("name"), it.optString("text"),
            dateTime(it.optString("startDateTime")) ?: return@mapNotNull null, dateTime(it.optString("endDateTime")) ?: return@mapNotNull null,
            it.optLong("subjectId").takeIf { id -> id > 0 }, longs(it, "roomIds"), longs(it, "teacherIds"),
        )
    }.sortedBy { it.start }

    /** homework names its lesson; the lesson (in lessonsById) names the subject */
    fun homework(r: JSONObject): List<Homework> {
        val lessons = r.optJSONObject("lessonsById")
        return r.optJSONArray("homeWorks").objects().map {
            val subject = lessons?.optJSONObject(it.optLong("lessonId").toString())?.optLong("subjectId")?.takeIf { id -> id > 0 }
            Homework(it.optLong("id"), subject, date(it.optString("startDate")), date(it.optString("endDate")),
                it.optString("text"), it.optString("remark").takeIf { _ -> !it.isNull("remark") }.orEmpty(), it.optBoolean("completed"))
        }.sortedWith(compareBy({ it.due }, { it.id }))
    }

    fun absences(r: JSONObject): List<Absence> = r.optJSONArray("absences").objects().mapNotNull {
        Absence(it.optLong("id"), dateTime(it.optString("startDateTime")) ?: return@mapNotNull null, dateTime(it.optString("endDateTime")) ?: return@mapNotNull null,
            it.optString("absenceReason"), it.optBoolean("excused"), it.optString("text"))
    }.sortedByDescending { it.start }

    fun messages(r: JSONObject): List<DayMessage> = r.optJSONArray("messages").objects().map {
        DayMessage(it.optLong("id"), it.optString("subject"), html(it.optString("body")), it.optJSONArray("attachments")?.length() ?: 0)
    }

    fun officeHours(r: JSONObject): List<OfficeHour> = r.optJSONArray("officeHours").objects().mapNotNull {
        OfficeHour(it.optLong("id"), dateTime(it.optString("startDateTime")) ?: return@mapNotNull null, dateTime(it.optString("endDateTime")) ?: return@mapNotNull null,
            it.optString("displayNameTeacher"), it.optString("displayNameRooms"),
            it.optString("email").takeIf { v -> !it.isNull("email") && v.isNotBlank() }, it.optString("phone").takeIf { v -> !it.isNull("phone") && v.isNotBlank() })
    }.sortedBy { it.start }

    /** School news come as HTML; the app shows text. Pure, so the JVM test covers it. */
    fun html(s: String): String = s
        .replace(Regex("(?i)<br\\s*/?>|</p>|</div>|</li>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
        .replace(Regex("&#(\\d+);")) { m -> m.groupValues[1].toIntOrNull()?.let { String(Character.toChars(it)) } ?: "" }
        .lines().map { it.trim() }.joinToString("\n").replace(Regex("\n{3,}"), "\n\n").trim()
}
