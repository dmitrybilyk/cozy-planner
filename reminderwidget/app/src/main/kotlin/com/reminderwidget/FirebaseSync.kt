package com.reminderwidget

import android.accounts.AccountManager
import android.content.Context
import android.util.Log
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

object FirebaseSync {
    private const val TAG = "FirebaseSync"
    private val db get() = FirebaseFirestore.getInstance()

    private var userId: String? = null

    fun init(ctx: Context) {
        if (userId != null) return
        try {
            val email = AccountManager.get(ctx).getAccountsByType("com.google")
                .firstOrNull()?.name ?: return
            userId = email.replace(Regex("[^a-zA-Z0-9]"), "_")
            Log.d(TAG, "Sync user: $userId")
        } catch (e: Exception) {
            Log.w(TAG, "Could not get account: $e")
        }
    }

    fun isReady() = userId != null

    // ── Todo ─────────────────────────────────────────────────────────────────

    fun pushTodo(ctx: Context, title: String, items: List<TodoStore.Item>, places: List<String> = emptyList()) {
        val uid = userId ?: return
        val itemsList = items.map { item ->
            val m = mutableMapOf<String, Any>("id" to item.id, "text" to item.text)
            item.reminderMinutes?.let { m["rm"] = it }
            item.placeName?.let { m["place"] = it }
            if (item.isGroup) m["isGroup"] = true
            if (item.done)    m["done"]    = true
            m
        }
        db.collection("users").document(uid).collection("data").document("todo")
            .set(mapOf("title" to title, "items" to itemsList, "places" to places, "ts" to System.currentTimeMillis()))
            .addOnFailureListener { Log.w(TAG, "pushTodo failed: $it") }
    }

    fun listenTodo(ctx: Context, onChange: (String, List<TodoStore.Item>, List<String>) -> Unit): ListenerRegistration? {
        val uid = userId ?: return null
        return db.collection("users").document(uid).collection("data").document("todo")
            .addSnapshotListener { snap, err ->
                if (err != null) { Log.w(TAG, "listenTodo: $err"); return@addSnapshotListener }
                if (snap == null || !snap.exists()) return@addSnapshotListener
                val title = snap.getString("title") ?: TodoStore.todayLabel()
                @Suppress("UNCHECKED_CAST")
                val rawItems = snap.get("items") as? List<Map<String, Any>> ?: emptyList()
                val items = rawItems.mapNotNull { m ->
                    val id      = (m["id"] as? Number)?.toLong() ?: return@mapNotNull null
                    val text    = m["text"] as? String ?: return@mapNotNull null
                    val rm      = (m["rm"] as? Number)?.toInt()?.takeIf { it >= 0 }
                    val place   = m["place"] as? String
                    val isGroup = m["isGroup"] as? Boolean ?: false
                    val done    = m["done"]    as? Boolean ?: false
                    TodoStore.Item(id, text, rm, place, isGroup, done)
                }
                @Suppress("UNCHECKED_CAST")
                val places = snap.get("places") as? List<String> ?: emptyList()
                onChange(title, items, places)
            }
    }

    // ── Events ────────────────────────────────────────────────────────────────

    fun pushEvent(ctx: Context, event: EventStore.AppEvent) {
        val uid = userId ?: return
        db.collection("users").document(uid).collection("events")
            .document(event.id.toString())
            .set(mapOf(
                "id"           to event.id,
                "title"        to event.title,
                "startMs"      to event.startMs,
                "durationMs"   to event.durationMs,
                "rrule"        to (event.rrule ?: ""),
                "completed"    to event.completed,
                "favorite"     to event.favorite,
                "isGroup"      to event.isGroup,
                "locationName" to (event.locationName ?: ""),
                "hasTime"      to event.hasTime,
                "ts"           to System.currentTimeMillis(),
            ))
            .addOnFailureListener { Log.w(TAG, "pushEvent failed: $it") }
    }

    fun deleteEvent(ctx: Context, eventId: Long) {
        val uid = userId ?: return
        db.collection("users").document(uid).collection("events")
            .document(eventId.toString()).delete()
            .addOnFailureListener { Log.w(TAG, "deleteEvent failed: $it") }
    }

