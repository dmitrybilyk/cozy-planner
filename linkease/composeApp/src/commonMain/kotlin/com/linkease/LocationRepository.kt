package com.linkease

interface LocationRepository {
    fun getAll(): List<Location>
    fun save(name: String, address: String, colorHex: String, mapsLink: String? = null): Location
    fun update(location: Location)
    fun delete(id: Long)
    fun deleteAll()
}
