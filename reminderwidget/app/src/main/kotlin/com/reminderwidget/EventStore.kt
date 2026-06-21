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
        val isGroup: Boolean = false,
        val locationName: String? = null,
        val hasTime: Boolean = true,
    )

    fun add(context: Context, event: AppEvent) {
        val list = load(context).toMutableList()
        list.removeAll { it.id == event.id }
        list.add(0, event)
        persist(context, list.take(50))
        FirebaseSync.pushEvent(context, event)
    }

    fun remove(context: Context, localId: Long) {
        val list = load(context).toMutableList()
        val event = list.find { it.id == localId }
        list.removeAll { it.id == localId }
        persist(context, list)
        FirebaseSync.deleteEvent(context, localId)
        if (event?.isGroup == true) {
            GroupStore.getGroupId(context)?.let { gid -> FirebaseSync.deleteGroupEvent(gid, localId) }
        }
    }

    fun clear(context: Context) {
        val all = load(context)
        val gid = GroupStore.getGroupId(context)
        all.forEach { e ->
            FirebaseSync.deleteEvent(context, e.id)
            if (e.isGroup && gid != null) FirebaseSync.deleteGroupEvent(gid, e.id)
        }
        persist(context, emptyList())
    }

    fun deleteNonFavorites(context: Context) {
        val gid = GroupStore.getGroupId(context)
        load(context).filter { !it.favorite }.forEach { e ->
            FirebaseSync.deleteEvent(context, e.id)
            if (e.isGroup && gid != null) FirebaseSync.deleteGroupEvent(gid, e.id)
        }
        persist(context, load(context).filter { it.favorite })
    }

    fun addSilent(context: Context, event: AppEvent) {
        val list = load(context).toMutableList()
        list.removeAll { it.id == event.id }
        list.add(0, event)
        persist(context, list.take(50))
    }

    fun removeSilent(context: Context, localId: Long) {
        val list = load(context).toMutableList()
        list.removeAll { it.id == localId }
        persist(context, list)
    }

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

    fun markGroup(context: Context, localId: Long, isGroup: Boolean) {
        patch(context, localId) { copy(isGroup = isGroup) }
    }

    fun setLocation(context: Context, localId: Long, locationName: String?) {
        val list = load(context).toMutableList()
        val idx = list.indexOfFirst { it.id == localId }
        if (idx < 0) return
        list[idx] = list[idx].copy(locationName = locationName)
        persist(context, list)
        FirebaseSync.pushEvent(context, list[idx])
        if (locationName != null) {
            val loc = LocationsStore.get(context, locationName)
            if (loc != null) GeofenceManager.registerForEvent(context, list[idx], loc.lat, loc.lng)
        } else {
            GeofenceManager.unregisterForEvent(context, localId)
        }
    }

    fun clearLocation(context: Context, locationName: String) {
        val list = load(context).toMutableList()
        var changed = false
        list.forEachIndexed { i, e ->
            if (e.locationName == locationName) {
                list[i] = e.copy(locationName = null)
                changed = true
            }
        }
        if (changed) persist(context, list)
    }

    fun update(context: Context, event: AppEvent) {
        patch(context, event.id) { event }
    }

    private fun patch(context: Context, localId: Long, transform: AppEvent.() -> AppEvent) {
        val list = load(context).toMutableList()
        val idx = list.indexOfFirst { it.id == localId }
        if (idx >= 0) {
            list[idx] = list[idx].transform()
            persist(context, list)
            FirebaseSync.pushEvent(context, list[idx])
            if (list[idx].isGroup) {
                GroupStore.getGroupId(context)?.let { gid -> FirebaseSync.pushGroupEvent(gid, list[idx]) }
            }
        }
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
                    isGroup         = if (o.has("isGroup"))   o.getBoolean("isGroup")   else false,
                    locationName    = if (o.has("locationName")) o.getString("locationName").takeIf { it.isNotBlank() } else null,
                    hasTime         = if (o.has("hasTime")) o.getBoolean("hasTime") else true,
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
                if (e.isGroup)   put("isGroup", true)
                e.locationName?.let { put("locationName", it) }
                if (!e.hasTime) put("hasTime", false)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }
}
