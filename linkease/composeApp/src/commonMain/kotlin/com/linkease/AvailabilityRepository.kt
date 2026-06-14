package com.linkease

import kotlinx.datetime.LocalTime

interface AvailabilityRepository {
    fun getAll(): List<AvailabilitySlot>
    fun save(dayOfWeek: Int, startTime: LocalTime, endTime: LocalTime, locationId: Long?): AvailabilitySlot
    fun update(slot: AvailabilitySlot)
    fun delete(id: Long)
}
