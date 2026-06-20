package com.reminderwidget

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract

object CalendarHelper {

    private data class CalInfo(val id: Long, val accountName: String, val accountType: String)

    /** Returns the new calendar event ID, or -1 on failure. */
    fun createReminder(
        context: Context,
        text: String,
        startMs: Long,
        durationMs: Long,
        eventColor: Int,
        rrule: String? = null,
    ): Long {
        val cal = findCalendarInfo(context) ?: return -1L
        val colorKey = if (eventColor != 0) findBestColorKey(context, cal.accountName, cal.accountType, eventColor) else null

        val eventValues = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, cal.id)
            put(CalendarContract.Events.TITLE, text)
            put(CalendarContract.Events.DESCRIPTION, text)
            put(CalendarContract.Events.DTSTART, startMs)
            put(CalendarContract.Events.EVENT_TIMEZONE, java.util.TimeZone.getDefault().id)
            put(CalendarContract.Events.HAS_ALARM, 1)
            if (rrule != null) {
                put(CalendarContract.Events.RRULE, rrule)
                put(CalendarContract.Events.DURATION, rfc5545Duration(durationMs))
            } else {
                put(CalendarContract.Events.DTEND, startMs + durationMs)
            }
            if (eventColor != 0) {
                put(CalendarContract.Events.EVENT_COLOR, eventColor)
                colorKey?.let { put(CalendarContract.Events.EVENT_COLOR_KEY, it) }
            }
        }

        val eventUri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, eventValues) ?: return -1L
        val eventId  = ContentUris.parseId(eventUri)

        context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, 0)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        })

        return eventId
    }

    private fun findCalendarInfo(context: Context): CalInfo? {
        val proj = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.IS_PRIMARY,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
        )
        val cursor = context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI, proj, null, null, null
        ) ?: return null
        return cursor.use {
            var primary: CalInfo? = null
            var fallback: CalInfo? = null
            while (it.moveToNext()) {
                val info = CalInfo(it.getLong(0), it.getString(2) ?: "", it.getString(3) ?: "")
                if (it.getInt(1) == 1) { primary = info; break }
                if (fallback == null) fallback = info
            }
            primary ?: fallback
        }
    }

    private fun findBestColorKey(context: Context, accountName: String, accountType: String, targetColor: Int): String? {
        val proj = arrayOf(CalendarContract.Colors.COLOR_KEY, CalendarContract.Colors.COLOR)
        val sel  = "${CalendarContract.Colors.ACCOUNT_NAME}=? AND ${CalendarContract.Colors.ACCOUNT_TYPE}=? AND ${CalendarContract.Colors.COLOR_TYPE}=?"
        val cursor = context.contentResolver.query(
            CalendarContract.Colors.CONTENT_URI, proj, sel,
            arrayOf(accountName, accountType, CalendarContract.Colors.TYPE_EVENT.toString()), null
        ) ?: return null
        var bestKey: String? = null
        var bestDist = Long.MAX_VALUE
        cursor.use {
            while (it.moveToNext()) {
                val key  = it.getString(0) ?: continue
                val dist = colorDist(it.getInt(1), targetColor)
                if (dist < bestDist) { bestDist = dist; bestKey = key }
            }
        }
        return bestKey
    }

    private fun colorDist(c1: Int, c2: Int): Long {
        fun comp(c: Int, shift: Int) = (c shr shift and 0xFF).toLong()
        val dr = comp(c1, 16) - comp(c2, 16)
        val dg = comp(c1,  8) - comp(c2,  8)
        val db = comp(c1,  0) - comp(c2,  0)
        return dr * dr + dg * dg + db * db
    }

    private fun rfc5545Duration(ms: Long): String {
        val days  = ms / 86_400_000L
        val hours = (ms % 86_400_000L) / 3_600_000L
        return when {
            days > 0 && hours == 0L -> "P${days}D"
            days > 0                -> "P${days}DT${hours}H"
            else                    -> "PT${ms / 3_600_000L}H"
        }
    }
}
