package com.linkease

interface ClientRepository {
    fun getAll(): List<Client>
    fun save(
        name: String, phone: String, email: String, colorHex: String, hourlyRate: Double = 0.0,
        packageTotal: Int = 0, packageUsed: Int = 0, birthDate: String? = null,
        firebaseClientId: String? = null,
    ): Client
    fun update(client: Client)
    fun delete(id: Long)
    fun deleteAll()
}
