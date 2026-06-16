package com.linkease.server.repo

import com.linkease.AvailabilityRepository
import com.linkease.AvailabilitySlot
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository

private val AVAILABILITY_ROW_MAPPER = RowMapper { rs, _ ->
    AvailabilitySlot(
        id = rs.getLong("id"),
        date = LocalDate.parse(rs.getString("date")),
        startTime = LocalTime.parse(rs.getString("start_time")),
        endTime = LocalTime.parse(rs.getString("end_time")),
        locationId = rs.getObject("location_id") as Long?,
    )
}

@Repository
class JdbcAvailabilityRepository(private val jdbc: JdbcTemplate) : AvailabilityRepository {

    override fun getAll(): List<AvailabilitySlot> =
        jdbc.query("select * from availability_slots order by date, start_time", AVAILABILITY_ROW_MAPPER)

    override fun save(date: LocalDate, startTime: LocalTime, endTime: LocalTime, locationId: Long?): AvailabilitySlot {
        val id = jdbc.queryForObject(
            "insert into availability_slots (date, start_time, end_time, location_id) values (?, ?, ?, ?) returning id",
            Long::class.java, date.toString(), startTime.toString(), endTime.toString(), locationId,
        )!!
        return AvailabilitySlot(id, date, startTime, endTime, locationId)
    }

    override fun update(slot: AvailabilitySlot) {
        jdbc.update(
            "update availability_slots set date = ?, start_time = ?, end_time = ?, location_id = ? where id = ?",
            slot.date.toString(), slot.startTime.toString(), slot.endTime.toString(), slot.locationId, slot.id,
        )
    }

    override fun delete(id: Long) {
        jdbc.update("delete from availability_slots where id = ?", id)
    }

    override fun deleteAll() {
        jdbc.update("delete from availability_slots")
    }
}
