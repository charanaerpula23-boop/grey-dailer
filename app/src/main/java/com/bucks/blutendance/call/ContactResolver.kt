package com.bucks.blutendance.call

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract

/**
 * Resolves phone numbers to saved contact names using the system
 * Contacts provider (PhoneLookup).
 *
 * Results are cached for the lifetime of the process so repeated
 * lookups (notification → in-call UI) are instantaneous.
 */
object ContactResolver {

    /** Simple in-memory cache: normalized-number → display name */
    private val cache = mutableMapOf<String, String?>()

    /**
     * Look up a phone number and return the saved contact name,
     * or `null` if no match is found.
     */
    @SuppressLint("MissingPermission")
    fun resolveContactName(context: Context, phoneNumber: String?): String? {
        if (phoneNumber.isNullOrBlank()) return null

        val key = normalizeNumber(phoneNumber)
        if (cache.containsKey(key)) return cache[key]

        val name = queryContactName(context, phoneNumber)
        cache[key] = name
        return name
    }

    /**
     * Returns the contact display name if found, otherwise returns
     * the original phone number. Never returns null.
     */
    fun getDisplayName(context: Context, phoneNumber: String?): String {
        if (phoneNumber.isNullOrBlank()) return "Unknown"
        return resolveContactName(context, phoneNumber) ?: phoneNumber
    }

    /** Clear the cache (e.g. if contacts change). */
    fun clearCache() {
        cache.clear()
    }

    /* ── Internal ─────────────────────────────────────────────── */

    private fun queryContactName(context: Context, phoneNumber: String): String? {
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(phoneNumber)
            )
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIdx >= 0) cursor.getString(nameIdx) else null
                } else null
            }
        } catch (e: Exception) {
            // SecurityException if READ_CONTACTS not granted, etc.
            null
        }
    }

    /** Strip spaces, dashes, parentheses for cache key normalisation. */
    private fun normalizeNumber(number: String): String {
        return number.replace(Regex("[\\s\\-()]+"), "")
    }
}
