package com.linkease

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class BrowserSessionRepository : SessionRepository {
    private val sessions = mutableListOf<Session>()
    private var nextId = 1L

    override fun getAll(): List<Session> = sessions.toList()

    override fun getById(id: Long): Session? = sessions.find { it.id == id }

    override fun save(date: LocalDate, startTime: LocalTime, endTime: LocalTime, clientIds: List<Long>, locationId: Long?, notes: String): Session {
        val session = Session(nextId++, date, startTime, endTime, clientIds, locationId, notes)
        sessions.add(session)
        return session
    }

    override fun update(session: Session) {
        val idx = sessions.indexOfFirst { it.id == session.id }
        if (idx >= 0) sessions[idx] = session
    }

    override fun delete(id: Long) { sessions.removeAll { it.id == id } }

    override fun deleteAll() { sessions.clear() }
}
