package com.timely.app

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists all user settings using SharedPreferences.
 * Days are stored as 1–7 where 1=Monday … 7=Sunday.
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "timely_settings"
        private const val KEY_SELECTED_DAYS = "selected_days"
        private const val KEY_START_HOUR = "start_hour"
        private const val KEY_START_MINUTE = "start_minute"
        private const val KEY_END_HOUR = "end_hour"
        private const val KEY_END_MINUTE = "end_minute"
        private const val KEY_INTERVAL = "interval_minutes"
        private const val KEY_IS_RUNNING = "is_running"
    }

    /** Selected days: 1=Mon, 2=Tue, 3=Wed, 4=Thu, 5=Fri, 6=Sat, 7=Sun */
    var selectedDays: Set<Int>
        get() {
            val stored = prefs.getStringSet(KEY_SELECTED_DAYS, setOf("1", "2", "3", "4", "5"))
            return stored?.mapNotNull { it.toIntOrNull() }?.toSet() ?: setOf(1, 2, 3, 4, 5)
        }
        set(value) {
            prefs.edit()
                .putStringSet(KEY_SELECTED_DAYS, value.map { it.toString() }.toSet())
                .apply()
        }

    var startHour: Int
        get() = prefs.getInt(KEY_START_HOUR, 7)
        set(value) { prefs.edit().putInt(KEY_START_HOUR, value).apply() }

    var startMinute: Int
        get() = prefs.getInt(KEY_START_MINUTE, 0)
        set(value) { prefs.edit().putInt(KEY_START_MINUTE, value).apply() }

    var endHour: Int
        get() = prefs.getInt(KEY_END_HOUR, 22)
        set(value) { prefs.edit().putInt(KEY_END_HOUR, value).apply() }

    var endMinute: Int
        get() = prefs.getInt(KEY_END_MINUTE, 0)
        set(value) { prefs.edit().putInt(KEY_END_MINUTE, value).apply() }

    /** Announcement interval in minutes (1–120, default 30) */
    var intervalMinutes: Int
        get() = prefs.getInt(KEY_INTERVAL, 30)
        set(value) { prefs.edit().putInt(KEY_INTERVAL, value).apply() }

    var isRunning: Boolean
        get() = prefs.getBoolean(KEY_IS_RUNNING, false)
        set(value) { prefs.edit().putBoolean(KEY_IS_RUNNING, value).apply() }
}
