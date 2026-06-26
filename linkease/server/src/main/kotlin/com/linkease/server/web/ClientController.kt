package com.linkease.server.web

import com.linkease.ClientRepository
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/clients")
class ClientController(private val repo: ClientRepository, private val sse: SseService) {

    @GetMapping
    fun getAll(): List<ClientDto> = repo.getAll().map { it.toDto() }

    @PostMapping
    fun create(@RequestBody body: ClientDto): ClientDto =
        repo.save(body.name, body.phone, body.email, body.colorHex, body.hourlyRate, body.packageTotal, body.packageUsed, body.birthDate).toDto()
            .also { sse.broadcast("clients") }

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody body: ClientDto) {
        repo.update(body.copy(id = id).toDomain())
        sse.broadcast("clients")
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long) {
        repo.delete(id)
        sse.broadcast("clients")
    }

    @DeleteMapping
    fun deleteAll() {
        repo.deleteAll()
        sse.broadcast("clients")
    }
}
