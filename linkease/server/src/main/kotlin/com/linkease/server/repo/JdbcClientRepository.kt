package com.linkease.server.repo

import com.linkease.Client
import com.linkease.ClientRepository
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowMapper
import org.springframework.stereotype.Repository

private val CLIENT_ROW_MAPPER = RowMapper { rs, _ ->
    Client(
        id = rs.getLong("id"),
        name = rs.getString("name"),
        phone = rs.getString("phone"),
        email = rs.getString("email"),
        colorHex = rs.getString("color_hex"),
        hourlyRate = rs.getDouble("hourly_rate"),
    )
}

@Repository
class JdbcClientRepository(private val jdbc: JdbcTemplate) : ClientRepository {

    override fun getAll(): List<Client> =
        jdbc.query("select * from clients order by id", CLIENT_ROW_MAPPER)

    override fun save(name: String, phone: String, email: String, colorHex: String, hourlyRate: Double): Client {
        val id = jdbc.queryForObject(
            "insert into clients (name, phone, email, color_hex, hourly_rate) values (?, ?, ?, ?, ?) returning id",
            Long::class.java, name, phone, email, colorHex, hourlyRate,
        )!!
        return Client(id, name, phone, email, colorHex, hourlyRate)
    }

    override fun update(client: Client) {
        jdbc.update(
            "update clients set name = ?, phone = ?, email = ?, color_hex = ?, hourly_rate = ? where id = ?",
            client.name, client.phone, client.email, client.colorHex, client.hourlyRate, client.id,
        )
    }

    override fun delete(id: Long) {
        jdbc.update("delete from clients where id = ?", id)
    }

    override fun deleteAll() {
        jdbc.update("delete from clients")
    }
}
