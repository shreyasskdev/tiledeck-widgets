package io.github.shreyasskdev.tiledeck.data

import org.json.JSONArray
import org.json.JSONObject

data class TimetableResult(
    val timetable: List<TimetableDayResult>,
    val updatedAt: Long
) {
    fun toJson(): String {
        val json = JSONObject()
        val daysArray = JSONArray()
        timetable.forEach { daysArray.put(it.toJson()) }
        json.put("timetable", daysArray)
        json.put("updatedAt", updatedAt)
        return json.toString()
    }

    companion object {
        fun fromJson(jsonString: String): TimetableResult {
            val json = JSONObject(jsonString)
            val timetableJson = json.optJSONArray("timetable") ?: JSONArray()
            val days = mutableListOf<TimetableDayResult>()
            for (i in 0 until timetableJson.length()) {
                val dayJson = timetableJson.optJSONObject(i)
                if (dayJson != null) {
                    days.add(TimetableDayResult.fromJson(dayJson))
                }
            }
            val updatedAt = json.optLong("updatedAt", 0L)
            return TimetableResult(days, updatedAt)
        }
    }
}

data class TimetableDayResult(
    val day: String,
    val subjects: List<TimetableSubjectResult>
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("day", day)
        val subArray = JSONArray()
        subjects.forEach { subArray.put(it.toJson()) }
        json.put("sub", subArray)
        return json
    }

    companion object {
        fun fromJson(json: JSONObject): TimetableDayResult {
            val day = json.optString("day", "")
            val subjectsJson = json.optJSONArray("sub") ?: JSONArray()
            val subjects = mutableListOf<TimetableSubjectResult>()
            for (i in 0 until subjectsJson.length()) {
                val subJson = subjectsJson.optJSONObject(i)
                if (subJson != null) {
                    subjects.add(TimetableSubjectResult.fromJson(subJson))
                }
            }
            return TimetableDayResult(day, subjects)
        }
    }
}

data class TimetableSubjectResult(
    val hour: Int,
    val subject: String,
    val attendanceStatus: String? = null
) {
    fun toJson(): JSONObject {
        val json = JSONObject()
        json.put("hour", hour)
        json.put("subject", subject)
        attendanceStatus?.let { json.put("attendanceStatus", it) }
        return json
    }

    companion object {
        fun fromJson(json: JSONObject): TimetableSubjectResult {
            val hour = json.optInt("hour", 0)
            val subject = json.optString("subject", "")
            val attendanceStatus = json
                .optString("attendanceStatus", "")
                .trim()
                .takeIf { it.isNotEmpty() }
            return TimetableSubjectResult(hour, subject, attendanceStatus)
        }
    }
}
