package io.github.brickystudios.stundenblick

import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** An error answer from Untis' JSON-RPC: code plus Untis' own (English) message. */
class UntisException(val code: Int, message: String) : Exception(message)

/** The one-time password Untis Mobile sends with every request: TOTP (RFC 6238), HmacSHA1, 30 s, 6 digits. */
object Totp {
    fun code(secret: String?, timeMs: Long): Long {
        if (secret.isNullOrBlank()) return 0 // anonymous access sends 0
        val key = base32(secret) ?: return 0
        val step = timeMs / 30_000
        val msg = ByteArray(8) { i -> (step ushr (8 * (7 - i))).toByte() }
        val h = Mac.getInstance("HmacSHA1").apply { init(SecretKeySpec(key, "HmacSHA1")) }.doFinal(msg)
        val o = h[19].toInt() and 0xF
        val bin = ((h[o].toInt() and 0x7F) shl 24) or ((h[o + 1].toInt() and 0xFF) shl 16) or
            ((h[o + 2].toInt() and 0xFF) shl 8) or (h[o + 3].toInt() and 0xFF)
        return (bin % 1_000_000).toLong()
    }

    /** RFC 4648 base32; null on a character outside the alphabet */
    fun base32(s: String): ByteArray? {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        var buf = 0
        var bits = 0
        val out = java.io.ByteArrayOutputStream()
        for (c in s.uppercase(Locale.ROOT)) {
            if (c == '=' || c == ' ' || c == '-') continue
            val v = alphabet.indexOf(c).takeIf { it >= 0 } ?: return null
            buf = (buf shl 5) or v
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out.write((buf shr bits) and 0xFF)
            }
        }
        return out.toByteArray()
    }
}

/** F1: what the user reads instead of a stack trace. Every known code has its own sentence and next step. */
object Errors {
    const val TWO_FACTOR = -8519

    val known = mapOf(
        -8500 to "Diese Schule gibt es bei WebUntis nicht (mehr). Such sie neu.",
        -8502 to "Diesen Benutzernamen kennt die Schule nicht.",
        -8504 to "Benutzername oder Passwort falsch.",
        -8509 to "Deine Schule gibt diesen Plan für dein Konto nicht frei. Wähl einen anderen Plan.",
        -8511 to "Dein Zugang ist gesperrt. Frag bei der Untis-Verwaltung deiner Schule nach.",
        TWO_FACTOR to "Dein Konto nutzt Zwei-Faktor-Anmeldung. Gib den 6-stelligen Code aus deiner Authenticator-App ein oder nimm den QR-Code aus WebUntis → Profil → Freigaben.",
        -8520 to "Die Anmeldung ist abgelaufen. Melde dich in den Einstellungen neu an.",
        -8521 to "Die Anmeldung ist ungültig. Melde dich in den Einstellungen neu an.",
        -8523 to "Diese Schule erlaubt keinen Zugang ohne Anmeldung.",
        -8524 to "Die Uhrzeit auf deinem Handy weicht zu stark ab. Stell „Uhrzeit automatisch“ ein.",
        -6003 to "Zu viele Treffer. Gib mehr vom Schulnamen oder den Ort dazu ein.",
        -7001 to "Diesen Plan gibt es nicht mehr. Wähl ihn neu aus.",
        -7002 to "Diesen Plan gibt es nicht mehr. Wähl ihn neu aus.",
    )

    fun explain(e: Throwable): String = when (e) {
        is UntisException -> known[e.code] ?: "Untis meldet Fehler ${e.code}: ${e.message}"
        is UnknownHostException, is ConnectException, is SocketTimeoutException -> "Keine Verbindung zum Untis-Server. Gezeigt wird der letzte Stand."
        is IOException -> "Die Verbindung ist abgebrochen. Gezeigt wird der letzte Stand."
        is JSONException -> "Untis hat eine unverständliche Antwort geschickt. Der Verbindungstest in den Einstellungen zeigt mehr."
        else -> "Unerwarteter Fehler (${e.javaClass.simpleName})."
    }
}

