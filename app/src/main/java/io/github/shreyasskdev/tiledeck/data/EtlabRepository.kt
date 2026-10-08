package io.github.shreyasskdev.tiledeck.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Talks to Etlab's own mobile-app JSON API (the same one the official
 * Android app uses), at [ETLAB_BASE_URL]. This is a *much* more reliable
 * foundation than scraping the web HTML: it's the real backend contract,
 * gives full subject names directly, and needs no session cookies -- just
 * a bearer token from /app/login.
 *
 * Endpoints reverse-engineered from the Etlab Android APK by the
 * open-source `retlab` project (github.com/dcdunkan/retlab /
 * github.com/dcdunkan/retlab-generate).
 */
class EtlabRepository(private val baseClient: OkHttpClient = OkHttpClient()) {

    data class FetchResult(val attendance: AttendanceResult, val timetable: TimetableResult? = null)
    private data class StudentProfile(val semesterId: String)

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun fetchAttendance(username: String, password: String): FetchResult =
        withContext(Dispatchers.IO) {
            val accessToken = login(username, password)
            val profile = runCatching { fetchProfile(accessToken) }.getOrNull()
            val attendance = fetchAttendanceBySubject(accessToken, profile?.semesterId.orEmpty())
            val timetable = runCatching {
                fetchTimetable(accessToken, profile?.semesterId)
            }.getOrNull()
            FetchResult(attendance, timetable)
        }

