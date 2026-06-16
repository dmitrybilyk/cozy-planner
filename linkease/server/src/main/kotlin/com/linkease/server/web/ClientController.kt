package com.linkease.server.web

import com.linkease.ClientRepository
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/clients")
class ClientController(private val repo: ClientRepository) {

    @GetMapping
    fun getAll(): List<ClientDto> = repo.getAll().map { it.toDto() }

    @PostMapping
    fun create(@RequestBody body: ClientDto): ClientDto =
        repo.save(body.name, body.phone, body.email, body.colorHex, body.hourlyRate).toDto()

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @RequestBody body: ClientDto) {
        repo.update(body.copy(id = id).toDomain())
    }

    @DeleteMapping("/{id}")
    fun delete(@PathVariable id: Long) = repo.delete(id)

    @DeleteMapping
    fun deleteAll() = repo.deleteAll()
}
