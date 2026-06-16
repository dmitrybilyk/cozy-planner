package com.linkease.server.web

import com.linkease.SessionRepository
import com.linkease.parseStorageTime
import kotlinx.datetime.LocalDate
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/sessions")
class SessionController(private val repo: SessionRepository) {

    @GetMapping
    fun getAll(): List<SessionDto> = repo.getAll().map { it.toDto() }

    @GetMapping("/{id}")
    fun getById(@PathVariable id: Long): ResponseEntity<SessionDto> =
        repo.getById(id)?.toDto()?.let { ResponseEntity.ok(it) } ?: ResponseEntity.notFound().build()

    @PostMapping
    fun create(@RequestBody body: SessionDto): SessionDto =
        repo.save(
            date = LocalDate.parse(body.date),
            startTime = parseStorageTime(body.startTime),
            endTime = parseStorageTime(body.endTime),
            clientIds = body.clientIds,
            locationId = body.locationId,
            notes = body.notes,
        ).toDto()

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody body: SessionDto) {
        repo.update(body.copy(id = id).toDomain())
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long) = repo.delete(id)

    @DeleteMapping
    fun deleteAll() = repo.deleteAll()
}
