package com.linkease

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class BrowserAvailabilityRepository : AvailabilityRepository {
    private val slots = mutableListOf<AvailabilitySlot>()
    private var nextId = 1L

    override fun getAll(): List<AvailabilitySlot> = slots.toList()

    override fun save(date: LocalDate, startTime: LocalTime, endTime: LocalTime, locationId: Long?): AvailabilitySlot {
        val slot = AvailabilitySlot(nextId++, date, startTime, endTime, locationId)
        slots.add(slot)
        return slot
    }

    override fun update(slot: AvailabilitySlot) {
        val idx = slots.indexOfFirst { it.id == slot.id }
        if (idx >= 0) slots[idx] = slot
    }

    override fun delete(id: Long) { slots.removeAll { it.id == id } }
}
