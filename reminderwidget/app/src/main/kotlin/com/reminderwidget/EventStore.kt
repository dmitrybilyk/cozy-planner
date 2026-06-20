package com.reminderwidget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object EventStore {
    private const val PREFS = "event_store"
    private const val KEY   = "events"

    data class AppEvent(
        val id: Long,
        val title: String,
        val startMs: Long,
        val durationMs: Long = 7 * 24 * 60 * 60_000L,
        val rrule: String? = null,
        val calendarEventId: Long = -1L,
        val completed: Boolean = false,
        val favorite: Boolean = false,
    )

    fun add(context: Context, event: AppEvent) {
        val list = load(context).toMutableList()
        list.removeAll { it.id == event.id }
        list.add(0, event)
        persist(context, list.take(50))
    }

    fun remove(context: Context, localId: Long) {
        val list = load(context).toMutableList()
        list.removeAll { it.id == localId }
        persist(context, list)
    }

    fun clear(context: Context) = persist(context, emptyList())

    fun updateCalendarId(context: Context, localId: Long, calendarEventId: Long) {
        patch(context, localId) { copy(calendarEventId = calendarEventId) }
    }

    fun updateStartMs(context: Context, localId: Long, newStartMs: Long) {
        patch(context, localId) { copy(startMs = newStartMs) }
    }

    fun markCompleted(context: Context, localId: Long) {
        patch(context, localId) { copy(completed = true) }
    }

    fun markFavorite(context: Context, localId: Long, favorite: Boolean) {
        patch(context, localId) { copy(favorite = favorite) }
    }

    fun update(context: Context, event: AppEvent) {
        patch(context, event.id) { event }
    }

    private fun patch(context: Context, localId: Long, transform: AppEvent.() -> AppEvent) {
        val list = load(context).toMutableList()
        val idx = list.indexOfFirst { it.id == localId }
        if (idx >= 0) { list[idx] = list[idx].transform(); persist(context, list) }
    }

    fun load(context: Context): List<AppEvent> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                AppEvent(
                    id              = if (o.has("id")) o.getLong("id") else o.getLong("calId"),
                    title           = o.getString("title"),
                    startMs         = o.getLong("startMs"),
                    durationMs      = if (o.has("durationMs")) o.getLong("durationMs") else 7 * 24 * 60 * 60_000L,
                    rrule           = if (o.has("rrule")) o.getString("rrule") else null,
                    calendarEventId = if (o.has("calendarEventId")) o.getLong("calendarEventId") else -1L,
                    completed       = if (o.has("completed")) o.getBoolean("completed") else false,
                    favorite        = if (o.has("favorite"))  o.getBoolean("favorite")  else false,
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun persist(context: Context, events: List<AppEvent>) {
        val arr = JSONArray()
        events.forEach { e ->
            arr.put(JSONObject().apply {
                put("id", e.id)
                put("title", e.title)
                put("startMs", e.startMs)
                put("durationMs", e.durationMs)
                e.rrule?.let { put("rrule", it) }
                put("calendarEventId", e.calendarEventId)
                if (e.completed) put("completed", true)
                if (e.favorite)  put("favorite", true)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }
}
