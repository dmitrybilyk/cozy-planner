package com.linkease.server.web

import com.linkease.AvailabilityRepository
import com.linkease.parseStorageTime
import kotlinx.datetime.LocalDate
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/availability")
class AvailabilityController(private val repo: AvailabilityRepository, private val sse: SseService) {

    @GetMapping
    fun getAll(): List<AvailabilitySlotDto> = repo.getAll().map { it.toDto() }

    @PostMapping
    fun create(@RequestBody body: AvailabilitySlotDto): AvailabilitySlotDto =
        repo.save(LocalDate.parse(body.date), parseStorageTime(body.startTime), parseStorageTime(body.endTime), body.locationId).toDto()
            .also { sse.broadcast("availability") }

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody body: AvailabilitySlotDto) {
        repo.update(body.copy(id = id).toDomain())
        sse.broadcast("availability")
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long) {
        repo.delete(id)
        sse.broadcast("availability")
    }

    @DeleteMapping
    fun deleteAll() {
        repo.deleteAll()
        sse.broadcast("availability")
    }
}
