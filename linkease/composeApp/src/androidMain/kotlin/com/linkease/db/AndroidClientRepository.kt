package com.linkease.db

import android.content.ContentValues
import com.linkease.Client
import com.linkease.ClientRepository

class AndroidClientRepository(private val helper: LinkDatabaseHelper) : ClientRepository {

    override fun getAll(): List<Client> {
        val cursor = helper.readableDatabase.query("clients", null, null, null, null, null, "name ASC")
        return buildList {
            cursor.use {
                while (it.moveToNext()) {
                    val rateIdx = it.getColumnIndex("hourlyRate")
                    add(Client(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        name = it.getString(it.getColumnIndexOrThrow("name")),
                        phone = it.getString(it.getColumnIndexOrThrow("phone")),
                        email = it.getString(it.getColumnIndexOrThrow("email")),
                        colorHex = it.getString(it.getColumnIndexOrThrow("colorHex")),
                        hourlyRate = if (rateIdx >= 0) it.getDouble(rateIdx) else 0.0
                    ))
                }
            }
        }
    }

    override fun save(name: String, phone: String, email: String, colorHex: String, hourlyRate: Double): Client {
        val values = ContentValues().apply {
            put("name", name); put("phone", phone); put("email", email)
            put("colorHex", colorHex); put("hourlyRate", hourlyRate)
        }
        val id = helper.writableDatabase.insert("clients", null, values)
        return Client(id, name, phone, email, colorHex, hourlyRate)
    }

    override fun update(client: Client) {
        val values = ContentValues().apply {
            put("name", client.name); put("phone", client.phone)
            put("email", client.email); put("colorHex", client.colorHex)
            put("hourlyRate", client.hourlyRate)
        }
        helper.writableDatabase.update("clients", values, "id = ?", arrayOf(client.id.toString()))
    }

    override fun delete(id: Long) {
        helper.writableDatabase.delete("clients", "id = ?", arrayOf(id.toString()))
    }
}
