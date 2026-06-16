package com.linkease

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class ApiSessionRepository : SessionRepository {
    private val cache = mutableListOf<Session>()
    private var nextTempId = -1L

    suspend fun load() {
        cache.clear()
        cache.addAll(apiGetList("/api/sessions", SessionApiDto.serializer()).map { it.toDomain() })
    }

    override fun getAll(): List<Session> = cache.toList()

    override fun getById(id: Long): Session? = cache.find { it.id == id }

    override fun save(date: LocalDate, startTime: LocalTime, endTime: LocalTime, clientIds: List<Long>, locationId: Long?, notes: String): Session {
        val tempId = nextTempId--
        val session = Session(tempId, date, startTime, endTime, clientIds, locationId, notes)
        cache.add(session)
        GlobalScope.launch {
            val saved = apiPost("/api/sessions", session.toApiDto(), SessionApiDto.serializer()).toDomain()
            val idx = cache.indexOfFirst { it.id == tempId }
            if (idx >= 0) cache[idx] = saved
        }
        return session
    }

    override fun update(session: Session) {
        val idx = cache.indexOfFirst { it.id == session.id }
        if (idx >= 0) cache[idx] = session
        GlobalScope.launch { apiPut("/api/sessions/${session.id}", session.toApiDto(), SessionApiDto.serializer()) }
    }

    override fun delete(id: Long) {
        cache.removeAll { it.id == id }
        GlobalScope.launch { apiDelete("/api/sessions/$id") }
    }

    override fun deleteAll() {
        cache.clear()
        GlobalScope.launch { apiDelete("/api/sessions") }
    }
}
