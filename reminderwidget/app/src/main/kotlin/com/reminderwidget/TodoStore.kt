package com.reminderwidget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object TodoStore {
    data class Item(val id: Long, val text: String)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("todo_store", Context.MODE_PRIVATE)

    fun loadTitle(ctx: Context): String =
        prefs(ctx).getString("title", todayLabel()) ?: todayLabel()

    fun saveTitle(ctx: Context, title: String) {
        prefs(ctx).edit().putString("title", title).apply()
        FirebaseSync.pushTodo(ctx, title, load(ctx))
    }

    fun load(ctx: Context): List<Item> {
        val raw = prefs(ctx).getString("items", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                Item(o.getLong("id"), o.getString("text"))
            }
        } catch (_: Exception) { emptyList() }
    }

    fun add(ctx: Context, text: String): List<Item> {
        val items = load(ctx).toMutableList()
        items.add(Item(System.currentTimeMillis(), text.trim()))
        save(ctx, items)
        FirebaseSync.pushTodo(ctx, loadTitle(ctx), items)
        return items
    }

    fun remove(ctx: Context, id: Long): List<Item> {
        val items = load(ctx).filter { it.id != id }
        save(ctx, items)
        FirebaseSync.pushTodo(ctx, loadTitle(ctx), items)
        return items
    }

    fun clear(ctx: Context) {
        save(ctx, emptyList())
        FirebaseSync.pushTodo(ctx, loadTitle(ctx), emptyList())
    }

    fun mergeFromRemote(ctx: Context, title: String, items: List<Item>) {
        prefs(ctx).edit().putString("title", title).apply()
        save(ctx, items)
    }

    private fun save(ctx: Context, items: List<Item>) {
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("id", it.id).put("text", it.text)) }
        prefs(ctx).edit().putString("items", arr.toString()).apply()
    }

    fun todayLabel(): String {
        val cal = Calendar.getInstance()
        val day = cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale("uk"))
            ?.replaceFirstChar { it.uppercase() } ?: ""
        val date = SimpleDateFormat("d MMMM", Locale("uk")).format(cal.time)
        return "$day, $date"
    }

    fun labelForCalendar(cal: Calendar): String {
        val day = cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale("uk"))
            ?.replaceFirstChar { it.uppercase() } ?: ""
        val date = SimpleDateFormat("d MMMM", Locale("uk")).format(cal.time)
        return "$day, $date"
    }
}
