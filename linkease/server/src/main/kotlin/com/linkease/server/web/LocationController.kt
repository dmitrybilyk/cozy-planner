package com.linkease.server.web

import com.linkease.LocationRepository
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/locations")
class LocationController(private val repo: LocationRepository, private val sse: SseService) {

    @GetMapping
    fun getAll(): List<LocationDto> = repo.getAll().map { it.toDto() }

    @PostMapping
    fun create(@RequestBody body: LocationDto): LocationDto =
        repo.save(body.name, body.address, body.colorHex, body.mapsLink).toDto()
            .also { sse.broadcast("locations") }

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody body: LocationDto) {
        repo.update(body.copy(id = id).toDomain())
        sse.broadcast("locations")
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long) {
        repo.delete(id)
        sse.broadcast("locations")
    }

    @DeleteMapping
    fun deleteAll() {
        repo.deleteAll()
        sse.broadcast("locations")
    }
}
