package io.github.brickystudios.stundenblick

import android.content.Context
import android.os.Build
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/** One loaded week of one timetable. periods == null: nothing cached and not loaded yet. */
data class Week(val periods: List<Period>?, val fetchedAt: Long, val loading: Boolean, val error: String?)

/**
 * App-wide data: profile, master data, cached weeks, preferences. Files live in the app's private storage
 * (allowBackup=false); the password never gets here, only the app secret Untis hands out.
 */
class Repo private constructor(ctx: Context) {
    private val app = ctx.applicationContext
    private val dir = app.filesDir
    private val prefs = app.getSharedPreferences("sb", Context.MODE_PRIVATE)

    val profile = mutableStateOf(readJson("profile.json")?.let { runCatching { Profile.fromJson(it) }.getOrNull() })
    val master = mutableStateOf(readJson("master.json")?.optJSONObject("data")?.let { runCatching { Parse.master(it) }.getOrNull() })
    val own = mutableStateOf(readOwn())
    val weeks = mutableStateMapOf<String, Week>()
    val favorites = mutableStateOf(prefs.getStringSet("favorites", emptySet())!!.toSet())
    val hiddenVersion = mutableStateOf(0) // bumps when a filter changes, so screens recompose

    var notify: Boolean
        get() = prefs.getBoolean("notify", false)
        set(v) = prefs.edit().putBoolean("notify", v).apply()
    var breakInfo: Boolean
        get() = prefs.getBoolean("breakInfo", false)
        set(v) = prefs.edit().putBoolean("breakInfo", v).apply()
    var updates: Boolean
        get() = prefs.getBoolean("updates", true)
        set(v) = prefs.edit().putBoolean("updates", v).apply()
    var weekView: Boolean
        get() = prefs.getBoolean("weekView", true)
        set(v) = prefs.edit().putBoolean("weekView", v).apply()

    private fun readJson(name: String): JSONObject? = File(dir, name).takeIf { it.exists() }?.let { runCatching { JSONObject(it.readText()) }.getOrNull() }
    private fun writeJson(name: String, o: JSONObject) {
        val f = File(dir, name)
        f.parentFile?.mkdirs()
        val tmp = File(f.path + ".tmp") // write-then-rename: a killed app never leaves half a file
        tmp.writeText(o.toString())
        tmp.renameTo(f)
    }

    private fun readOwn(): Element? = prefs.getString("own", null)?.let { Element.fromKey(it, "Mein Plan")?.copy(longName = prefs.getString("ownName", "").orEmpty()) }
    fun setOwn(e: Element?) {
        prefs.edit().putString("own", e?.key).putString("ownName", e?.longName).apply()
        own.value = e?.copy(name = "Mein Plan")
        // another plan: the old plan's history and day caches no longer apply
        listOf("history.json", "exams.json", "homework.json", "absences.json", "officehours.json").forEach { File(dir, it).delete() }
        history.value = emptyList()
        info.clear()
    }

    // ---- names for favorites: "CLASS:3|Q2"
    fun isFavorite(e: Element) = favorites.value.any { it.substringBefore('|') == e.key }
    fun toggleFavorite(e: Element) {
        val rest = favorites.value.filterNot { it.substringBefore('|') == e.key }.toSet()
        val next = if (rest.size == favorites.value.size) rest + "${e.key}|${e.name}" else rest
        prefs.edit().putStringSet("favorites", next).apply()
        favorites.value = next
    }
    fun favoriteElements(): List<Element> = favorites.value.mapNotNull {
        val key = it.substringBefore('|')
        val (k, id) = key.split(':').takeIf { p -> p.size == 2 } ?: return@mapNotNull null
        val kind = runCatching { Kind.valueOf(k) }.getOrNull() ?: return@mapNotNull null
        master.value?.find(kind, id.toLong()) ?: Element.fromKey(key, it.substringAfter('|'))
    }.sortedBy { it.name }

