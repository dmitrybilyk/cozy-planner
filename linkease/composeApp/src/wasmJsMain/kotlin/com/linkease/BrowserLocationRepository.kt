package com.linkease

class BrowserLocationRepository : LocationRepository {
    private val locations = mutableListOf<Location>()
    private var nextId = 1L

    override fun getAll(): List<Location> = locations.toList()

    override fun save(name: String, address: String, colorHex: String, mapsLink: String?): Location {
        val location = Location(nextId++, name, address, colorHex, mapsLink)
        locations.add(location)
        return location
    }

    override fun update(location: Location) {
        val idx = locations.indexOfFirst { it.id == location.id }
        if (idx >= 0) locations[idx] = location
    }

    override fun delete(id: Long) { locations.removeAll { it.id == id } }

    override fun deleteAll() { locations.clear() }
}
