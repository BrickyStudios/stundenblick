package io.github.brickystudios.stundenblick

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/**
 * The internal JSON-RPC interface of Untis Mobile (jsonrpc_intern.do), the same one the official app uses.
 * Blocking calls: run them on Dispatchers.IO. Demo profiles never reach this object (see Data).
 */
object Untis {
    private const val SEARCH = "https://schoolsearch.webuntis.com/schoolquery2"

    private fun post(url: String, body: String): String {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        try {
            c.outputStream.use { it.write(body.toByteArray()) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream ?: throw IOException("HTTP $code")
            val text = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299 && !text.trimStart().startsWith("{")) throw IOException("HTTP $code")
            return text
        } finally {
            c.disconnect()
        }
    }

    /** One JSON-RPC call; returns `result` or throws UntisException with Untis' error code. */
    fun call(url: String, method: String, params: JSONObject): Any {
        val req = JSONObject().put("id", "sb").put("jsonrpc", "2.0").put("method", method).put("params", JSONArray().put(params))
        val res = JSONObject(post(url, req.toString()))
        res.optJSONObject("error")?.let { throw UntisException(it.optInt("code"), it.optString("message")) }
        if (!res.has("result")) throw org.json.JSONException("no result")
        return res.get("result")
    }

    fun auth(p: Profile, now: Long = System.currentTimeMillis()): JSONObject =
        JSONObject().put("user", p.user ?: "#anonymous#").put("otp", Totp.code(p.secret, now)).put("clientTime", now)

    fun searchSchools(query: String): List<School> =
        Parse.schools(call(SEARCH, "searchSchool", JSONObject().put("search", query)) as JSONObject)

    /** Trades user + password (+ 2FA code) for the app secret; the password is not kept anywhere. */
    fun appSecret(server: String, school: String, user: String, password: String, token: String?): String {
        val p = JSONObject().put("userName", user).put("password", password)
        if (!token.isNullOrBlank()) p.put("token", token.trim())
        return call(Profile(server, school, "", null, null).rpcUrl, "getAppSharedSecret", p).toString()
    }

    fun userData(p: Profile): JSONObject = call(
        p.rpcUrl, "getUserData2017",
        JSONObject().put("elementId", 0).put("deviceOs", "AND").put("deviceOsVersion", "").put("auth", auth(p)),
    ) as JSONObject

    fun timetable(p: Profile, kind: Kind, id: Long, monday: LocalDate): JSONObject = call(
        p.rpcUrl, "getTimetable2017",
        JSONObject().put("id", id).put("type", kind.name)
            .put("startDate", monday.toString()).put("endDate", monday.plusDays(5).toString())
            .put("masterDataTimestamp", 0).put("timetableTimestamp", 0).put("timetableTimestamps", JSONArray())
            .put("auth", auth(p)),
    ) as JSONObject

    fun periodData(p: Profile, ids: Collection<Long>): JSONObject = call(
        p.rpcUrl, "getPeriodData2017",
        JSONObject().put("ttIds", JSONArray(ids)).put("auth", auth(p)),
    ) as JSONObject

    private fun range(p: Profile, from: LocalDate, to: LocalDate) =
        JSONObject().put("startDate", from.toString()).put("endDate", to.toString()).put("auth", auth(p))

    fun exams(p: Profile, kind: Kind, id: Long, from: LocalDate, to: LocalDate): JSONObject =
        call(p.rpcUrl, "getExams2017", range(p, from, to).put("id", id).put("type", kind.name)) as JSONObject

    fun homework(p: Profile, kind: Kind, id: Long, from: LocalDate, to: LocalDate): JSONObject =
        call(p.rpcUrl, "getHomeWork2017", range(p, from, to).put("id", id).put("type", kind.name)) as JSONObject

    fun messagesOfDay(p: Profile, day: LocalDate): JSONObject =
        call(p.rpcUrl, "getMessagesOfDay2017", JSONObject().put("date", day.toString()).put("auth", auth(p))) as JSONObject

    fun officeHours(p: Profile, klasseId: Long, from: LocalDate): JSONObject =
        call(p.rpcUrl, "getOfficeHours2017", JSONObject().put("klasseId", klasseId).put("startDate", from.toString()).put("auth", auth(p))) as JSONObject

    fun absences(p: Profile, from: LocalDate, to: LocalDate): JSONObject =
        call(p.rpcUrl, "getStudentAbsences2017", range(p, from, to).put("includeExcused", true).put("includeUnExcused", true)) as JSONObject

    fun authToken(p: Profile): String =
        (call(p.rpcUrl, "getAuthToken", JSONObject().put("auth", auth(p))) as JSONObject).getString("token")

    /** F4 only: does the newer REST timetable answer for this account? Returns HTTP code and number of days. */
    fun restProbe(p: Profile, token: String, kind: Kind, id: Long, monday: LocalDate): Pair<Int, Int> {
        val url = "https://${p.server}/WebUntis/api/rest/view/v1/timetable/entries?start=$monday&end=${monday.plusDays(4)}" +
            "&format=1&resourceType=${kind.name}&resources=$id&periodTypes=&timetableType=MY_TIMETABLE&layout=START_TIME"
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val code = c.responseCode
            if (code != 200) return code to 0
            val days = JSONObject(c.inputStream.bufferedReader().use { it.readText() }).optJSONArray("days")?.length() ?: 0
            return code to days
        } finally {
            c.disconnect()
        }
    }
}