    // ---- K5 course filter, per timetable
    fun hidden(e: Element): Set<Long> = prefs.getStringSet("hidden_${e.key}", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet()
    fun setHidden(e: Element, ids: Set<Long>) {
        prefs.edit().putStringSet("hidden_${e.key}", ids.map { it.toString() }.toSet()).apply()
        hiddenVersion.value++
    }

    var last: Element?
        get() = prefs.getString("last", null)?.let { Element.fromKey(it, prefs.getString("lastName", "").orEmpty()) }
        set(v) = prefs.edit().putString("last", v?.key).putString("lastName", v?.name).apply()

    // ---- network, with the demo school answering locally
    private fun userData(p: Profile) = if (p.demo) Demo.userData() else Untis.userData(p)
    private fun timetable(p: Profile, kind: Kind, id: Long, monday: LocalDate) = if (p.demo) Demo.timetable(kind, id, monday) else Untis.timetable(p, kind, id, monday)
    private fun periodData(p: Profile, ids: Collection<Long>) = if (p.demo) Demo.periodData(ids) else Untis.periodData(p, ids)

    /** Checks the profile with Untis, then stores it. Throws on failure, so a bad login leaves nothing behind. */
    suspend fun login(p: Profile) = withContext(Dispatchers.IO) {
        val ud = userData(p)
        val md = ud.getJSONObject("masterData")
        val m = Parse.master(md)
        // the QR link carries only the login name; Untis knows the real school name
        val named = if (p.schoolName.isBlank()) p.copy(schoolName = ud.optJSONObject("userData")?.optString("schoolName").orEmpty()) else p
        logout()
        writeJson("profile.json", named.toJson())
        writeJson("master.json", masterFile(md, ud))
        profile.value = named
        master.value = m
        setOwn(ud.optJSONObject("userData")?.let(Parse::own))
    }

    /** master data changes rarely: once a day is enough (new teachers, rooms) */
    suspend fun refreshMaster() = withContext(Dispatchers.IO) {
        val p = profile.value ?: return@withContext
        val saved = readJson("master.json")?.optLong("savedAt") ?: 0
        if (System.currentTimeMillis() - saved < 24 * 3600_000L) return@withContext
        runCatching {
            val ud = userData(p)
            val md = ud.getJSONObject("masterData")
            master.value = Parse.master(md)
            writeJson("master.json", masterFile(md, ud))
        }
    }

    private fun masterFile(md: JSONObject, ud: JSONObject) = JSONObject().put("savedAt", System.currentTimeMillis()).put("data", md)
        .put("rights", ud.optJSONObject("userData")?.optJSONArray("rights") ?: org.json.JSONArray())
        .put("klassenIds", ud.optJSONObject("userData")?.optJSONArray("klassenIds") ?: org.json.JSONArray())

    private fun masterExtras() = readJson("master.json")
    fun rights(): Set<String> = masterExtras()?.optJSONArray("rights").strings().toSet()
    private fun schoolYear(day: LocalDate) = masterExtras()?.optJSONObject("data")?.let { Parse.schoolYear(it, day) }

    fun logout() {
        dir.listFiles()?.forEach { it.deleteRecursively() }
        prefs.edit().clear().apply()
        profile.value = null
        master.value = null
        own.value = null
        weeks.clear()
        favorites.value = emptySet()
        history.value = emptyList()
        rooms.value = emptySet()
        info.clear()
    }

    private fun weekFile(e: Element, monday: LocalDate) = "weeks/${e.kind.name}-${e.id}-$monday.json"
    fun weekKey(e: Element, monday: LocalDate) = "${e.key}@$monday"

    fun cachedPeriods(e: Element, monday: LocalDate): List<Period>? =
        readJson(weekFile(e, monday))?.optJSONObject("data")?.let { runCatching { Parse.periods(it) }.getOrNull() }

    /** Shows the cache at once, then asks Untis unless the cache is younger than 5 minutes (or force). */
    suspend fun load(e: Element, monday: LocalDate, force: Boolean = false, maxAgeMs: Long = 5 * 60_000L) {
        val key = weekKey(e, monday)
        val p = profile.value ?: return
        val cached = weeks[key] ?: withContext(Dispatchers.IO) {
            val f = readJson(weekFile(e, monday))
            Week(f?.optJSONObject("data")?.let { runCatching { Parse.periods(it) }.getOrNull() }, f?.optLong("savedAt") ?: 0, false, null)
        }
        if (cached.loading) return
        if (!force && cached.periods != null && System.currentTimeMillis() - cached.fetchedAt < maxAgeMs) {
            weeks[key] = cached
            return
        }
        weeks[key] = cached.copy(loading = true, error = null)
        weeks[key] = withContext(Dispatchers.IO) {
            runCatching {
                val r = timetable(p, e.kind, e.id, monday)
                val now = System.currentTimeMillis()
                writeJson(weekFile(e, monday), JSONObject().put("savedAt", now).put("data", r))
                val fresh = Parse.periods(r)
                if (own.value?.key == e.key) record(cached.periods, fresh, e)
                Week(fresh, now, false, null)
            }.getOrElse { Week(cached.periods, cached.fetchedAt, false, Errors.explain(it)) }
        }
        if (own.value?.key == e.key) {
            DayWidget.update(app)
            BreakInfo.schedule(app)
        }
    }

    /** K4: topic, homework and extra text; a double-lesson block asks for all its periods and merges them */
    suspend fun extra(period: Period): Result<Parse.Extra?> = withContext(Dispatchers.IO) {
        val p = profile.value ?: return@withContext Result.success(null)
        runCatching {
            val r = periodData(p, period.ids)
            val parts = period.ids.mapNotNull { Parse.extra(r, it) }
            if (parts.isEmpty()) null else Parse.Extra(
                parts.map { it.topic }.filter(String::isNotBlank).distinct().joinToString("\n"),
                parts.flatMap { it.homework }.distinct(),
                parts.map { it.info }.filter(String::isNotBlank).distinct().joinToString("\n"),
            )
        }
    }

    // ---- Info Center (wave B): one cached answer per endpoint, at most once a day (the message: every two hours)
    enum class Info(val file: String) { EXAMS("exams.json"), HOMEWORK("homework.json"), ABSENCES("absences.json"), MESSAGES("messages.json"), OFFICE("officehours.json") }
    data class InfoState(val data: JSONObject?, val savedAt: Long, val day: String?, val noRight: Boolean, val loading: Boolean = false, val error: String? = null)

    val info = mutableStateMapOf<Info, InfoState>()

    private fun readInfo(k: Info) = readJson(k.file)?.let { InfoState(it.optJSONObject("data"), it.optLong("savedAt"), it.optString("day").ifBlank { null }, it.optBoolean("noRight")) }
        ?: InfoState(null, 0, null, false)

    /** what the screens read; the cache file until the first load of this run */
    fun infoOf(k: Info): InfoState = info[k] ?: readInfo(k)

    /** Untis hands this to the account at all? Without login there is no homework or absence list. */
    fun offers(k: Info): Boolean {
        val p = profile.value ?: return false
        if (infoOf(k).noRight) return false
        return when (k) {
            Info.HOMEWORK, Info.ABSENCES -> !p.anonymous || p.demo
            Info.EXAMS -> own.value != null
            Info.OFFICE -> "R_OFFICEHOURS" in rights() && officeClass() != null
            Info.MESSAGES -> true
        }
    }

    private fun officeClass(): Long? = masterExtras()?.optJSONArray("klassenIds")?.let { if (it.length() > 0) it.optLong(0) else null }
        ?: own.value?.takeIf { it.kind == Kind.CLASS }?.id

    suspend fun loadInfo(k: Info, force: Boolean = false) = withContext(Dispatchers.IO) {
        val p = profile.value ?: return@withContext
        if (!offers(k)) return@withContext
        val cur = infoOf(k)
        val today = LocalDate.now()
        val due = if (k == Info.MESSAGES) Fetch.message(cur.savedAt, System.currentTimeMillis(), force)
        else Fetch.daily(cur.day?.let { runCatching { LocalDate.parse(it) }.getOrNull() }, today, force)
        if (!due || cur.loading) { info[k] = cur; return@withContext }
        info[k] = cur.copy(loading = true, error = null)
        val own = own.value
        runCatching {
            when (k) {
                Info.EXAMS -> if (p.demo) Demo.exams() else Untis.exams(p, own!!.kind, own.id, today, schoolYear(today)?.endInclusive ?: today.plusDays(90))
                Info.HOMEWORK -> if (p.demo) Demo.homework() else Untis.homework(p, own!!.kind, own.id, today.minusDays(7), today.plusDays(28))
                Info.ABSENCES -> if (p.demo) Demo.absences() else Untis.absences(p, schoolYear(today)?.start ?: today.minusDays(180), today)
                Info.MESSAGES -> if (p.demo) Demo.messagesOfDay() else Untis.messagesOfDay(p, today)
                Info.OFFICE -> if (p.demo) Demo.officeHours() else Untis.officeHours(p, officeClass()!!, today)
            }
        }.fold(
            { r ->
                val now = System.currentTimeMillis()
                writeJson(k.file, JSONObject().put("savedAt", now).put("day", today.toString()).put("data", r))
                if (k == Info.EXAMS) recordExams(cur.data, r)
                info[k] = InfoState(r, now, today.toString(), false)
                if (k == Info.EXAMS) DayWidget.update(app)
            },
            { e ->
                if (e is UntisException && e.code == -8509) {
                    // the school does not hand this out: remember it, so the entry stays hidden
                    writeJson(k.file, JSONObject().put("savedAt", System.currentTimeMillis()).put("day", today.toString()).put("noRight", true))
                    info[k] = InfoState(null, System.currentTimeMillis(), today.toString(), true)
                } else info[k] = cur.copy(loading = false, error = Errors.explain(e)) // network: keep the cache and the day unset
            },
        )
    }

    /** V4: a newly announced exam goes into the change history, without an extra request */
    private fun recordExams(old: JSONObject?, fresh: JSONObject) {
        old ?: return
        val m = master.value
        val before = InfoParse.exams(old).map { it.id }.toSet()
        val day = java.time.format.DateTimeFormatter.ofPattern("EE d.M.", java.util.Locale.GERMAN)
        val add = InfoParse.exams(fresh).filter { it.id !in before }.map { e ->
            val subject = e.subjectId?.let { m?.find(Kind.SUBJECT, it)?.name } ?: e.name.ifBlank { "Klausur" }
            Entry(-e.id, "Neue Klausur: $subject am ${day.format(e.start)}", System.currentTimeMillis(), false)
        }
        if (add.isNotEmpty()) synchronized(historyLock) { writeHistory(history.value + add) }
    }

    // homework ticked off on this phone only; Untis' own "completed" also counts
    val homeworkDone = mutableStateOf(prefs.getStringSet("hwDone", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet())
    fun toggleDone(id: Long) {
        val next = if (id in homeworkDone.value) homeworkDone.value - id else homeworkDone.value + id
        prefs.edit().putStringSet("hwDone", next.map { it.toString() }.toSet()).apply()
        homeworkDone.value = next
    }

    // ---- RoomFinder (J2): rooms the user picked, each week loaded at most once
    val rooms = mutableStateOf(prefs.getStringSet("rooms", emptySet())!!.mapNotNull { it.toLongOrNull() }.toSet())
    fun setRooms(ids: Set<Long>) {
        prefs.edit().putStringSet("rooms", ids.map { it.toString() }.toSet()).apply()
        rooms.value = ids
    }

    // ---- change history (E11): one writer for the job thread and the UI, so no entry is lost or doubled
    data class Entry(val id: Long, val text: String, val at: Long, val seen: Boolean)

    val history = mutableStateOf(readHistory())
    private val historyLock = Any()

    private fun readHistory(): List<Entry> = readJson("history.json")?.optJSONArray("entries").objects().map {
        Entry(it.optLong("id"), it.optString("text"), it.optLong("at"), it.optBoolean("seen"))
    }

    private fun writeHistory(list: List<Entry>) {
        val cutoff = System.currentTimeMillis() - 60L * 24 * 3600_000
        val kept = list.filter { it.at >= cutoff }.takeLast(200)
        writeJson("history.json", JSONObject().put("entries", org.json.JSONArray(kept.map {
            JSONObject().put("id", it.id).put("text", it.text).put("at", it.at).put("seen", it.seen)
        })))
        history.value = kept
    }

    /** Compares the own plan from today on (two weeks) and keeps each new change once; returns the new texts. */
    private fun record(old: List<Period>?, fresh: List<Period>, e: Element): List<String> {
        val m = master.value ?: return emptyList()
        val today = LocalDate.now()
        fun window(ps: List<Period>) = ps.filter { !it.start.toLocalDate().isBefore(today) && it.start.toLocalDate().isBefore(today.plusDays(14)) }
        val found = Changes.between(old?.let(::window), window(fresh), m, hidden(e))
        if (found.isEmpty()) return emptyList()
        synchronized(historyLock) {
            val have = history.value.map { it.id to it.text }.toSet()
            val add = found.filter { it !in have }.map { (id, text) -> Entry(id, text, System.currentTimeMillis(), false) }
            if (add.isNotEmpty()) writeHistory(history.value + add)
            return add.map { it.text }
        }
    }

    fun markSeen(ids: Collection<Long>) = synchronized(historyLock) {
        if (history.value.any { !it.seen && it.id in ids }) writeHistory(history.value.map { if (it.id in ids) it.copy(seen = true) else it })
    }

    fun unseen(): Set<Long> = history.value.filter { !it.seen }.map { it.id }.toSet()

    /** K7: fetch this and next week of the own plan, return messages for what changed since the last look. */
    fun checkChanges(): List<String> {
        val p = profile.value ?: return emptyList()
        val e = own.value ?: return emptyList()
        master.value ?: return emptyList()
        val monday = Parse.monday(LocalDate.now())
        return listOf(monday, monday.plusWeeks(1)).flatMap { mon ->
            val old = cachedPeriods(e, mon)
            val r = timetable(p, e.kind, e.id, mon)
            writeJson(weekFile(e, mon), JSONObject().put("savedAt", System.currentTimeMillis()).put("data", r))
            record(old, Parse.periods(r), e)
        }
    }

    /** F4: each step reports OK or a code; Diag.report never sees a secret, token or name. */
    suspend fun connectionTest(): String = withContext(Dispatchers.IO) {
        val p = profile.value ?: return@withContext "Nicht angemeldet."
        val steps = mutableListOf<Diag.Step>()
        val e = own.value
        val monday = Parse.monday(LocalDate.now())
        if (p.demo) return@withContext "Demo-Schule: es gibt keinen Server zu prüfen."
        val ud = runCatching { userData(p) }
        ud.fold(
            {
                val own = Parse.own(it.getJSONObject("userData"))
                val rights = it.optJSONObject("userData")?.optJSONArray("rights").strings().sorted().joinToString(",").ifBlank { "keine" }
                steps += Diag.Step("Benutzerdaten", true, (if (own == null) "kein eigener Plan genannt" else "eigener Plan: ${own.kind.label}") + "; Rechte: $rights")
            },
            { steps += Diag.Step("Benutzerdaten", false, Diag.detail(it)) },
        )
        val today = LocalDate.now()
        val year = ud.getOrNull()?.optJSONObject("masterData")?.let { Parse.schoolYear(it, today) }
        val klasse = ud.getOrNull()?.optJSONObject("userData")?.optJSONArray("klassenIds")?.let { if (it.length() > 0) it.optLong(0) else null }
            ?: e?.takeIf { it.kind == Kind.CLASS }?.id
        // F6: count and field names per endpoint, never a value, so a test with a real account shows what the school hands out
        fun probe(label: String, skip: String? = null, block: () -> JSONObject) {
            if (skip != null) { steps += Diag.Step(label, false, skip); return }
            runCatching(block).fold(
                { val (n, fields) = Diag.shape(it); steps += Diag.Step(label, true, "$n ${if (n == 1) "Eintrag" else "Einträge"}; $fields") },
                { steps += Diag.Step(label, false, Diag.detail(it)) },
            )
        }
        val ownOrNull = if (e == null) "kein eigener Plan" else null
        probe("Klausuren", ownOrNull) { Untis.exams(p, e!!.kind, e.id, today, year?.endInclusive?.takeIf { it > today } ?: today.plusDays(90)) }
        probe("Hausaufgaben", if (p.anonymous) "ohne Anmeldung nicht abgefragt" else ownOrNull) { Untis.homework(p, e!!.kind, e.id, today.minusDays(7), today.plusDays(28)) }
        probe("Nachricht des Tages") { Untis.messagesOfDay(p, today) }
        probe("Sprechstunden", if (klasse == null) "keine Klasse" else null) { Untis.officeHours(p, klasse!!, today) }
        probe("Fehlzeiten", if (p.anonymous) "ohne Anmeldung nicht abgefragt" else null) { Untis.absences(p, year?.start ?: today.minusDays(90), today) }
        var firstId: Long? = null
        if (e != null) runCatching { Parse.periods(timetable(p, e.kind, e.id, monday)) }.fold(
            { firstId = it.firstOrNull()?.id; steps += Diag.Step("Eigener Plan (JSON-RPC)", true, "${it.size} Stunden diese Woche") },
            { steps += Diag.Step("Eigener Plan (JSON-RPC)", false, Diag.detail(it)) },
        ) else steps += Diag.Step("Eigener Plan (JSON-RPC)", false, "keiner gesetzt")
        firstId?.let { id ->
            runCatching { Parse.extra(periodData(p, listOf(id)), id) }.fold(
                { steps += Diag.Step("Stunden-Details", true, if (it == null) "leer" else "Inhalt ${if (it.topic.isBlank()) "leer" else "da"}, ${it.homework.size} Hausaufgaben") },
                { steps += Diag.Step("Stunden-Details", false, Diag.detail(it)) },
            )
        }
        runCatching { Untis.authToken(p) }.fold(
            { token ->
                steps += Diag.Step("REST-Zugang", true, "")
                if (e != null) runCatching { Untis.restProbe(p, token, e.kind, e.id, monday) }.fold(
                    { (code, days) -> steps += Diag.Step("Eigener Plan (REST)", code == 200, "HTTP $code, $days Tage") },
                    { steps += Diag.Step("Eigener Plan (REST)", false, Diag.detail(it)) },
                )
            },
            { steps += Diag.Step("REST-Zugang", false, Diag.detail(it)) },
        )
        Diag.report(BuildConfig.VERSION_NAME, Build.VERSION.RELEASE, p.anonymous, steps)
    }

    companion object {
        @Volatile private var instance: Repo? = null
        fun get(ctx: Context): Repo = instance ?: synchronized(this) { instance ?: Repo(ctx).also { instance = it } }
    }
}
