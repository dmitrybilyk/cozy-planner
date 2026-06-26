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
        packageTotal = rs.getInt("package_total"),
        packageUsed = rs.getInt("package_used"),
        birthDate = rs.getString("birth_date"),
    )
}

@Repository
class JdbcClientRepository(private val jdbc: JdbcTemplate) : ClientRepository {

    override fun getAll(): List<Client> =
        jdbc.query("select * from clients order by id", CLIENT_ROW_MAPPER)

    override fun save(
        name: String, phone: String, email: String, colorHex: String, hourlyRate: Double,
        packageTotal: Int, packageUsed: Int, birthDate: String?, firebaseClientId: String?,
    ): Client {
        val id = jdbc.queryForObject(
            "insert into clients (name, phone, email, color_hex, hourly_rate, package_total, package_used, birth_date) values (?, ?, ?, ?, ?, ?, ?, ?) returning id",
            Long::class.java, name, phone, email, colorHex, hourlyRate, packageTotal, packageUsed, birthDate,
        )!!
        return Client(id, name, phone, email, colorHex, hourlyRate, packageTotal, packageUsed, birthDate)
    }

    override fun update(client: Client) {
        jdbc.update(
            "update clients set name = ?, phone = ?, email = ?, color_hex = ?, hourly_rate = ?, package_total = ?, package_used = ?, birth_date = ? where id = ?",
            client.name, client.phone, client.email, client.colorHex, client.hourlyRate,
            client.packageTotal, client.packageUsed, client.birthDate, client.id,
        )
    }

    override fun delete(id: Long) {
        jdbc.update("delete from clients where id = ?", id)
    }

    override fun deleteAll() {
        jdbc.update("delete from clients")
    }
}
