package com.linkease

import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

object FirebaseHelper {

    fun getCurrentUserId(): String? = Firebase.auth.currentUser?.uid

    fun signInAnonymously(onComplete: (uid: String?) -> Unit) {
        if (Firebase.auth.currentUser != null) {
            onComplete(Firebase.auth.currentUser!!.uid)
            return
        }
        Firebase.auth.signInAnonymously()
            .addOnSuccessListener { result -> onComplete(result.user?.uid) }
            .addOnFailureListener { onComplete(null) }
    }

    fun publishTrainerData(
        uid: String,
        availability: List<AvailabilitySlot>,
        sessions: List<Session>,
        locations: List<Location>,
        clients: List<Client> = emptyList(),
        workHoursStart: Int,
        workHoursEnd: Int,
        onComplete: (Boolean) -> Unit,
    ) {
        val data = hashMapOf(
            "workHoursStart" to workHoursStart,
            "workHoursEnd" to workHoursEnd,
            "updatedAt" to com.google.firebase.Timestamp.now(),
            "availability" to availability.map { a ->
                hashMapOf(
                    "date" to a.date.toString(),
                    "startTime" to a.startTime.toStorageString(),
                    "endTime" to a.endTime.toStorageString(),
                    "locationId" to (a.locationId ?: 0L),
                )
            },
            "sessionSlots" to sessions.map { s ->
                hashMapOf(
                    "date" to s.date.toString(),
                    "startTime" to s.startTime.toStorageString(),
                    "endTime" to s.endTime.toStorageString(),
                )
            },
            "locations" to locations.map { l ->
                hashMapOf("id" to l.id, "name" to l.name, "colorHex" to l.colorHex)
            },
        )

        Firebase.firestore.collection("trainers").document(uid)
            .set(data)
            .addOnSuccessListener {
                val linkedClients = clients.filter { it.firebaseClientId != null }
                if (linkedClients.isEmpty()) { onComplete(true); return@addOnSuccessListener }
                var remaining = linkedClients.size
                fun done() { if (--remaining == 0) onComplete(true) }
                linkedClients.forEach { linkedClient ->
                    val firebaseId = linkedClient.firebaseClientId!!
                    val clientSessions = sessions.filter { linkedClient.id in it.clientIds }
                    val ref = Firebase.firestore.collection("clientSessions").document(firebaseId)
                    // Read existing confirmed statuses before overwriting
                    ref.get().addOnSuccessListener { existing ->
                        @Suppress("UNCHECKED_CAST")
                        val confirmedIds = (existing.get("sessions") as? List<Map<String, Any>>)
                            ?.filter { (it["clientConfirmed"] as? Boolean) == true }
                            ?.mapNotNull { it["id"] as? String }
                            ?.toSet() ?: emptySet()
                        val sessionsData = clientSessions.map { s ->
                            hashMapOf(
                                "id" to s.id.toString(),
                                "date" to s.date.toString(),
                                "startTime" to s.startTime.toStorageString(),
                                "endTime" to s.endTime.toStorageString(),
                                "notes" to (s.notes ?: ""),
                                "clientConfirmed" to (s.id.toString() in confirmedIds),
                            )
                        }
                        ref.set(hashMapOf(
                            "trainerId" to uid,
                            "sessions" to sessionsData,
                            "updatedAt" to com.google.firebase.Timestamp.now()
                        )).addOnSuccessListener { done() }.addOnFailureListener { done() }
                    }.addOnFailureListener {
                        // fallback: write without preserving confirmed
                        val sessionsData = clientSessions.map { s ->
                            hashMapOf("id" to s.id.toString(), "date" to s.date.toString(),
                                "startTime" to s.startTime.toStorageString(), "endTime" to s.endTime.toStorageString(),
                                "notes" to (s.notes ?: ""), "clientConfirmed" to false)
                        }
                        ref.set(hashMapOf("trainerId" to uid, "sessions" to sessionsData,
                            "updatedAt" to com.google.firebase.Timestamp.now()))
                            .addOnSuccessListener { done() }.addOnFailureListener { done() }
                    }
                }
            }
            .addOnFailureListener { onComplete(false) }
    }

