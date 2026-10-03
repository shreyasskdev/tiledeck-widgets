package io.github.shreyasskdev.tiledeck.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONObject

/**
 * Stores the Etlab username/password and the last fetched attendance result
 * in EncryptedSharedPreferences, so credentials never sit in plain text on
 * the device. Also stores optional per-subject name overrides -- Etlab's
 * API already gives us real subject names, but you can shorten/rename them
 * here if you want (e.g. "Analog & Digital Communication" -> "ADC").
 *
 * NOTE: EncryptedSharedPreferences.create() must not be called more than
 * once per file per process -- doing so can return a stale snapshot and it
 * is also expensive. We therefore build the SharedPreferences lazily and
 * cache it in a companion object, so both the Activity and the Glance widget
 * share the same in-memory instance.
 */
class AttendancePrefs(context: Context) {

    private val prefs: SharedPreferences = getOrCreate(context.applicationContext)

    // ── Credentials ──────────────────────────────────────────────────────────

    fun saveCredentials(username: String, password: String) {
        prefs.edit()
            .putString(KEY_USERNAME, username)
            .putString(KEY_PASSWORD, password)
            .commit()   // synchronous: widget reads this immediately after
    }

    fun getUsername(): String? = prefs.getString(KEY_USERNAME, null)
    fun getPassword(): String? = prefs.getString(KEY_PASSWORD, null)
    fun hasCredentials(): Boolean = getUsername() != null && getPassword() != null

    // ── Last fetched result ──────────────────────────────────────────────────

    fun saveLastResult(result: AttendanceResult) {
        prefs.edit()
            .putString(KEY_LAST_RESULT, result.toJson())
            .putLong(KEY_LAST_UPDATED, System.currentTimeMillis())
            .commit()   // synchronous
    }

    fun saveLastTimetable(result: TimetableResult) {
        prefs.edit()
            .putString(KEY_LAST_TIMETABLE, result.toJson())
            .commit()   // synchronous
    }

    fun getLastResult(): AttendanceResult? =
        prefs.getString(KEY_LAST_RESULT, null)?.let {
            runCatching { AttendanceResult.fromJson(it) }.getOrNull()
        }

    fun getLastTimetable(): TimetableResult? =
        prefs.getString(KEY_LAST_TIMETABLE, null)?.let {
            runCatching { TimetableResult.fromJson(it) }.getOrNull()
        }

    fun getLastUpdatedText(): String {
        val ts = prefs.getLong(KEY_LAST_UPDATED, 0L)
        if (ts == 0L) return "Not set up yet"
        val minutesAgo = (System.currentTimeMillis() - ts) / 60000
        return when {
            minutesAgo < 1 -> "Updated just now"
            minutesAgo < 60 -> "Updated ${minutesAgo}m ago"
            else -> "Updated ${minutesAgo / 60}h ago"
        }
    }

    // ── Subject name overrides ───────────────────────────────────────────────

    /** Optional per-subject display-name override, keyed by course code. */
    fun saveSubjectNames(names: Map<String, String>) {
        val json = JSONObject()
        names.forEach { (code, name) -> json.put(code, name) }
        prefs.edit()
            .putString(KEY_SUBJECT_NAMES, json.toString())
            .commit()   // synchronous
    }

    fun getSubjectNames(): Map<String, String> {
        val raw = prefs.getString(KEY_SUBJECT_NAMES, null) ?: return emptyMap()
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return emptyMap()
        return json.keys().asSequence().associateWith { json.getString(it) }
    }

    fun saveUseCustomNames(useCustom: Boolean) {
        prefs.edit()
            .putBoolean(KEY_USE_CUSTOM_NAMES, useCustom)
            .commit()   // synchronous
    }

    fun getUseCustomNames(): Boolean =
        prefs.getBoolean(KEY_USE_CUSTOM_NAMES, true)

    // ── Background refresh frequency ──────────────────────────────────────────

    fun saveRefreshIntervalMinutes(minutes: Long) {
        prefs.edit()
            .putLong(KEY_REFRESH_INTERVAL, minutes)
            .commit()   // synchronous
    }

    fun getRefreshIntervalMinutes(): Long =
        prefs.getLong(KEY_REFRESH_INTERVAL, 60L) // Default 60 mins (1 hour)


    private companion object {
        const val PREFS_FILE = "etlab_attendance_prefs"

        const val KEY_USERNAME = "username"
        const val KEY_PASSWORD = "password"
        const val KEY_LAST_RESULT = "last_result"
        const val KEY_LAST_TIMETABLE = "last_timetable"
        const val KEY_LAST_UPDATED = "last_updated"
        const val KEY_SUBJECT_NAMES = "subject_names"
        const val KEY_USE_CUSTOM_NAMES = "use_custom_names"
        const val KEY_REFRESH_INTERVAL = "refresh_interval_minutes"

        // Volatile because it may be touched from Activity + Worker + Glance
        // threads. Double-checked locking keeps us to one instance per process.
        @Volatile
        private var instance: SharedPreferences? = null

        fun getOrCreate(appContext: Context): SharedPreferences {
            instance?.let { return it }
            synchronized(this) {
                instance?.let { return it }

                val masterKey = MasterKey.Builder(appContext)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()

                val created = EncryptedSharedPreferences.create(
                    appContext,
                    PREFS_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
                instance = created
                return created
            }
        }
    }
}