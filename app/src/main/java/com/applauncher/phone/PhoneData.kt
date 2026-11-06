package com.applauncher.phone

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.telecom.Call
import android.os.Build

data class RecentCall(
    val number: String,
    val name: String?,
    val type: Int,     // CallLog.Calls.INCOMING_TYPE / OUTGOING_TYPE / MISSED_TYPE ...
    val time: Long,
    val durationSec: Long,
)

data class ContactNumber(
    val contactId: Long,
    val name: String,
    val number: String,
)

/** Reads the call log and contacts, and finds a caller's name. */
object PhoneData {

    private val nameCache = HashMap<String, String?>()

    fun has(context: Context, permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    /** Most recent calls first. Call off the main thread. */
    fun recentCalls(context: Context, max: Int = 200): List<RecentCall> {
        if (!has(context, Manifest.permission.READ_CALL_LOG)) return emptyList()
        val result = ArrayList<RecentCall>()
        runCatching {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(
                    CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.TYPE,
                    CallLog.Calls.DATE, CallLog.Calls.DURATION,
                ),
                null, null, "${CallLog.Calls.DATE} DESC",
            )?.use { c ->
                while (c.moveToNext() && result.size < max) {
                    result += RecentCall(
                        number = c.getString(0).orEmpty(),
                        name = c.getString(1)?.takeIf { it.isNotBlank() },
                        type = c.getInt(2),
                        time = c.getLong(3),
                        durationSec = c.getLong(4),
                    )
                }
            }
        }
        return result
    }

    /** Every contact phone number, A–Z. Call off the main thread. */
    fun contacts(context: Context): List<ContactNumber> {
        if (!has(context, Manifest.permission.READ_CONTACTS)) return emptyList()
        val result = ArrayList<ContactNumber>()
        val seen = HashSet<String>()
        runCatching {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                ),
                null, null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} COLLATE NOCASE ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val name = c.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                    val number = c.getString(2)?.takeIf { it.isNotBlank() } ?: continue
                    // Same number saved twice (e.g. with and without spaces): keep one.
                    if (seen.add("$id|${digitsOnly(number)}")) result += ContactNumber(id, name, number)
                }
            }
        }
        return result
    }

    /** Starred contacts (one number each), A–Z. Call off the main thread. */
    fun favourites(context: Context): List<ContactNumber> {
        if (!has(context, Manifest.permission.READ_CONTACTS)) return emptyList()
        val result = ArrayList<ContactNumber>()
        val seen = HashSet<Long>()
        runCatching {
            context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                ),
                "${ContactsContract.CommonDataKinds.Phone.STARRED} = 1", null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} COLLATE NOCASE ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val name = c.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                    val number = c.getString(2)?.takeIf { it.isNotBlank() } ?: continue
                    if (seen.add(id)) result += ContactNumber(id, name, number)
                }
            }
        }
        return result
    }

    /** Back-to-back calls with the same number become one row, e.g. "Hirthik (4)". */
    fun group(recents: List<RecentCall>): List<Pair<RecentCall, Int>> {
        val groups = ArrayList<Pair<RecentCall, Int>>()
        for (call in recents) {
            val last = groups.lastOrNull()
            if (last != null && digitsOnly(last.first.number) == digitsOnly(call.number) &&
                (last.first.type == CallLog.Calls.MISSED_TYPE) == (call.type == CallLog.Calls.MISSED_TYPE)
            ) {
                groups[groups.lastIndex] = last.first to last.second + 1
            } else {
                groups += call to 1
            }
        }
        return groups
    }

    /** Caller's contact name, or null if unknown. Call off the main thread. */
    fun nameFor(context: Context, number: String): String? {
        if (number.isBlank()) return null
        synchronized(nameCache) { if (number in nameCache) return nameCache[number] }
        if (!has(context, Manifest.permission.READ_CONTACTS)) return null
        val name = runCatching {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
            context.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
        synchronized(nameCache) { nameCache[number] = name }
        return name
    }

    fun numberOf(call: Call): String =
        call.details.handle?.schemeSpecificPart
            ?: call.details.gatewayInfo?.originalAddress?.schemeSpecificPart
            ?: ""

    /** Name Android already knows for the call (Android 11+), if any. */
    fun knownName(call: Call): String? {
        val details = call.details
        val fromContacts = if (Build.VERSION.SDK_INT >= 30) details.contactDisplayName else null
        return (fromContacts ?: details.callerDisplayName)?.takeIf { it.isNotBlank() }
    }

    fun digitsOnly(number: String) = number.filter { it.isDigit() || it == '+' }
}
