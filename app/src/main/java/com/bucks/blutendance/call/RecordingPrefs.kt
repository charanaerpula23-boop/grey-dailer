package com.bucks.blutendance.call

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists call-recording preference to SharedPreferences.
 * File is stored in app-internal storage only.
 */
object RecordingPrefs {

    private const val PREFS_NAME = "super_user_prefs"
    private const val KEY_RECORDING_ENABLED = "call_recording_enabled"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isRecordingEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_RECORDING_ENABLED, false)

    fun setRecordingEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_RECORDING_ENABLED, enabled).apply()
    }
}
