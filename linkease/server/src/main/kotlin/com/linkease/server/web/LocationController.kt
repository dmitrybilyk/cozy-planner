package com.linkease.server.web

import com.linkease.LocationRepository
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/locations")
class LocationController(private val repo: LocationRepository) {

    @GetMapping
    fun getAll(): List<LocationDto> = repo.getAll().map { it.toDto() }

    @PostMapping
    fun create(@RequestBody body: LocationDto): LocationDto =
        repo.save(body.name, body.address, body.colorHex, body.mapsLink).toDto()

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody body: LocationDto) {
        repo.update(body.copy(id = id).toDomain())
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long) = repo.delete(id)

    @DeleteMapping
    fun deleteAll() = repo.deleteAll()
}
