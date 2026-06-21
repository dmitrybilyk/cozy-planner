package com.reminderwidget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object LocationsStore {
    data class AppLocation(val name: String, val lat: Double, val lng: Double)

    private const val PREFS = "locations_store"
    private const val KEY   = "locations"

    fun load(ctx: Context): List<AppLocation> {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                AppLocation(o.getString("name"), o.getDouble("lat"), o.getDouble("lng"))
            }
        } catch (_: Exception) { emptyList() }
    }

    fun get(ctx: Context, name: String): AppLocation? = load(ctx).find { it.name == name }

    fun add(ctx: Context, loc: AppLocation) {
        val list = load(ctx).toMutableList()
        list.removeAll { it.name == loc.name }
        list.add(loc)
        save(ctx, list)
    }

    fun remove(ctx: Context, name: String) {
        save(ctx, load(ctx).filter { it.name != name })
    }

    fun rename(ctx: Context, oldName: String, newName: String) {
        save(ctx, load(ctx).map { if (it.name == oldName) it.copy(name = newName) else it })
    }

    private fun save(ctx: Context, list: List<AppLocation>) {
        val arr = JSONArray()
        list.forEach { loc ->
            arr.put(JSONObject().put("name", loc.name).put("lat", loc.lat).put("lng", loc.lng))
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, arr.toString()).apply()
    }
}