    fun listenClientSessions(clientFirebaseId: String, onResult: (List<ClientSession>) -> Unit): ListenerRegistration {
        return Firebase.firestore.collection("clientSessions").document(clientFirebaseId)
            .addSnapshotListener { doc, _ ->
                if (doc == null || !doc.exists()) { onResult(emptyList()); return@addSnapshotListener }
                try {
                    @Suppress("UNCHECKED_CAST")
                    val trainerId = doc.getString("trainerId") ?: ""
                    val sessions = (doc.get("sessions") as? List<Map<String, Any>> ?: emptyList()).mapNotNull { s ->
                        try {
                            ClientSession(
                                id = s["id"] as? String ?: return@mapNotNull null,
                                trainerId = trainerId,
                                date = LocalDate.parse(s["date"] as String),
                                startTime = parseStorageTime(s["startTime"] as String),
                                endTime = parseStorageTime(s["endTime"] as String),
                                notes = (s["notes"] as? String)?.ifBlank { null },
                                clientConfirmed = s["clientConfirmed"] as? Boolean ?: false,
                            )
                        } catch (_: Exception) { null }
                    }
                    onResult(sessions)
                } catch (_: Exception) { onResult(emptyList()) }
            }
    }

    fun confirmClientSession(clientFirebaseId: String, sessionId: String, onComplete: (Boolean) -> Unit) {
        val ref = Firebase.firestore.collection("clientSessions").document(clientFirebaseId)
        ref.get().addOnSuccessListener { doc ->
            @Suppress("UNCHECKED_CAST")
            val sessions = (doc.get("sessions") as? List<Map<String, Any>> ?: emptyList()).map { s ->
                if ((s["id"] as? String) == sessionId) {
                    hashMapOf("id" to s["id"], "date" to s["date"], "startTime" to s["startTime"],
                        "endTime" to s["endTime"], "notes" to s["notes"], "clientConfirmed" to true)
                } else s
            }
            ref.update("sessions", sessions)
                .addOnSuccessListener { onComplete(true) }
                .addOnFailureListener { onComplete(false) }
        }.addOnFailureListener { onComplete(false) }
    }

    fun rejectClientSession(clientFirebaseId: String, sessionId: String, onComplete: (Boolean) -> Unit) {
        val ref = Firebase.firestore.collection("clientSessions").document(clientFirebaseId)
        ref.get().addOnSuccessListener { doc ->
            @Suppress("UNCHECKED_CAST")
            val sessions = (doc.get("sessions") as? List<Map<String, Any>> ?: emptyList())
                .filter { (it["id"] as? String) != sessionId }
            ref.update("sessions", sessions)
                .addOnSuccessListener { onComplete(true) }
                .addOnFailureListener { onComplete(false) }
        }.addOnFailureListener { onComplete(false) }
    }

