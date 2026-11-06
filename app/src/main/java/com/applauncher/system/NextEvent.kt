package com.applauncher.system

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract

data class CalendarEvent(
    val id: Long,
    val title: String,
    val begin: Long,
    val allDay: Boolean,
) {
    fun openIntent(): Intent = Intent(
        Intent.ACTION_VIEW,
        ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id),
    )
}

/** The next calendar event in the coming week. */
object NextEvent {

    fun hasPermission(context: Context) =
        context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Call off the main thread. */
    fun load(context: Context): CalendarEvent? {
        if (!hasPermission(context)) return null
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, now - 12 * 60 * 60 * 1000L) // include all-day events from today
            ContentUris.appendId(it, now + 7 * 24 * 60 * 60 * 1000L)
        }.build()
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(
                    CalendarContract.Instances.EVENT_ID,
                    CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END,
                    CalendarContract.Instances.ALL_DAY,
                ),
                null, null, "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val end = c.getLong(3)
                    if (end < now) continue // already over
                    val title = c.getString(1)?.takeIf { it.isNotBlank() } ?: "(No title)"
                    return@use CalendarEvent(c.getLong(0), title, c.getLong(2), c.getInt(4) == 1)
                }
                null
            }
        }.getOrNull()
    }
}
