package com.reminderwidget

import android.content.Context
import org.json.JSONObject

object PlaceCoordsStore {
    private const val PREFS = "place_coords"
    private const val KEY   = "coords"

    fun get(ctx: Context, place: String): Pair<Double, Double>? {
        val obj = load(ctx)
        if (!obj.has(place)) return null
        val arr = obj.getJSONArray(place)
        return Pair(arr.getDouble(0), arr.getDouble(1))
    }

    fun set(ctx: Context, place: String, lat: Double, lng: Double) {
        val obj = load(ctx)
        obj.put(place, org.json.JSONArray().put(lat).put(lng))
        save(ctx, obj)
    }

    fun remove(ctx: Context, place: String) {
        val obj = load(ctx)
        obj.remove(place)
        save(ctx, obj)
    }

    fun rename(ctx: Context, oldName: String, newName: String) {
        val coords = get(ctx, oldName) ?: return
        remove(ctx, oldName)
        set(ctx, newName, coords.first, coords.second)
    }

    fun hasCoords(ctx: Context, place: String) = get(ctx, place) != null

    fun all(ctx: Context): Map<String, Pair<Double, Double>> {
        val obj = load(ctx)
        val result = mutableMapOf<String, Pair<Double, Double>>()
        obj.keys().forEach { key ->
            val arr = obj.getJSONArray(key)
            result[key] = Pair(arr.getDouble(0), arr.getDouble(1))
        }
        return result
    }

    private fun load(ctx: Context): JSONObject {
        val raw = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "{}") ?: "{}"
        return try { JSONObject(raw) } catch (_: Exception) { JSONObject() }
    }

    private fun save(ctx: Context, obj: JSONObject) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, obj.toString()).apply()
    }
}