    private fun login(username: String, password: String): String {
        val payload = JSONObject()
            .put("username", username)
            .put("password", password)
            .toString()

        val request = Request.Builder()
            //.url("$ETLAB_BASE_URL/app/login")
            .url("$ETLAB_BASE_URL/androidapp/app/login")
            .header("User-Agent", ETLAB_USER_AGENT)
            .header("Content-Type", "application/json")
            .post(payload.toRequestBody(jsonMediaType))
            .build()

        baseClient.newCall(request).execute().use { response ->
            val bodyText = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(bodyText) }.getOrNull()
                ?: throw ParsingException(
                    "Login response wasn't JSON. HTTP ${response.code}. " +
                            "Body starts with: ${bodyText.take(300).replace("\n", " ")}"
                )

            val loggedIn = json.optBoolean("login", false)
            val accessToken = json.optString("access_token", "")

            if (!loggedIn || accessToken.isBlank()) {
                val serverError = json.optString("error", "").takeIf { it.isNotBlank() }
                if (serverError != null) throw ParsingException(serverError)
                throw InvalidCredentialsException()
            }

            return accessToken
        }
    }

    private fun fetchProfile(accessToken: String): StudentProfile {
        val request = Request.Builder()
            .url("$ETLAB_BASE_URL/androidapp/app/profile")
            .header("User-Agent", ETLAB_USER_AGENT)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $accessToken")
            .post(JSONObject().toString().toRequestBody(jsonMediaType))
            .build()

        baseClient.newCall(request).execute().use { response ->
            val bodyText = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(bodyText) }.getOrNull()
                ?: throw ParsingException("Etlab returned an unexpected profile response.")

            if (!json.optBoolean("login", true)) throw SessionExpiredException()

            val semesterId = json.optString("sem_id", "").trim()
            if (semesterId.isBlank()) throw ParsingException("No current semester found in profile.")
            return StudentProfile(semesterId)
        }
    }

    private fun fetchAttendanceBySubject(accessToken: String, semesterId: String): AttendanceResult {
        val payload = JSONObject().put("sem_id", semesterId).toString()

        val request = Request.Builder()
            //.url("$ETLAB_BASE_URL/app/attendancebysubject")
            .url("$ETLAB_BASE_URL/androidapp/app/attendancebysubject")
            .header("User-Agent", ETLAB_USER_AGENT)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $accessToken")
            .post(payload.toRequestBody(jsonMediaType))
            .build()

        baseClient.newCall(request).execute().use { response ->
            val bodyText = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(bodyText) }.getOrNull()
                ?: throw ParsingException("Etlab returned an unexpected attendance response.")

            if (!json.optBoolean("login", true)) {
                throw SessionExpiredException()
            }

            val subjectsJson = json.optJSONArray("subjects")
                ?: throw ParsingException("No subjects found in Etlab's response.")

            val subjects = mutableListOf<SubjectAttendance>()
            var sumPresent = 0
            var sumTotal = 0

            for (i in 0 until subjectsJson.length()) {
                val s = subjectsJson.optJSONObject(i) ?: continue
                val code = s.optString("code", "").trim().uppercase()
                val name = s.optString("subject", "").trim()
                if (code.isBlank()) continue

                // "total_subject" is formatted "present/total", e.g. "23/24".
                val raw = s.optString("total_subject", "").trim()
                val parts = raw.split("/")
                val present = parts.getOrNull(0)?.trim()?.toIntOrNull() ?: 0
                val total = parts.getOrNull(1)?.trim()?.toIntOrNull() ?: 0

                val percent = s.optString("percentage_subject", "")
                    .replace("%", "")
                    .trim()
                    .toDoubleOrNull()
                    ?: if (total > 0) (present * 100.0 / total) else 0.0

                subjects.add(SubjectAttendance(code = code, name = name, present = present, total = total, percent = percent))
                sumPresent += present
                sumTotal += total
            }

            val overallPercent = json.optString("total_percent", "")
                .replace("%", "")
                .trim()
                .toDoubleOrNull()
                ?: if (sumTotal > 0) (sumPresent * 100.0 / sumTotal) else 0.0

            return AttendanceResult(
                subjects = subjects,
                totalPresent = sumPresent,
                totalHours = sumTotal,
                overallPercent = overallPercent,
                updatedAt = System.currentTimeMillis()
            )
        }
    }

    private fun fetchTimetable(accessToken: String, semesterId: String?): TimetableResult {
        val payload = JSONObject().toString()

        val request = Request.Builder()
            .url("$ETLAB_BASE_URL/androidapp/app/timetable")
            .header("User-Agent", ETLAB_USER_AGENT)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $accessToken")
            .post(payload.toRequestBody(jsonMediaType))
            .build()

        baseClient.newCall(request).execute().use { response ->
            val bodyText = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(bodyText) }.getOrNull()
                ?: throw ParsingException("Etlab returned an unexpected timetable response.")

            if (!json.optBoolean("login", true)) {
                throw SessionExpiredException()
            }

            val timetableJson = json.optJSONArray("timetable")
                ?: throw ParsingException("No timetable found in Etlab's response.")

            val today = LocalDate.now()
            val todayAttendance = semesterId
                ?.takeIf { it.isNotBlank() }
                ?.let { id -> runCatching { fetchDailyAttendance(accessToken, today, id) }.getOrNull() }
                .orEmpty()
            val todayKey = etlabDayKey(today)

            val days = mutableListOf<TimetableDayResult>()
            for (i in 0 until timetableJson.length()) {
                val dayJson = timetableJson.optJSONObject(i) ?: continue
                val day = dayJson.optString("day", "")
                val subjectsJson = dayJson.optJSONArray("sub") ?: JSONArray()

                val subjects = mutableListOf<TimetableSubjectResult>()
                for (j in 0 until subjectsJson.length()) {
                    val subJson = subjectsJson.optJSONObject(j) ?: continue
                    val hour = subJson.optInt("hour", 0)
                    val subject = subJson.optString("subject", "").trim()
                    val attendanceStatus = if (day == todayKey) todayAttendance[hour] else null
                    subjects.add(TimetableSubjectResult(hour, subject, attendanceStatus))
                }
                days.add(TimetableDayResult(day, subjects))
            }
            return TimetableResult(days, System.currentTimeMillis())
        }
    }

    private fun fetchDailyAttendance(
        accessToken: String,
        date: LocalDate,
        semesterId: String
    ): Map<Int, String> {
        val payload = JSONObject()
            .put("date", date.toString())
            .put("semester", semesterId)
            .toString()

        val request = Request.Builder()
            .url("$ETLAB_BASE_URL/androidapp/app/attendancebydaydate")
            .header("User-Agent", ETLAB_USER_AGENT)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer $accessToken")
            .post(payload.toRequestBody(jsonMediaType))
            .build()

        baseClient.newCall(request).execute().use { response ->
            val bodyText = response.body?.string().orEmpty()
            val json = runCatching { JSONObject(bodyText) }.getOrNull()
                ?: throw ParsingException("Etlab returned an unexpected daily attendance response.")
            val attends = json.optJSONArray("attends") ?: return emptyMap()

            return buildMap {
                for (index in 0 until attends.length()) {
                    val entry = attends.optJSONObject(index) ?: continue
                    val hour = entry.optInt("hour", 0)
                    val attendance = entry.optString("attendance", "").trim()
                    if (hour > 0 && attendance.isNotEmpty()) put(hour, attendance)
                }
            }
        }
    }

    private fun etlabDayKey(date: LocalDate): String? = when (date.dayOfWeek.value) {
        1 -> "M"
        2 -> "T"
        3 -> "W"
        4 -> "Th"
        5 -> "F"
        else -> null
    }
}
