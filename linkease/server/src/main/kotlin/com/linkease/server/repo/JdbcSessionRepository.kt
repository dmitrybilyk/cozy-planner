package com.linkease.server.repo

import com.linkease.Session
import com.linkease.SessionRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

private val SESSION_ROW_MAPPER = RowMapper { rs, _ ->
    Session(
        id = rs.getLong("id"),
        date = LocalDate.parse(rs.getString("date")),
        startTime = LocalTime.parse(rs.getString("start_time")),
        endTime = LocalTime.parse(rs.getString("end_time")),
        locationId = rs.getObject("location_id") as Long?,
        notes = rs.getString("notes"),
    )
}

@Repository
class JdbcSessionRepository(private val jdbc: JdbcTemplate) : SessionRepository {

    private fun clientIdsBySession(): Map<Long, List<Long>> =
        jdbc.query("select session_id, client_id from session_clients") { rs, _ ->
            rs.getLong("session_id") to rs.getLong("client_id")
        }.groupBy({ it.first }, { it.second })

    override fun getAll(): List<Session> {
        val sessions = jdbc.query("select * from sessions order by date, start_time", SESSION_ROW_MAPPER)
        val clientIds = clientIdsBySession()
        return sessions.map { it.copy(clientIds = clientIds[it.id].orEmpty()) }
    }

    override fun getById(id: Long): Session? {
        val session = jdbc.query("select * from sessions where id = ?", SESSION_ROW_MAPPER, id).firstOrNull()
            ?: return null
        val clientIds = jdbc.query(
            "select client_id from session_clients where session_id = ?",
            { rs, _ -> rs.getLong("client_id") },
            id,
        )
        return session.copy(clientIds = clientIds)
    }

    @Transactional
    override fun save(
        date: LocalDate,
        startTime: LocalTime,
        endTime: LocalTime,
        clientIds: List<Long>,
        locationId: Long?,
        notes: String,
    ): Session {
        val id = jdbc.queryForObject(
            "insert into sessions (date, start_time, end_time, location_id, notes) values (?, ?, ?, ?, ?) returning id",
            Long::class.java, date.toString(), startTime.toString(), endTime.toString(), locationId, notes,
        )!!
        linkClients(id, clientIds)
        return Session(id, date, startTime, endTime, clientIds, locationId, notes)
    }

    @Transactional
    override fun update(session: Session) {
        jdbc.update(
            "update sessions set date = ?, start_time = ?, end_time = ?, location_id = ?, notes = ? where id = ?",
            session.date.toString(), session.startTime.toString(), session.endTime.toString(),
            session.locationId, session.notes, session.id,
        )
        jdbc.update("delete from session_clients where session_id = ?", session.id)
        linkClients(session.id, session.clientIds)
    }

    private fun linkClients(sessionId: Long, clientIds: List<Long>) {
        clientIds.forEach { clientId ->
            jdbc.update("insert into session_clients (session_id, client_id) values (?, ?)", sessionId, clientId)
        }
    }

    override fun delete(id: Long) {
        jdbc.update("delete from sessions where id = ?", id)
    }

    override fun deleteAll() {
        jdbc.update("delete from sessions")
    }
}
