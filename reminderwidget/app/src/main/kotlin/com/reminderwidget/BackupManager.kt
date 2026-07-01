package com.reminderwidget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object BackupManager {

    fun exportJson(context: Context): String {
        val root = JSONObject()
        root.put("version", 1)
        root.put("exportedAt", System.currentTimeMillis())

        root.put("events",
            JSONArray(context.getSharedPreferences("event_store", Context.MODE_PRIVATE)
                .getString("events", "[]")))

        val todo = context.getSharedPreferences("todo_store", Context.MODE_PRIVATE)
        root.put("todoTitle",  todo.getString("title", ""))
        root.put("todoItems",  JSONArray(todo.getString("items", "[]")))
        root.put("todoPlaces", todo.getString("places", ""))

        val group = context.getSharedPreferences("group_todo_store", Context.MODE_PRIVATE)
        root.put("groupItems",  JSONArray(group.getString("items", "[]")))
        root.put("groupPlaces", group.getString("places", ""))

        root.put("locations",
            JSONArray(context.getSharedPreferences("locations_store", Context.MODE_PRIVATE)
                .getString("locations", "[]")))

        return root.toString(2)
    }

    fun importJson(context: Context, json: String): ImportResult {
        val root = JSONObject(json)

        var events = 0
        var todos  = 0

        if (root.has("events")) {
            val arr = root.getJSONArray("events")
            context.getSharedPreferences("event_store", Context.MODE_PRIVATE)
                .edit().putString("events", arr.toString()).apply()
            events = arr.length()
        }

        val todoEdit = context.getSharedPreferences("todo_store", Context.MODE_PRIVATE).edit()
        if (root.has("todoTitle"))  todoEdit.putString("title",  root.getString("todoTitle"))
        if (root.has("todoItems")) {
            val arr = root.getJSONArray("todoItems")
            todoEdit.putString("items", arr.toString())
            todos = arr.length()
        }
        if (root.has("todoPlaces")) todoEdit.putString("places", root.getString("todoPlaces"))
        todoEdit.apply()

        val groupEdit = context.getSharedPreferences("group_todo_store", Context.MODE_PRIVATE).edit()
        if (root.has("groupItems"))  groupEdit.putString("items",  root.getJSONArray("groupItems").toString())
        if (root.has("groupPlaces")) groupEdit.putString("places", root.getString("groupPlaces"))
        groupEdit.apply()

        if (root.has("locations")) {
            context.getSharedPreferences("locations_store", Context.MODE_PRIVATE)
                .edit().putString("locations", root.getJSONArray("locations").toString()).apply()
        }

        return ImportResult(events, todos)
    }

    data class ImportResult(val events: Int, val todos: Int)
}