/** K5: subjects the user switched off for one timetable (Oberstufe: only my courses). */
object Filter {
    fun apply(periods: List<Period>, hidden: Set<Long>) =
        if (hidden.isEmpty()) periods else periods.filter { it.subjectId == null || it.subjectId !in hidden }
}

/** K7: compare what was cached with what Untis says now and phrase each new deviation once. */
object Changes {
    private val day = DateTimeFormatter.ofPattern("EE d.M.", Locale.GERMAN)
    private val clock = DateTimeFormatter.ofPattern("HH:mm")

    /** what makes a period "the same" for a notification: status and who/where */
    private fun signature(p: Period) = listOf(p.states.sorted(), p.refs.sortedBy { "${it.kind}${it.id}${it.orgId}" }, p.exam, p.substText).toString()

    /**
     * Messages for periods that are special now and were not, or not like this, before.
     * old == null means first run: fill the cache, notify nothing.
     */
    fun between(old: List<Period>?, new: List<Period>, master: Master, hidden: Set<Long>): List<Pair<Long, String>> {
        if (old == null) return emptyList()
        val before = old.associateBy { it.id }
        return Filter.apply(new, hidden).filter { p ->
            val o = before[p.id]
            if (o == null) p.special else signature(o) != signature(p) && (p.special || o.special)
        }.map { it.id to describe(it, master) }
    }

    fun when_(p: Period, master: Master): String {
        val n = master.unitNumber(p.start.toLocalTime())
        return day.format(p.start) + ", " + (if (n != null) "$n. Std." else clock.format(p.start))
    }

    private fun name(master: Master, kind: Kind, id: Long) = master.find(kind, id)?.name ?: "?"

    fun describe(p: Period, master: Master): String {
        val subject = p.of(Kind.SUBJECT).firstOrNull()?.let { name(master, Kind.SUBJECT, it.id) } ?: p.lessonText.ifBlank { "Stunde" }
        val what = when {
            p.cancelled -> "$subject entfällt"
            p.isExam -> "$subject: ${p.exam ?: "Klausur"}"
            else -> {
                val parts = mutableListOf<String>()
                p.of(Kind.TEACHER).filter { it.changed }.forEach { parts += "Vertretung " + (if (it.id == 0L) "offen" else name(master, Kind.TEACHER, it.id)) }
                p.of(Kind.ROOM).filter { it.changed }.forEach { parts += "Raum ${name(master, Kind.ROOM, it.id)} statt ${name(master, Kind.ROOM, it.orgId)}" }
                if (p.additional) parts += "Zusatzstunde"
                if (parts.isEmpty()) parts += p.substText.ifBlank { "geändert" }
                "$subject: " + parts.joinToString(", ")
            }
        }
        return when_(p, master) + ": " + what
    }
}

/** F4: one line per check, built only from counts and codes, so no secret, token or name can end up in it. */
object Diag {
    data class Step(val label: String, val ok: Boolean, val detail: String)

    fun report(appVersion: String, android: String, anonymous: Boolean, steps: List<Step>) = buildString {
        appendLine("Stundenblick $appVersion · Android $android · ${if (anonymous) "ohne Anmeldung" else "mit Konto"}")
        steps.forEachIndexed { i, s -> appendLine("${i + 1}. ${s.label}: ${if (s.ok) "OK" else "Fehler"}${if (s.detail.isNotBlank()) " (${s.detail})" else ""}") }
    }.trimEnd()

    /** Field names only, never values: the result's keys plus the keys of the first item of its first non-empty list. */
    fun shape(o: JSONObject): Pair<Int, String> {
        val top = o.keys().asSequence().sorted().toList()
        val list = top.firstNotNullOfOrNull { k -> o.optJSONArray(k)?.takeIf { it.length() > 0 }?.let { k to it } }
        val first = list?.second?.optJSONObject(0)
        val inner = if (list != null && first != null) "; ${list.first}[]: " + first.keys().asSequence().sorted().joinToString(",") else ""
        return (list?.second?.length() ?: 0) to "Felder: " + top.joinToString(",") + inner
    }

    fun detail(e: Throwable): String = when (e) {
        is UntisException -> "Untis ${e.code}"
        else -> e.javaClass.simpleName
    }
}
