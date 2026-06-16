package com.linkease

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

interface SessionRepository {
    fun getAll(): List<Session>
    fun getById(id: Long): Session?
    fun save(
        date: LocalDate,
        startTime: LocalTime,
        endTime: LocalTime,
        clientIds: List<Long>,
        locationId: Long?,
        notes: String
    ): Session
    fun update(session: Session)
    fun delete(id: Long)
    fun deleteAll()
}
