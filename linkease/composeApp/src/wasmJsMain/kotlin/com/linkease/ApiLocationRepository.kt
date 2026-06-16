package com.linkease

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class ApiLocationRepository : LocationRepository {
    private val cache = mutableListOf<Location>()
    private var nextTempId = -1L

    suspend fun load() {
        cache.clear()
        cache.addAll(apiGetList("/api/locations", LocationApiDto.serializer()).map { it.toDomain() })
    }

    override fun getAll(): List<Location> = cache.toList()

    override fun save(name: String, address: String, colorHex: String, mapsLink: String?): Location {
        val tempId = nextTempId--
        val location = Location(tempId, name, address, colorHex, mapsLink)
        cache.add(location)
        GlobalScope.launch {
            val saved = apiPost("/api/locations", location.toApiDto(), LocationApiDto.serializer()).toDomain()
            val idx = cache.indexOfFirst { it.id == tempId }
            if (idx >= 0) cache[idx] = saved
        }
        return location
    }

    override fun update(location: Location) {
        val idx = cache.indexOfFirst { it.id == location.id }
        if (idx >= 0) cache[idx] = location
        GlobalScope.launch { apiPut("/api/locations/${location.id}", location.toApiDto(), LocationApiDto.serializer()) }
    }

    override fun delete(id: Long) {
        cache.removeAll { it.id == id }
        GlobalScope.launch { apiDelete("/api/locations/$id") }
    }

    override fun deleteAll() {
        cache.clear()
        GlobalScope.launch { apiDelete("/api/locations") }
    }
}
