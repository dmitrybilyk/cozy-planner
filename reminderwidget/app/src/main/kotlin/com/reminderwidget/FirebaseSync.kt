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

    fun pushTodo(ctx: Context, title: String, items: List<TodoStore.Item>) {
        val uid = userId ?: return
        val itemsList = items.map { mapOf("id" to it.id, "text" to it.text) }
        db.collection("users").document(uid).collection("data").document("todo")
            .set(mapOf("title" to title, "items" to itemsList, "ts" to System.currentTimeMillis()))
            .addOnFailureListener { Log.w(TAG, "pushTodo failed: $it") }
    }

    fun listenTodo(ctx: Context, onChange: (String, List<TodoStore.Item>) -> Unit): ListenerRegistration? {
        val uid = userId ?: return null
        return db.collection("users").document(uid).collection("data").document("todo")
            .addSnapshotListener { snap, err ->
                if (err != null) { Log.w(TAG, "listenTodo: $err"); return@addSnapshotListener }
                if (snap == null || !snap.exists()) return@addSnapshotListener
                val title = snap.getString("title") ?: TodoStore.todayLabel()
                @Suppress("UNCHECKED_CAST")
                val rawItems = snap.get("items") as? List<Map<String, Any>> ?: emptyList()
                val items = rawItems.mapNotNull { m ->
                    val id   = (m["id"] as? Number)?.toLong() ?: return@mapNotNull null
                    val text = m["text"] as? String ?: return@mapNotNull null
                    TodoStore.Item(id, text)
                }
                onChange(title, items)
            }
    }

    // ── Events ────────────────────────────────────────────────────────────────

    fun pushEvent(ctx: Context, event: EventStore.AppEvent) {
        val uid = userId ?: return
        db.collection("users").document(uid).collection("events")
            .document(event.id.toString())
            .set(mapOf(
                "id"         to event.id,
                "title"      to event.title,
                "startMs"    to event.startMs,
                "durationMs" to event.durationMs,
                "rrule"      to (event.rrule ?: ""),
                "completed"  to event.completed,
                "favorite"   to event.favorite,
                "ts"         to System.currentTimeMillis(),
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
                                    id         = doc.getLong("id") ?: continue,
                                    title      = doc.getString("title") ?: continue,
                                    startMs    = doc.getLong("startMs") ?: continue,
                                    durationMs = doc.getLong("durationMs") ?: 0,
                                    rrule      = doc.getString("rrule")?.takeIf { it.isNotBlank() },
                                    completed  = doc.getBoolean("completed") ?: false,
                                    favorite   = doc.getBoolean("favorite") ?: false,
                                ))
                            } catch (_: Exception) {}
                        }
                        DocumentChange.Type.REMOVED -> {
                            doc.getLong("id")?.let { removed.add(it) }
                        }
                    }
                }
                if (added.isNotEmpty() || removed.isNotEmpty()) onChange(added, removed)
            }
    }
}
