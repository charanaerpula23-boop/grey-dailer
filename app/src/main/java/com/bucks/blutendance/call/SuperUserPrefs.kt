package com.bucks.blutendance.call

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists Super User unlock code in app-internal SharedPreferences.
 */
object SuperUserPrefs {

    private const val PREFS_NAME = "super_user_prefs"
    private const val KEY_SUPER_USER_CODE = "super_user_code"
    private const val DEFAULT_SUPER_USER_CODE = "0909090909"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSuperUserCode(context: Context): String =
        prefs(context).getString(KEY_SUPER_USER_CODE, DEFAULT_SUPER_USER_CODE)
            ?: DEFAULT_SUPER_USER_CODE

    fun isSuperUserCode(context: Context, candidate: String): Boolean {
        val normalized = normalize(candidate)
        val saved = normalize(getSuperUserCode(context))
        return normalized == saved
    }

    fun setSuperUserCode(context: Context, newCode: String): Boolean {
        val normalized = normalize(newCode)
        if (normalized.length < 4) return false
        prefs(context).edit().putString(KEY_SUPER_USER_CODE, normalized).apply()
        return true
    }

    private fun normalize(value: String): String =
        value.filter { it.isDigit() || it == '*' || it == '#' }
}
