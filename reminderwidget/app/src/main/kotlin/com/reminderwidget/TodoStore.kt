package com.reminderwidget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object TodoStore {
    data class Item(
        val id: Long,
        val text: String,
        val reminderMinutes: Int? = null,
        val placeName: String? = null,
        val isGroup: Boolean = false,
        val done: Boolean = false,
        val sortOrder: Long = id,
    )

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("todo_store", Context.MODE_PRIVATE)

    // ── Title ─────────────────────────────────────────────────────────────────

    fun loadTitle(ctx: Context): String =
        prefs(ctx).getString("title", todayLabel()) ?: todayLabel()

    fun saveTitle(ctx: Context, title: String) {
        prefs(ctx).edit().putString("title", title).apply()
        pushAll(ctx)
    }

    // ── Items ─────────────────────────────────────────────────────────────────

    fun load(ctx: Context): List<Item> {
        val raw = prefs(ctx).getString("items", "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                val rm      = if (o.has("rm")) o.optInt("rm", -1).let { v -> if (v >= 0) v else null } else null
                val place   = if (o.has("place")) o.optString("place", null) else null
                val isGroup = if (o.has("isGroup")) o.getBoolean("isGroup") else false
                val done    = if (o.has("done"))    o.getBoolean("done")    else false
                Item(o.getLong("id"), o.getString("text"), rm, place, isGroup, done)
            }
        } catch (_: Exception) { emptyList() }
    }

    fun add(ctx: Context, text: String): List<Item> {
        val items = load(ctx).toMutableList()
        items.add(Item(System.currentTimeMillis(), text.trim()))
        save(ctx, items); pushAll(ctx)
        return items
    }

    fun remove(ctx: Context, id: Long): List<Item> {
        val items = load(ctx).filter { it.id != id }
        save(ctx, items); pushAll(ctx)
        return items
    }

    fun clear(ctx: Context) {
        save(ctx, emptyList()); pushAll(ctx)
    }

    fun reorder(ctx: Context, items: List<Item>): List<Item> {
        save(ctx, items); pushAll(ctx)
        return items
    }

    fun setReminderMinutes(ctx: Context, id: Long, minutes: Int?): List<Item> {
        val items = load(ctx).map { if (it.id == id) it.copy(reminderMinutes = minutes) else it }
        save(ctx, items); pushAll(ctx); return items
    }

    fun markDone(ctx: Context, id: Long, done: Boolean): List<Item> {
        val items = load(ctx).map { if (it.id == id) it.copy(done = done) else it }
        save(ctx, items); pushAll(ctx); return items
    }

    fun assignPlace(ctx: Context, itemId: Long, placeName: String?): List<Item> {
        val items = load(ctx).map { if (it.id == itemId) it.copy(placeName = placeName) else it }
        save(ctx, items); pushAll(ctx)
        return items
    }

    // ── Places ────────────────────────────────────────────────────────────────

    fun loadPlaces(ctx: Context): List<String> =
        prefs(ctx).getString("places", null)
            ?.split("\n")?.filter { it.isNotBlank() }?.distinct()
            ?: emptyList()

    fun addPlace(ctx: Context, text: String): List<String> {
        val places = loadPlaces(ctx).toMutableList()
        val p = text.trim().replaceFirstChar { it.uppercase() }
        if (p.isNotBlank() && !places.contains(p)) { places.add(p); savePlaces(ctx, places); pushAll(ctx) }
        return places
    }

    fun removePlace(ctx: Context, place: String): List<String> {
        val p = place.trim().replaceFirstChar { it.uppercase() }
        val places = loadPlaces(ctx).filter { it != p }
        savePlaces(ctx, places)
        // unassign items that belonged to this place
        val items = load(ctx).map { if (it.placeName == p) it.copy(placeName = null) else it }
        save(ctx, items); pushAll(ctx)
        return places
    }

    fun renamePlace(ctx: Context, oldName: String, newName: String): List<String> {
        val old = oldName.trim().replaceFirstChar { it.uppercase() }
        val new = newName.trim().replaceFirstChar { it.uppercase() }
        val places = loadPlaces(ctx).map { if (it == old) new else it }
        savePlaces(ctx, places)
        val items = load(ctx).map { if (it.placeName == old) it.copy(placeName = new) else it }
        save(ctx, items); pushAll(ctx)
        return places
    }

    fun reorderPlaces(ctx: Context, places: List<String>): List<String> {
        savePlaces(ctx, places.distinct()); pushAll(ctx)
        return places
    }

    fun mergeFromRemote(ctx: Context, title: String, items: List<Item>, places: List<String> = emptyList()) {
        prefs(ctx).edit().putString("title", title).apply()
        save(ctx, items)
        if (places.isNotEmpty()) savePlaces(ctx, places)
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun save(ctx: Context, items: List<Item>) {
        val arr = JSONArray()
        items.forEach { item ->
            val obj = JSONObject().put("id", item.id).put("text", item.text)
            item.reminderMinutes?.let { obj.put("rm", it) }
            item.placeName?.let { obj.put("place", it) }
            if (item.isGroup) obj.put("isGroup", true)
            if (item.done)    obj.put("done", true)
            arr.put(obj)
        }
        prefs(ctx).edit().putString("items", arr.toString()).apply()
    }

    private fun savePlaces(ctx: Context, places: List<String>) {
        prefs(ctx).edit().putString("places", places.joinToString("\n")).apply()
    }

    private fun pushAll(ctx: Context) {
        FirebaseSync.pushTodo(ctx, loadTitle(ctx), load(ctx), loadPlaces(ctx))
    }

    // ── Date helpers ──────────────────────────────────────────────────────────

    fun todayLabel(): String = labelForCalendar(Calendar.getInstance())

    fun labelForCalendar(cal: Calendar): String {
        val day = cal.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.LONG, Locale("uk"))
            ?.replaceFirstChar { it.uppercase() } ?: ""
        val date = SimpleDateFormat("d MMMM", Locale("uk")).format(cal.time)
        return "$day, $date"
    }
}
