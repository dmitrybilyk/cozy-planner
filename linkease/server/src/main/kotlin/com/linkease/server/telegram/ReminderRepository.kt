package com.linkease.server.telegram

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class ReminderRepository(private val jdbc: JdbcTemplate) {
    fun unremindedSessionIds(): List<Long> =
        jdbc.query("select id from sessions where reminded = false", { rs, _ -> rs.getLong("id") })

    fun markReminded(sessionId: Long) {
        jdbc.update("update sessions set reminded = true where id = ?", sessionId)
    }
}
