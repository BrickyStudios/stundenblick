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
        writeJson("master.json", JSONObject().put("savedAt", System.currentTimeMillis()).put("data", md))
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
            val md = userData(p).getJSONObject("masterData")
            master.value = Parse.master(md)
            writeJson("master.json", JSONObject().put("savedAt", System.currentTimeMillis()).put("data", md))
        }
    }

    fun logout() {
        dir.listFiles()?.forEach { it.deleteRecursively() }
        prefs.edit().clear().apply()
        profile.value = null
        master.value = null
        own.value = null
        weeks.clear()
        favorites.value = emptySet()
    }

    private fun weekFile(e: Element, monday: LocalDate) = "weeks/${e.kind.name}-${e.id}-$monday.json"
    fun weekKey(e: Element, monday: LocalDate) = "${e.key}@$monday"

    fun cachedPeriods(e: Element, monday: LocalDate): List<Period>? =
        readJson(weekFile(e, monday))?.optJSONObject("data")?.let { runCatching { Parse.periods(it) }.getOrNull() }

    /** Shows the cache at once, then asks Untis unless the cache is younger than 5 minutes (or force). */
    suspend fun load(e: Element, monday: LocalDate, force: Boolean = false) {
        val key = weekKey(e, monday)
        val p = profile.value ?: return
        val cached = weeks[key] ?: withContext(Dispatchers.IO) {
            val f = readJson(weekFile(e, monday))
            Week(f?.optJSONObject("data")?.let { runCatching { Parse.periods(it) }.getOrNull() }, f?.optLong("savedAt") ?: 0, false, null)
        }
        if (cached.loading) return
        if (!force && cached.periods != null && System.currentTimeMillis() - cached.fetchedAt < 5 * 60_000L) {
            weeks[key] = cached
            return
        }
        weeks[key] = cached.copy(loading = true, error = null)
        weeks[key] = withContext(Dispatchers.IO) {
            runCatching {
                val r = timetable(p, e.kind, e.id, monday)
                val now = System.currentTimeMillis()
                writeJson(weekFile(e, monday), JSONObject().put("savedAt", now).put("data", r))
                Week(Parse.periods(r), now, false, null)
            }.getOrElse { Week(cached.periods, cached.fetchedAt, false, Errors.explain(it)) }
        }
        if (own.value?.key == e.key) DayWidget.update(app)
    }

    /** K4: topic, homework and extra text for one period */
    suspend fun extra(period: Period): Result<Parse.Extra?> = withContext(Dispatchers.IO) {
        val p = profile.value ?: return@withContext Result.success(null)
        runCatching { Parse.extra(periodData(p, listOf(period.id)), period.id) }
    }

    /** K7: fetch this and next week of the own plan, return messages for what changed since the last look. */
    fun checkChanges(): List<String> {
        val p = profile.value ?: return emptyList()
        val e = own.value ?: return emptyList()
        val m = master.value ?: return emptyList()
        val monday = Parse.monday(LocalDate.now())
        val today = LocalDate.now()
        return listOf(monday, monday.plusWeeks(1)).flatMap { mon ->
            val old = cachedPeriods(e, mon)
            val r = timetable(p, e.kind, e.id, mon)
            writeJson(weekFile(e, mon), JSONObject().put("savedAt", System.currentTimeMillis()).put("data", r))
            val fresh = Parse.periods(r).filter { !it.start.toLocalDate().isBefore(today) && it.start.toLocalDate().isBefore(today.plusDays(7)) }
            Changes.between(old?.filter { !it.start.toLocalDate().isBefore(today) }, fresh, m, hidden(e))
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
        // F6: count and field names per endpoint, never a value, so Jonas' test shows what his school hands out
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
