package com.linkease.db

import android.content.ContentValues
import com.linkease.Location
import com.linkease.LocationRepository

class AndroidLocationRepository(private val helper: LinkDatabaseHelper) : LocationRepository {

    override fun getAll(): List<Location> {
        val cursor = helper.readableDatabase.query("locations", null, null, null, null, null, "name ASC")
        return buildList {
            cursor.use {
                while (it.moveToNext()) {
                    add(Location(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        name = it.getString(it.getColumnIndexOrThrow("name")),
                        address = it.getString(it.getColumnIndexOrThrow("address")),
                        colorHex = it.getString(it.getColumnIndexOrThrow("colorHex")),
                        mapsLink = it.getString(it.getColumnIndexOrThrow("mapsLink"))
                    ))
                }
            }
        }
    }

    override fun save(name: String, address: String, colorHex: String, mapsLink: String?): Location {
        val values = ContentValues().apply {
            put("name", name); put("address", address); put("colorHex", colorHex); put("mapsLink", mapsLink)
        }
        val id = helper.writableDatabase.insert("locations", null, values)
        return Location(id, name, address, colorHex, mapsLink)
    }

    override fun update(location: Location) {
        val values = ContentValues().apply {
            put("name", location.name); put("address", location.address); put("colorHex", location.colorHex)
            put("mapsLink", location.mapsLink)
        }
        helper.writableDatabase.update("locations", values, "id = ?", arrayOf(location.id.toString()))
    }

    override fun delete(id: Long) {
        helper.writableDatabase.delete("locations", "id = ?", arrayOf(id.toString()))
    }

    override fun deleteAll() {
        helper.writableDatabase.delete("locations", null, null)
    }
}
