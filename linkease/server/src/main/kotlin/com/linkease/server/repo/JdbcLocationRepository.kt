package com.linkease.server.repo

import com.linkease.Location
import com.linkease.LocationRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository

private val LOCATION_ROW_MAPPER = RowMapper { rs, _ ->
    Location(
        id = rs.getLong("id"),
        name = rs.getString("name"),
        address = rs.getString("address"),
        colorHex = rs.getString("color_hex"),
        mapsLink = rs.getString("maps_link"),
    )
}

@Repository
class JdbcLocationRepository(private val jdbc: JdbcTemplate) : LocationRepository {

    override fun getAll(): List<Location> =
        jdbc.query("select * from locations order by id", LOCATION_ROW_MAPPER)

    override fun save(name: String, address: String, colorHex: String, mapsLink: String?): Location {
        val id = jdbc.queryForObject(
            "insert into locations (name, address, color_hex, maps_link) values (?, ?, ?, ?) returning id",
            Long::class.java, name, address, colorHex, mapsLink,
        )!!
        return Location(id, name, address, colorHex, mapsLink)
    }

    override fun update(location: Location) {
        jdbc.update(
            "update locations set name = ?, address = ?, color_hex = ?, maps_link = ? where id = ?",
            location.name, location.address, location.colorHex, location.mapsLink, location.id,
        )
    }

    override fun delete(id: Long) {
        jdbc.update("delete from locations where id = ?", id)
    }

    override fun deleteAll() {
        jdbc.update("delete from locations")
    }
}
