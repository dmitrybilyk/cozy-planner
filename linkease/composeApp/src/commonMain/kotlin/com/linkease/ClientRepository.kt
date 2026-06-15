package com.linkease

interface ClientRepository {
    fun getAll(): List<Client>
    fun save(name: String, phone: String, email: String, colorHex: String, hourlyRate: Double = 0.0): Client
    fun update(client: Client)
    fun delete(id: Long)
}
