package com.linkease

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import org.json.JSONObject

object CalendarSyncHelper {

    private const val PREFS_NAME = "linkease_gcal"
    private const val KEY_EVENT_IDS = "event_ids"

    private fun hasPermission(context: Context) =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED

    private fun getDefaultCalendarId(context: Context): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID)
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI, projection,
            "${CalendarContract.Calendars.VISIBLE} = 1",
            null,
            "${CalendarContract.Calendars.IS_PRIMARY} DESC",
        )?.use { c -> if (c.moveToFirst()) return c.getLong(0) }
        return null
    }

    private fun loadEventIds(context: Context): MutableMap<Long, Long> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_EVENT_IDS, "{}") ?: "{}"
        return try {
            val obj = JSONObject(json)
            obj.keys().asSequence().associate { it.toLong() to obj.getLong(it) }.toMutableMap()
        } catch (_: Exception) { mutableMapOf() }
    }

    private fun saveEventIds(context: Context, map: Map<Long, Long>) {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k.toString(), v) }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_EVENT_IDS, obj.toString()).apply()
    }

    private fun buildValues(calId: Long, session: Session, clients: List<Client>, location: Location?): ContentValues {
        val tz = TimeZone.currentSystemDefault()
        val startMs = session.date.atTime(session.startTime).toInstant(tz).toEpochMilliseconds()
        val endMs   = session.date.atTime(session.endTime).toInstant(tz).toEpochMilliseconds()
        return ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calId)
            put(CalendarContract.Events.TITLE, clients.joinToString(", ") { it.name }.ifBlank { "Сесія" })
            put(CalendarContract.Events.DTSTART, startMs)
            put(CalendarContract.Events.DTEND, endMs)
            put(CalendarContract.Events.EVENT_LOCATION, location?.name ?: "")
            put(CalendarContract.Events.DESCRIPTION, session.notes)
            put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
        }
    }

    fun insertEvent(context: Context, session: Session, clients: List<Client>, location: Location?) {
        if (!hasPermission(context)) return
        val calId = getDefaultCalendarId(context) ?: return
        val uri = context.contentResolver.insert(
            CalendarContract.Events.CONTENT_URI,
            buildValues(calId, session, clients, location)
        ) ?: return
        val eventId = uri.lastPathSegment?.toLongOrNull() ?: return
        val ids = loadEventIds(context)
        ids[session.id] = eventId
        saveEventIds(context, ids)
    }

    fun updateEvent(context: Context, session: Session, clients: List<Client>, location: Location?) {
        if (!hasPermission(context)) return
        val ids = loadEventIds(context)
        val eventId = ids[session.id]
        if (eventId == null) {
            // No existing event for this session — create one
            insertEvent(context, session, clients, location)
            return
        }
        val calId = getDefaultCalendarId(context) ?: return
        val eventUri = Uri.withAppendedPath(CalendarContract.Events.CONTENT_URI, eventId.toString())
        context.contentResolver.update(eventUri, buildValues(calId, session, clients, location), null, null)
    }

    fun deleteEvent(context: Context, sessionId: Long) {
        if (!hasPermission(context)) return
        val ids = loadEventIds(context)
        val eventId = ids[sessionId] ?: return
        val eventUri = Uri.withAppendedPath(CalendarContract.Events.CONTENT_URI, eventId.toString())
        context.contentResolver.delete(eventUri, null, null)
        ids.remove(sessionId)
        saveEventIds(context, ids)
    }
}
