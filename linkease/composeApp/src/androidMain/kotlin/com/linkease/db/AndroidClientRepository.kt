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
                    val pkgTotalIdx = it.getColumnIndex("packageTotal")
                    val pkgUsedIdx = it.getColumnIndex("packageUsed")
                    val birthIdx = it.getColumnIndex("birthDate")
                    add(Client(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        name = it.getString(it.getColumnIndexOrThrow("name")),
                        phone = it.getString(it.getColumnIndexOrThrow("phone")),
                        email = it.getString(it.getColumnIndexOrThrow("email")),
                        colorHex = it.getString(it.getColumnIndexOrThrow("colorHex")),
                        hourlyRate = if (rateIdx >= 0) it.getDouble(rateIdx) else 0.0,
                        packageTotal = if (pkgTotalIdx >= 0) it.getInt(pkgTotalIdx) else 0,
                        packageUsed = if (pkgUsedIdx >= 0) it.getInt(pkgUsedIdx) else 0,
                        birthDate = if (birthIdx >= 0 && !it.isNull(birthIdx)) it.getString(birthIdx) else null,
                        firebaseClientId = it.getColumnIndex("firebaseClientId").let { idx ->
                            if (idx >= 0 && !it.isNull(idx)) it.getString(idx) else null
                        },
                    ))
                }
            }
        }
    }

    override fun save(
        name: String, phone: String, email: String, colorHex: String, hourlyRate: Double,
        packageTotal: Int, packageUsed: Int, birthDate: String?, firebaseClientId: String?,
    ): Client {
        val values = ContentValues().apply {
            put("name", name); put("phone", phone); put("email", email)
            put("colorHex", colorHex); put("hourlyRate", hourlyRate)
            put("packageTotal", packageTotal); put("packageUsed", packageUsed)
            if (birthDate != null) put("birthDate", birthDate) else putNull("birthDate")
            if (firebaseClientId != null) put("firebaseClientId", firebaseClientId) else putNull("firebaseClientId")
        }
        val id = helper.writableDatabase.insert("clients", null, values)
        return Client(id, name, phone, email, colorHex, hourlyRate, packageTotal, packageUsed, birthDate, firebaseClientId)
    }

    override fun update(client: Client) {
        val values = ContentValues().apply {
            put("name", client.name); put("phone", client.phone)
            put("email", client.email); put("colorHex", client.colorHex)
            put("hourlyRate", client.hourlyRate)
            put("packageTotal", client.packageTotal); put("packageUsed", client.packageUsed)
            if (client.birthDate != null) put("birthDate", client.birthDate) else putNull("birthDate")
            if (client.firebaseClientId != null) put("firebaseClientId", client.firebaseClientId) else putNull("firebaseClientId")
        }
        helper.writableDatabase.update("clients", values, "id = ?", arrayOf(client.id.toString()))
    }

    override fun delete(id: Long) {
        helper.writableDatabase.delete("clients", "id = ?", arrayOf(id.toString()))
    }

    override fun deleteAll() {
        helper.writableDatabase.delete("clients", null, null)
    }
}