    fun submitBookingRequest(
        trainerId: String, clientFirebaseId: String,
        date: LocalDate, startTime: LocalTime, endTime: LocalTime,
        clientNote: String?, onComplete: (Boolean) -> Unit,
    ) {
        val data = hashMapOf(
            "trainerId" to trainerId,
            "clientFirebaseId" to clientFirebaseId,
            "date" to date.toString(),
            "startTime" to startTime.toStorageString(),
            "endTime" to endTime.toStorageString(),
            "clientNote" to (clientNote ?: ""),
            "status" to "pending",
            "createdAt" to com.google.firebase.Timestamp.now(),
        )
        Firebase.firestore.collection("bookingRequests").add(data)
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    fun listenBookingRequests(trainerId: String, onResult: (List<BookingRequest>) -> Unit): ListenerRegistration {
        // Single whereEqualTo only — compound queries need a composite index that doesn't auto-create.
        // Filter status in-memory.
        return Firebase.firestore.collection("bookingRequests")
            .whereEqualTo("trainerId", trainerId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { onResult(emptyList()); return@addSnapshotListener }
                val requests = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        val status = doc.getString("status") ?: "pending"
                        if (status != "pending") return@mapNotNull null
                        BookingRequest(
                            id = doc.id,
                            trainerId = doc.getString("trainerId") ?: return@mapNotNull null,
                            clientFirebaseId = doc.getString("clientFirebaseId") ?: return@mapNotNull null,
                            date = LocalDate.parse(doc.getString("date") ?: return@mapNotNull null),
                            startTime = parseStorageTime(doc.getString("startTime") ?: return@mapNotNull null),
                            endTime = parseStorageTime(doc.getString("endTime") ?: return@mapNotNull null),
                            clientNote = doc.getString("clientNote")?.ifBlank { null },
                            status = status,
                        )
                    } catch (_: Exception) { null }
                } ?: emptyList()
                onResult(requests)
            }
    }

    fun listenClientBookingRequests(clientFirebaseId: String, trainerId: String, onResult: (List<BookingRequest>) -> Unit): ListenerRegistration {
        // Single whereEqualTo — filter trainerId in-memory to avoid needing composite index.
        return Firebase.firestore.collection("bookingRequests")
            .whereEqualTo("clientFirebaseId", clientFirebaseId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { onResult(emptyList()); return@addSnapshotListener }
                val requests = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        if (doc.getString("trainerId") != trainerId) return@mapNotNull null
                        BookingRequest(
                            id = doc.id,
                            trainerId = doc.getString("trainerId") ?: return@mapNotNull null,
                            clientFirebaseId = doc.getString("clientFirebaseId") ?: return@mapNotNull null,
                            date = LocalDate.parse(doc.getString("date") ?: return@mapNotNull null),
                            startTime = parseStorageTime(doc.getString("startTime") ?: return@mapNotNull null),
                            endTime = parseStorageTime(doc.getString("endTime") ?: return@mapNotNull null),
                            clientNote = doc.getString("clientNote")?.ifBlank { null },
                            status = doc.getString("status") ?: "pending",
                        )
                    } catch (_: Exception) { null }
                } ?: emptyList()
                onResult(requests)
            }
    }

    fun updateBookingRequestStatus(requestId: String, status: String, onComplete: (Boolean) -> Unit) {
        Firebase.firestore.collection("bookingRequests").document(requestId)
            .update("status", status)
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    fun saveClientAvailability(
        clientFirebaseId: String,
        trainerId: String,
        slots: List<ClientAvailabilitySlot>,
        onComplete: (Boolean) -> Unit,
    ) {
        val slotsData = slots.map { s ->
            hashMapOf("id" to s.id, "date" to s.date.toString(),
                "startTime" to s.startTime.toStorageString(), "endTime" to s.endTime.toStorageString())
        }
        Firebase.firestore.collection("clientAvailability").document(clientFirebaseId)
            .set(hashMapOf("trainerId" to trainerId, "clientFirebaseId" to clientFirebaseId,
                "slots" to slotsData, "updatedAt" to com.google.firebase.Timestamp.now()))
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { onComplete(false) }
    }

    fun listenClientAvailabilityForTrainer(trainerId: String, onResult: (List<ClientAvailabilitySlot>) -> Unit): ListenerRegistration {
        return Firebase.firestore.collection("clientAvailability")
            .whereEqualTo("trainerId", trainerId)
            .addSnapshotListener { snapshot, _ ->
                val all = snapshot?.documents?.flatMap { doc ->
                    val cid = doc.getString("clientFirebaseId") ?: return@flatMap emptyList()
                    @Suppress("UNCHECKED_CAST")
                    (doc.get("slots") as? List<Map<String, Any>> ?: emptyList()).mapNotNull { s ->
                        try {
                            ClientAvailabilitySlot(
                                id = s["id"] as? String ?: return@mapNotNull null,
                                clientFirebaseId = cid,
                                date = LocalDate.parse(s["date"] as String),
                                startTime = parseStorageTime(s["startTime"] as String),
                                endTime = parseStorageTime(s["endTime"] as String),
                            )
                        } catch (_: Exception) { null }
                    }
                } ?: emptyList()
                onResult(all)
            }
    }

    fun listenClientAvailability(clientFirebaseId: String, onResult: (List<ClientAvailabilitySlot>) -> Unit): ListenerRegistration {
        return Firebase.firestore.collection("clientAvailability").document(clientFirebaseId)
            .addSnapshotListener { doc, _ ->
                if (doc == null || !doc.exists()) { onResult(emptyList()); return@addSnapshotListener }
                @Suppress("UNCHECKED_CAST")
                val slots = (doc.get("slots") as? List<Map<String, Any>> ?: emptyList()).mapNotNull { s ->
                    try {
                        ClientAvailabilitySlot(
                            id = s["id"] as? String ?: return@mapNotNull null,
                            clientFirebaseId = clientFirebaseId,
                            date = LocalDate.parse(s["date"] as String),
                            startTime = parseStorageTime(s["startTime"] as String),
                            endTime = parseStorageTime(s["endTime"] as String),
                        )
                    } catch (_: Exception) { null }
                }
                onResult(slots)
            }
    }

    fun listenChat(trainerId: String, clientFirebaseId: String, onResult: (List<ChatMessage>) -> Unit): ListenerRegistration {
        val chatId = "${trainerId}_${clientFirebaseId}"
        // No orderBy — avoids needing a composite index. Sort in-memory by timestamp.
        return Firebase.firestore.collection("chats").document(chatId).collection("messages")
            .addSnapshotListener { snapshot, error ->
                if (error != null) { onResult(emptyList()); return@addSnapshotListener }
                val messages = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        ChatMessage(
                            id = doc.id,
                            senderId = doc.getString("senderId") ?: return@mapNotNull null,
                            text = doc.getString("text") ?: return@mapNotNull null,
                            timestamp = doc.getLong("timestamp") ?: 0L,
                        )
                    } catch (_: Exception) { null }
                }?.sortedBy { it.timestamp } ?: emptyList()
                onResult(messages)
            }
    }

    fun registerClientWithTrainer(
        trainerId: String,
        clientUID: String,
        deviceModel: String,
        clientEmail: String? = null,
        onComplete: (Boolean) -> Unit,
    ) {
        val data = hashMapOf<String, Any>(
            "uid" to clientUID,
            "deviceModel" to deviceModel,
            "connectedAt" to com.google.firebase.Timestamp.now(),
        )
        if (!clientEmail.isNullOrBlank()) data["email"] = clientEmail
        Firebase.firestore.collection("trainerConnections")
            .document(trainerId)
            .collection("clients")
            .document(clientUID)
            .set(data)
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { e ->
                android.util.Log.e("Firebase", "registerClientWithTrainer failed: ${e.message}")
                onComplete(false)
            }
    }

    fun listenTrainerConnections(
        trainerId: String,
        onResult: (List<Triple<String, String, String?>>) -> Unit,
    ): ListenerRegistration {
        return Firebase.firestore.collection("trainerConnections")
            .document(trainerId)
            .collection("clients")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.e("Firebase", "listenTrainerConnections failed: ${error.message}")
                    onResult(emptyList()); return@addSnapshotListener
                }
                val clients = snapshot?.documents?.mapNotNull { doc ->
                    val uid = doc.getString("uid") ?: return@mapNotNull null
                    val model = doc.getString("deviceModel") ?: ""
                    val email = doc.getString("email")
                    Triple(uid, model, email)
                } ?: emptyList()
                onResult(clients)
            }
    }

    fun sendChatMessage(
        trainerId: String, clientFirebaseId: String,
        senderId: String, text: String, onComplete: (Boolean, String?) -> Unit,
    ) {
        val chatId = "${trainerId}_${clientFirebaseId}"
        val data = hashMapOf(
            "senderId" to senderId,
            "text" to text,
            "timestamp" to System.currentTimeMillis(),
        )
        Firebase.firestore.collection("chats").document(chatId).collection("messages").add(data)
            .addOnSuccessListener { onComplete(true, null) }
            .addOnFailureListener { e ->
                android.util.Log.e("Firebase", "sendChatMessage failed: ${e.message}")
                onComplete(false, e.message)
            }
    }

    fun clearTrainerData(uid: String, email: String?, onComplete: (Boolean) -> Unit) {
        var remaining = if (email.isNullOrBlank()) 1 else 2
        var anyFailed = false
        fun done(ok: Boolean) { if (!ok) anyFailed = true; if (--remaining == 0) onComplete(!anyFailed) }
        Firebase.firestore.collection("trainers").document(uid)
            .delete()
            .addOnSuccessListener { done(true) }
            .addOnFailureListener { done(false) }
        if (!email.isNullOrBlank()) {
            Firebase.firestore.collection("trainerEmails").document(email.trim().lowercase())
                .delete()
                .addOnSuccessListener { done(true) }
                .addOnFailureListener { done(false) }
        }
    }

    fun publishTrainerEmail(uid: String, email: String, onComplete: (Boolean) -> Unit) {
        val normalizedEmail = email.trim().lowercase()
        Firebase.firestore.collection("trainerEmails").document(normalizedEmail)
            .set(hashMapOf("uid" to uid, "email" to email, "updatedAt" to com.google.firebase.Timestamp.now()))
            .addOnSuccessListener { onComplete(true) }
            .addOnFailureListener { e ->
                android.util.Log.e("Firebase", "publishTrainerEmail failed: ${e.message}")
                onComplete(false)
            }
    }

    fun lookupTrainerByEmail(email: String, onResult: (String?) -> Unit) {
        val normalizedEmail = email.trim().lowercase()
        Firebase.firestore.collection("trainerEmails").document(normalizedEmail)
            .get()
            .addOnSuccessListener { doc ->
                onResult(if (doc.exists()) doc.getString("uid") else null)
            }
            .addOnFailureListener { e ->
                android.util.Log.e("Firebase", "lookupTrainerByEmail failed: ${e.message}")
                onResult(null)
            }
    }

    fun listenTrainerData(trainerId: String, onResult: (TrainerData?) -> Unit): ListenerRegistration {
        return Firebase.firestore.collection("trainers").document(trainerId)
            .addSnapshotListener { doc, _ ->
                if (doc == null || !doc.exists()) { onResult(null); return@addSnapshotListener }
                try {
                    val workStart = (doc.getLong("workHoursStart") ?: 7L).toInt()
                    val workEnd   = (doc.getLong("workHoursEnd")   ?: 22L).toInt()

                    @Suppress("UNCHECKED_CAST")
                    val avail = (doc.get("availability") as? List<Map<String, Any>> ?: emptyList()).map { a ->
                        AvailabilitySlot(
                            id = 0L,
                            date = LocalDate.parse(a["date"] as String),
                            startTime = parseStorageTime(a["startTime"] as String),
                            endTime   = parseStorageTime(a["endTime"]   as String),
                            locationId = (a["locationId"] as? Long)?.takeIf { it > 0L },
                        )
                    }

                    @Suppress("UNCHECKED_CAST")
                    val slots = (doc.get("sessionSlots") as? List<Map<String, Any>> ?: emptyList()).map { s ->
                        BookedSlot(
                            date      = LocalDate.parse(s["date"] as String),
                            startTime = parseStorageTime(s["startTime"] as String),
                            endTime   = parseStorageTime(s["endTime"]   as String),
                        )
                    }

                    @Suppress("UNCHECKED_CAST")
                    val locs = (doc.get("locations") as? List<Map<String, Any>> ?: emptyList()).map { l ->
                        Location(
                            id       = (l["id"] as? Long) ?: 0L,
                            name     = l["name"] as? String ?: "",
                            colorHex = l["colorHex"] as? String ?: "#4CAF50",
                        )
                    }

                    onResult(TrainerData(trainerId, workStart, workEnd, avail, slots, locs))
                } catch (_: Exception) { onResult(null) }
            }
    }
}
