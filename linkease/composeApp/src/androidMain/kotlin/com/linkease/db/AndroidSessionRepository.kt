package com.linkease.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.linkease.Session
import com.linkease.SessionRepository
import com.linkease.parseStorageTime
import com.linkease.toStorageString
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class AndroidSessionRepository(private val helper: LinkDatabaseHelper) : SessionRepository {

    override fun getAll(): List<Session> {
        val cursor = helper.readableDatabase.query(
            "sessions", null, null, null, null, null, "date ASC, startTime ASC"
        )
        return buildList {
            cursor.use {
                while (it.moveToNext()) {
                    val id = it.getLong(it.getColumnIndexOrThrow("id"))
                    add(Session(
                        id = id,
                        date = LocalDate.parse(it.getString(it.getColumnIndexOrThrow("date"))),
                        startTime = parseStorageTime(it.getString(it.getColumnIndexOrThrow("startTime"))),
                        endTime = parseStorageTime(it.getString(it.getColumnIndexOrThrow("endTime"))),
                        clientIds = getClientIdsForSession(id),
                        locationId = it.getLong(it.getColumnIndexOrThrow("locationId")).takeIf { v -> v != 0L },
                        notes = it.getString(it.getColumnIndexOrThrow("notes"))
                    ))
                }
            }
        }
    }

    override fun getById(id: Long): Session? {
        val cursor = helper.readableDatabase.query(
            "sessions", null, "id = ?", arrayOf(id.toString()), null, null, null
        )
        return cursor.use {
            if (!it.moveToFirst()) return null
            Session(
                id = id,
                date = LocalDate.parse(it.getString(it.getColumnIndexOrThrow("date"))),
                startTime = parseStorageTime(it.getString(it.getColumnIndexOrThrow("startTime"))),
                endTime = parseStorageTime(it.getString(it.getColumnIndexOrThrow("endTime"))),
                clientIds = getClientIdsForSession(id),
                locationId = it.getLong(it.getColumnIndexOrThrow("locationId")).takeIf { v -> v != 0L },
                notes = it.getString(it.getColumnIndexOrThrow("notes"))
            )
        }
    }

    override fun save(date: LocalDate, startTime: LocalTime, endTime: LocalTime, clientIds: List<Long>, locationId: Long?, notes: String): Session {
        val db = helper.writableDatabase
        val values = ContentValues().apply {
            put("date", date.toString())
            put("startTime", startTime.toStorageString())
            put("endTime", endTime.toStorageString())
            put("locationId", locationId)
            put("notes", notes)
        }
        val id = db.insert("sessions", null, values)
        saveClientLinks(db, id, clientIds)
        return Session(id, date, startTime, endTime, clientIds, locationId, notes)
    }

    override fun update(session: Session) {
        val db = helper.writableDatabase
        val values = ContentValues().apply {
            put("date", session.date.toString())
            put("startTime", session.startTime.toStorageString())
            put("endTime", session.endTime.toStorageString())
            put("locationId", session.locationId)
            put("notes", session.notes)
        }
        db.update("sessions", values, "id = ?", arrayOf(session.id.toString()))
        db.delete("session_clients", "sessionId = ?", arrayOf(session.id.toString()))
        saveClientLinks(db, session.id, session.clientIds)
    }

    override fun delete(id: Long) {
        val db = helper.writableDatabase
        db.delete("session_clients", "sessionId = ?", arrayOf(id.toString()))
        db.delete("sessions", "id = ?", arrayOf(id.toString()))
    }

    override fun deleteAll() {
        val db = helper.writableDatabase
        db.delete("session_clients", null, null)
        db.delete("sessions", null, null)
    }

    private fun getClientIdsForSession(sessionId: Long): List<Long> {
        val cursor = helper.readableDatabase.query(
            "session_clients", arrayOf("clientId"), "sessionId = ?", arrayOf(sessionId.toString()), null, null, null
        )
        return buildList { cursor.use { while (it.moveToNext()) add(it.getLong(0)) } }
    }

    private fun saveClientLinks(db: SQLiteDatabase, sessionId: Long, clientIds: List<Long>) {
        clientIds.forEach { clientId ->
            val cv = ContentValues().apply { put("sessionId", sessionId); put("clientId", clientId) }
            db.insert("session_clients", null, cv)
        }
    }
}
