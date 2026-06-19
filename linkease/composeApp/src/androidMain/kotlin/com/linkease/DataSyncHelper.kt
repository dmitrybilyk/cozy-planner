package com.linkease

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.core.content.FileProvider
import com.linkease.db.LinkDatabaseHelper
import kotlinx.datetime.LocalDate
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object DataSyncHelper {

    private const val BACKUP_FILENAME = "linkease_backup.json"

    fun getBackupFile(context: Context): File =
        File(context.getExternalFilesDir(null) ?: context.filesDir, BACKUP_FILENAME)

    fun buildExportJson(
        sessions: List<Session>,
        clients: List<Client>,
        locations: List<Location>,
        availability: List<AvailabilitySlot>,
    ): String = JSONObject().apply {
        put("version", 1)
        put("clients", JSONArray(clients.map { c ->
            JSONObject().apply {
                put("id", c.id); put("name", c.name)
                put("phone", c.phone); put("email", c.email)
                put("colorHex", c.colorHex)
                if (c.hourlyRate > 0) put("hourlyRate", c.hourlyRate)
                if (c.packageTotal > 0) put("packageTotal", c.packageTotal)
                if (c.packageUsed > 0) put("packageUsed", c.packageUsed)
                if (c.birthDate != null) put("birthDate", c.birthDate)
            }
        }))
        put("locations", JSONArray(locations.map { l ->
            JSONObject().apply {
                put("id", l.id); put("name", l.name)
                put("address", l.address); put("colorHex", l.colorHex)
            }
        }))
        put("availability", JSONArray(availability.map { a ->
            JSONObject().apply {
                put("id", a.id); put("date", a.date.toString())
                put("startTime", a.startTime.toStorageString())
                put("endTime", a.endTime.toStorageString())
                if (a.locationId != null) put("locationId", a.locationId)
            }
        }))
        put("sessions", JSONArray(sessions.map { s ->
            JSONObject().apply {
                put("id", s.id); put("date", s.date.toString())
                put("startTime", s.startTime.toStorageString())
                put("endTime", s.endTime.toStorageString())
                put("clientIds", JSONArray(s.clientIds))
                if (s.locationId != null) put("locationId", s.locationId)
                put("notes", s.notes)
            }
        }))
    }.toString(2)

    fun exportAll(
        context: Context,
        sessions: List<Session>,
        clients: List<Client>,
        locations: List<Location>,
        availability: List<AvailabilitySlot>,
    ) {
        try {
            val json = JSONObject().apply {
                put("version", 1)
                put("clients", JSONArray(clients.map { c ->
                    JSONObject().apply {
                        put("id", c.id); put("name", c.name)
                        put("phone", c.phone); put("email", c.email)
                        put("colorHex", c.colorHex)
                        if (c.hourlyRate > 0) put("hourlyRate", c.hourlyRate)
                        if (c.packageTotal > 0) put("packageTotal", c.packageTotal)
                        if (c.packageUsed > 0) put("packageUsed", c.packageUsed)
                        if (c.birthDate != null) put("birthDate", c.birthDate)
                    }
                }))
                put("locations", JSONArray(locations.map { l ->
                    JSONObject().apply {
                        put("id", l.id); put("name", l.name)
                        put("address", l.address); put("colorHex", l.colorHex)
                    }
                }))
                put("availability", JSONArray(availability.map { a ->
                    JSONObject().apply {
                        put("id", a.id); put("date", a.date.toString())
                        put("startTime", a.startTime.toStorageString())
                        put("endTime", a.endTime.toStorageString())
                        if (a.locationId != null) put("locationId", a.locationId)
                    }
                }))
                put("sessions", JSONArray(sessions.map { s ->
                    JSONObject().apply {
                        put("id", s.id); put("date", s.date.toString())
                        put("startTime", s.startTime.toStorageString())
                        put("endTime", s.endTime.toStorageString())
                        put("clientIds", JSONArray(s.clientIds))
                        if (s.locationId != null) put("locationId", s.locationId)
                        put("notes", s.notes)
                    }
                }))
            }
            val dir = context.getExternalFilesDir(null) ?: context.filesDir
            dir.mkdirs()
            val tmp = File(dir, "$BACKUP_FILENAME.tmp")
            tmp.writeText(json.toString(2), Charsets.UTF_8)
            tmp.renameTo(getBackupFile(context))
        } catch (_: Exception) {
            // best-effort — never crash on backup
        }
    }

    fun shareBackup(context: Context) {
        val file = getBackupFile(context)
        if (!file.exists()) {
            android.widget.Toast.makeText(context, "Резервна копія ще не створена", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.provider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Поділитися резервною копією").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    data class ImportedData(
        val clients: List<Client>,
        val locations: List<Location>,
        val availability: List<AvailabilitySlot>,
        val sessions: List<Session>,
    )

    fun parseFromUri(uri: Uri, context: Context): ImportedData? = try {
        val text = context.contentResolver.openInputStream(uri)
            ?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return null
        parseText(text)
    } catch (_: Exception) { null }

    private fun parseText(text: String): ImportedData? = try {
        val root = JSONObject(text)
        val clients = root.getJSONArray("clients").let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Client(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    phone = o.optString("phone"),
                    email = o.optString("email"),
                    colorHex = o.optString("colorHex", "#2196F3"),
                    hourlyRate = o.optDouble("hourlyRate", 0.0),
                    packageTotal = o.optInt("packageTotal", 0),
                    packageUsed = o.optInt("packageUsed", 0),
                    birthDate = if (o.has("birthDate")) o.getString("birthDate") else null,
                )
            }
        }
        val locations = root.getJSONArray("locations").let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Location(o.getLong("id"), o.getString("name"), o.optString("address"),
                    o.optString("colorHex", "#4CAF50"))
            }
        }
        val availability = root.getJSONArray("availability").let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                AvailabilitySlot(o.getLong("id"), LocalDate.parse(o.getString("date")),
                    parseStorageTime(o.getString("startTime")),
                    parseStorageTime(o.getString("endTime")),
                    if (o.has("locationId")) o.getLong("locationId") else null)
            }
        }
        val sessions = root.getJSONArray("sessions").let { arr ->
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val cIds = o.getJSONArray("clientIds")
                Session(o.getLong("id"), LocalDate.parse(o.getString("date")),
                    parseStorageTime(o.getString("startTime")),
                    parseStorageTime(o.getString("endTime")),
                    (0 until cIds.length()).map { cIds.getLong(it) },
                    if (o.has("locationId")) o.getLong("locationId") else null,
                    o.optString("notes"))
            }
        }
        ImportedData(clients, locations, availability, sessions)
    } catch (_: Exception) { null }

    fun replaceAllData(context: Context, data: ImportedData) {
        val db = LinkDatabaseHelper(context).writableDatabase
        db.beginTransaction()
        try {
            db.delete("session_clients", null, null)
            db.delete("sessions", null, null)
            db.delete("clients", null, null)
            db.delete("locations", null, null)
            db.delete("availability", null, null)

            data.clients.forEach { c ->
                db.insertWithOnConflict("clients", null, ContentValues().apply {
                    put("id", c.id); put("name", c.name)
                    put("phone", c.phone); put("email", c.email)
                    put("colorHex", c.colorHex); put("hourlyRate", c.hourlyRate)
                    put("packageTotal", c.packageTotal); put("packageUsed", c.packageUsed)
                    if (c.birthDate != null) put("birthDate", c.birthDate) else putNull("birthDate")
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            data.locations.forEach { l ->
                db.insertWithOnConflict("locations", null, ContentValues().apply {
                    put("id", l.id); put("name", l.name)
                    put("address", l.address); put("colorHex", l.colorHex)
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            data.availability.forEach { a ->
                db.insertWithOnConflict("availability", null, ContentValues().apply {
                    put("id", a.id); put("date", a.date.toString())
                    put("startTime", a.startTime.toStorageString())
                    put("endTime", a.endTime.toStorageString())
                    put("locationId", a.locationId)
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            data.sessions.forEach { s ->
                db.insertWithOnConflict("sessions", null, ContentValues().apply {
                    put("id", s.id); put("date", s.date.toString())
                    put("startTime", s.startTime.toStorageString())
                    put("endTime", s.endTime.toStorageString())
                    put("locationId", s.locationId); put("notes", s.notes)
                }, SQLiteDatabase.CONFLICT_REPLACE)
                s.clientIds.forEach { clientId ->
                    db.insertWithOnConflict("session_clients", null, ContentValues().apply {
                        put("sessionId", s.id); put("clientId", clientId)
                    }, SQLiteDatabase.CONFLICT_REPLACE)
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}
