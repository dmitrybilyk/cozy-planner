package com.reminderwidget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object AgendaStore {
    private const val PREFS = "agenda_store"
    private const val KEY   = "items"

    data class AgendaItem(
        val id: Long,
        val title: String,
        val startMs: Long,
        val completed: Boolean = false,
    )

    fun add(context: Context, item: AgendaItem) {
        val list = load(context).toMutableList()
        list.removeAll { it.id == item.id }
        val idx = list.indexOfFirst { it.startMs > item.startMs }
        if (idx == -1) list.add(item) else list.add(idx, item)
        persist(context, list)
    }

    fun remove(context: Context, id: Long) =
        persist(context, load(context).filter { it.id != id })

    fun update(context: Context, item: AgendaItem) {
        val list = load(context).toMutableList()
        val idx = list.indexOfFirst { it.id == item.id }
        if (idx >= 0) { list[idx] = item; persist(context, list) }
    }

    fun markCompleted(context: Context, id: Long) {
        val list = load(context).toMutableList()
        val idx  = list.indexOfFirst { it.id == id }
        if (idx >= 0) { list[idx] = list[idx].copy(completed = true); persist(context, list) }
    }

    fun clear(context: Context) = persist(context, emptyList())

    fun load(context: Context): List<AgendaItem> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]"
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                AgendaItem(
                    id        = o.getLong("id"),
                    title     = o.getString("title"),
                    startMs   = o.getLong("startMs"),
                    completed = if (o.has("completed")) o.getBoolean("completed") else false,
                )
            }
        } catch (_: Exception) { emptyList() }
    }

    private fun persist(context: Context, items: List<AgendaItem>) {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("startMs", item.startMs)
                if (item.completed) put("completed", true)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }
}
