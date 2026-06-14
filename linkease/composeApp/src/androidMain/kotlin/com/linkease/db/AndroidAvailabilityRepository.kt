package com.linkease.db

import android.content.ContentValues
import com.linkease.AvailabilityRepository
import com.linkease.AvailabilitySlot
import com.linkease.parseStorageTime
import com.linkease.toStorageString
import kotlinx.datetime.LocalTime

class AndroidAvailabilityRepository(private val helper: LinkDatabaseHelper) : AvailabilityRepository {

    override fun getAll(): List<AvailabilitySlot> {
        val cursor = helper.readableDatabase.query(
            "availability", null, null, null, null, null, "dayOfWeek ASC, startTime ASC"
        )
        return buildList {
            cursor.use {
                while (it.moveToNext()) {
                    add(AvailabilitySlot(
                        id = it.getLong(it.getColumnIndexOrThrow("id")),
                        dayOfWeek = it.getInt(it.getColumnIndexOrThrow("dayOfWeek")),
                        startTime = parseStorageTime(it.getString(it.getColumnIndexOrThrow("startTime"))),
                        endTime = parseStorageTime(it.getString(it.getColumnIndexOrThrow("endTime"))),
                        locationId = it.getLong(it.getColumnIndexOrThrow("locationId")).takeIf { v -> v != 0L }
                    ))
                }
            }
        }
    }

    override fun save(dayOfWeek: Int, startTime: LocalTime, endTime: LocalTime, locationId: Long?): AvailabilitySlot {
        val values = ContentValues().apply {
            put("dayOfWeek", dayOfWeek)
            put("startTime", startTime.toStorageString())
            put("endTime", endTime.toStorageString())
            put("locationId", locationId)
        }
        val id = helper.writableDatabase.insert("availability", null, values)
        return AvailabilitySlot(id, dayOfWeek, startTime, endTime, locationId)
    }

    override fun update(slot: AvailabilitySlot) {
        val values = ContentValues().apply {
            put("dayOfWeek", slot.dayOfWeek)
            put("startTime", slot.startTime.toStorageString())
            put("endTime", slot.endTime.toStorageString())
            put("locationId", slot.locationId)
        }
        helper.writableDatabase.update("availability", values, "id = ?", arrayOf(slot.id.toString()))
    }

    override fun delete(id: Long) {
        helper.writableDatabase.delete("availability", "id = ?", arrayOf(id.toString()))
    }
}