    fun listenEvents(
        ctx: Context,
        onChange: (added: List<EventStore.AppEvent>, removed: List<Long>) -> Unit
    ): ListenerRegistration? {
        val uid = userId ?: return null
        Log.d(TAG, "listenEvents: attaching for uid=$uid")
        return db.collection("users").document(uid).collection("events")
            .addSnapshotListener { snap, err ->
                if (err != null) { Log.w(TAG, "listenEvents: $err"); return@addSnapshotListener }
                if (snap == null) return@addSnapshotListener
                val added   = mutableListOf<EventStore.AppEvent>()
                val removed = mutableListOf<Long>()
                for (change in snap.documentChanges) {
                    val doc = change.document
                    when (change.type) {
                        DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                            try {
                                added.add(EventStore.AppEvent(
                                    id           = doc.getLong("id") ?: continue,
                                    title        = doc.getString("title") ?: continue,
                                    startMs      = doc.getLong("startMs") ?: continue,
                                    durationMs   = doc.getLong("durationMs") ?: 0,
                                    rrule        = doc.getString("rrule")?.takeIf { it.isNotBlank() },
                                    completed    = doc.getBoolean("completed") ?: false,
                                    favorite     = doc.getBoolean("favorite") ?: false,
                                    isGroup      = doc.getBoolean("isGroup") ?: false,
                                    locationName = doc.getString("locationName")?.takeIf { it.isNotBlank() },
                                    hasTime      = doc.getBoolean("hasTime") ?: true,
                                ))
                            } catch (_: Exception) {}
                        }
                        DocumentChange.Type.REMOVED -> {
                            doc.getLong("id")?.let { removed.add(it) }
                        }
                    }
                }
                if (added.isNotEmpty() || removed.isNotEmpty()) {
                    onChange(added, removed)
                }
            }
    }

    // ── Group ─────────────────────────────────────────────────────────────────

    fun joinGroup(ctx: Context, groupId: String, memberName: String) {
        val uid = userId ?: return
        db.collection("groups").document(groupId).collection("members").document(uid)
            .set(mapOf("name" to memberName, "joinedAt" to System.currentTimeMillis()))
            .addOnFailureListener { Log.w(TAG, "joinGroup failed: $it") }
    }

    fun leaveGroup(ctx: Context, groupId: String) {
        val uid = userId ?: return
        db.collection("groups").document(groupId).collection("members").document(uid)
            .delete()
            .addOnFailureListener { Log.w(TAG, "leaveGroup failed: $it") }
    }

    fun listenGroupMembers(
        groupId: String,
        onChange: (List<Pair<String, String>>) -> Unit
    ): ListenerRegistration? =
        db.collection("groups").document(groupId).collection("members")
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null) return@addSnapshotListener
                onChange(snap.documents.mapNotNull { doc ->
                    val name = doc.getString("name") ?: return@mapNotNull null
                    Pair(doc.id, name)
                })
            }

    fun pushGroupEvent(groupId: String, event: EventStore.AppEvent) {
        db.collection("groups").document(groupId).collection("events")
            .document(event.id.toString())
            .set(mapOf(
                "id"           to event.id,
                "title"        to event.title,
                "startMs"      to event.startMs,
                "durationMs"   to event.durationMs,
                "rrule"        to (event.rrule ?: ""),
                "completed"    to event.completed,
                "favorite"     to event.favorite,
                "locationName" to (event.locationName ?: ""),
                "hasTime"      to event.hasTime,
                "ts"           to System.currentTimeMillis(),
            ))
            .addOnFailureListener { Log.w(TAG, "pushGroupEvent failed: $it") }
    }

    fun deleteGroupEvent(groupId: String, eventId: Long) {
        db.collection("groups").document(groupId).collection("events")
            .document(eventId.toString()).delete()
            .addOnFailureListener { Log.w(TAG, "deleteGroupEvent failed: $it") }
    }

    fun listenGroupEvents(
        groupId: String,
        onChange: (added: List<EventStore.AppEvent>, removed: List<Long>) -> Unit
    ): ListenerRegistration? {
        Log.d(TAG, "listenGroupEvents: attaching for group=$groupId")
        return db.collection("groups").document(groupId).collection("events")
            .addSnapshotListener { snap, err ->
                if (err != null) { Log.w(TAG, "listenGroupEvents: $err"); return@addSnapshotListener }
                if (snap == null) return@addSnapshotListener
                val added   = mutableListOf<EventStore.AppEvent>()
                val removed = mutableListOf<Long>()
                for (change in snap.documentChanges) {
                    val doc = change.document
                    when (change.type) {
                        DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                            try {
                                added.add(EventStore.AppEvent(
                                    id           = doc.getLong("id") ?: continue,
                                    title        = doc.getString("title") ?: continue,
                                    startMs      = doc.getLong("startMs") ?: continue,
                                    durationMs   = doc.getLong("durationMs") ?: 0,
                                    rrule        = doc.getString("rrule")?.takeIf { it.isNotBlank() },
                                    completed    = doc.getBoolean("completed") ?: false,
                                    favorite     = doc.getBoolean("favorite") ?: false,
                                    isGroup      = true,
                                    locationName = doc.getString("locationName")?.takeIf { it.isNotBlank() },
                                    hasTime      = doc.getBoolean("hasTime") ?: true,
                                ))
                            } catch (_: Exception) {}
                        }
                        DocumentChange.Type.REMOVED -> doc.getLong("id")?.let { removed.add(it) }
                    }
                }
                if (added.isNotEmpty() || removed.isNotEmpty()) onChange(added, removed)
            }
    }

    // ── Group todo items (per-document, like group events) ────────────────────

    fun pushGroupItem(groupId: String, item: TodoStore.Item) {
        val m = mutableMapOf<String, Any>(
            "id"        to item.id,
            "text"      to item.text,
            "sortOrder" to item.sortOrder,
        )
        item.reminderMinutes?.let { m["rm"] = it }
        item.placeName?.let { m["place"] = it }
        if (item.done) m["done"] = true
        db.collection("groups").document(groupId).collection("todoItems")
            .document(item.id.toString()).set(m)
            .addOnFailureListener { Log.w(TAG, "pushGroupItem failed: $it") }
    }

    fun deleteGroupItem(groupId: String, itemId: Long) {
        db.collection("groups").document(groupId).collection("todoItems")
            .document(itemId.toString()).delete()
            .addOnFailureListener { Log.w(TAG, "deleteGroupItem failed: $it") }
    }

    fun listenGroupItems(
        groupId: String,
        onChange: (added: List<TodoStore.Item>, removed: List<Long>) -> Unit
    ): ListenerRegistration? =
        db.collection("groups").document(groupId).collection("todoItems")
            .addSnapshotListener { snap, err ->
                if (err != null) { Log.w(TAG, "listenGroupItems: $err"); return@addSnapshotListener }
                if (snap == null) return@addSnapshotListener
                val added   = mutableListOf<TodoStore.Item>()
                val removed = mutableListOf<Long>()
                for (change in snap.documentChanges) {
                    val doc = change.document
                    when (change.type) {
                        DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                            val id        = doc.getLong("id") ?: continue
                            val text      = doc.getString("text") ?: continue
                            val rm        = doc.getLong("rm")?.toInt()
                            val place     = doc.getString("place")
                            val done      = doc.getBoolean("done") ?: false
                            val sortOrder = doc.getLong("sortOrder") ?: id
                            added.add(TodoStore.Item(id, text, rm, place, isGroup = true, done = done, sortOrder = sortOrder))
                        }
                        DocumentChange.Type.REMOVED -> doc.getLong("id")?.let { removed.add(it) }
                    }
                }
                if (added.isNotEmpty() || removed.isNotEmpty()) onChange(added, removed)
            }

    // ── Group places (single ordered list document) ───────────────────────────

    fun pushGroupPlaces(groupId: String, places: List<String>) {
        db.collection("groups").document(groupId).collection("data").document("places")
            .set(mapOf("places" to places, "ts" to System.currentTimeMillis()))
            .addOnFailureListener { Log.w(TAG, "pushGroupPlaces failed: $it") }
    }

    fun listenGroupPlaces(
        groupId: String,
        onChange: (List<String>) -> Unit
    ): ListenerRegistration? =
        db.collection("groups").document(groupId).collection("data").document("places")
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null || !snap.exists()) return@addSnapshotListener
                @Suppress("UNCHECKED_CAST")
                val places = snap.get("places") as? List<String> ?: emptyList()
                onChange(places)
            }

    // ── Group todo (legacy single-doc — kept for backward compat read) ────────

    fun pushGroupTodo(groupId: String, items: List<TodoStore.Item>, places: List<String> = emptyList()) {
        val itemsList = items.map { item ->
            val m = mutableMapOf<String, Any>("id" to item.id, "text" to item.text)
            item.reminderMinutes?.let { m["rm"] = it }
            item.placeName?.let { m["place"] = it }
            if (item.done) m["done"] = true
            m
        }
        db.collection("groups").document(groupId).collection("data").document("todo")
            .set(mapOf("items" to itemsList, "places" to places, "ts" to System.currentTimeMillis()))
            .addOnFailureListener { Log.w(TAG, "pushGroupTodo failed: $it") }
    }

    fun listenGroupTodo(
        groupId: String,
        onChange: (items: List<TodoStore.Item>, places: List<String>) -> Unit
    ): ListenerRegistration? =
        db.collection("groups").document(groupId).collection("data").document("todo")
            .addSnapshotListener { snap, err ->
                if (err != null || snap == null || !snap.exists()) return@addSnapshotListener
                @Suppress("UNCHECKED_CAST")
                val rawItems = snap.get("items") as? List<Map<String, Any>> ?: emptyList()
                val items = rawItems.mapNotNull { m ->
                    val id    = (m["id"] as? Number)?.toLong() ?: return@mapNotNull null
                    val text  = m["text"] as? String ?: return@mapNotNull null
                    val rm    = (m["rm"] as? Number)?.toInt()?.takeIf { it >= 0 }
                    val place = m["place"] as? String
                    val done  = m["done"]  as? Boolean ?: false
                    TodoStore.Item(id, text, rm, place, isGroup = true, done = done)
                }
                @Suppress("UNCHECKED_CAST")
                val places = snap.get("places") as? List<String> ?: emptyList()
                onChange(items, places)
            }
}
