package com.linkease

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

class ApiAvailabilityRepository : AvailabilityRepository {
    private val cache = mutableListOf<AvailabilitySlot>()
    private var nextTempId = -1L

    suspend fun load() {
        cache.clear()
        cache.addAll(apiGetList("/api/availability", AvailabilitySlotApiDto.serializer()).map { it.toDomain() })
    }

    override fun getAll(): List<AvailabilitySlot> = cache.toList()

    override fun save(date: LocalDate, startTime: LocalTime, endTime: LocalTime, locationId: Long?): AvailabilitySlot {
        val tempId = nextTempId--
        val slot = AvailabilitySlot(tempId, date, startTime, endTime, locationId)
        cache.add(slot)
        GlobalScope.launch {
            val saved = apiPost("/api/availability", slot.toApiDto(), AvailabilitySlotApiDto.serializer()).toDomain()
            val idx = cache.indexOfFirst { it.id == tempId }
            if (idx >= 0) cache[idx] = saved
        }
        return slot
    }

    override fun update(slot: AvailabilitySlot) {
        val idx = cache.indexOfFirst { it.id == slot.id }
        if (idx >= 0) cache[idx] = slot
        GlobalScope.launch { apiPut("/api/availability/${slot.id}", slot.toApiDto(), AvailabilitySlotApiDto.serializer()) }
    }

    override fun delete(id: Long) {
        cache.removeAll { it.id == id }
        GlobalScope.launch { apiDelete("/api/availability/$id") }
    }

    override fun deleteAll() {
        cache.clear()
        GlobalScope.launch { apiDelete("/api/availability") }
    }
}
