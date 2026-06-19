package com.linkease

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

class ApiClientRepository : ClientRepository {
    private val cache = mutableListOf<Client>()
    private var nextTempId = -1L

    suspend fun load() {
        cache.clear()
        cache.addAll(apiGetList("/api/clients", ClientApiDto.serializer()).map { it.toDomain() })
    }

    override fun getAll(): List<Client> = cache.toList()

    // Optimistic write: the UI sees the new row immediately under a temporary negative id;
    // once the server assigns the real id, the cache entry is patched in place.
    override fun save(
        name: String, phone: String, email: String, colorHex: String, hourlyRate: Double,
        packageTotal: Int, packageUsed: Int, birthDate: String?, firebaseClientId: String?,
    ): Client {
        val tempId = nextTempId--
        val client = Client(tempId, name, phone, email, colorHex, hourlyRate, packageTotal, packageUsed, birthDate, firebaseClientId)
        cache.add(client)
        GlobalScope.launch {
            val saved = apiPost("/api/clients", client.toApiDto(), ClientApiDto.serializer()).toDomain()
            val idx = cache.indexOfFirst { it.id == tempId }
            if (idx >= 0) cache[idx] = saved
        }
        return client
    }

    override fun update(client: Client) {
        val idx = cache.indexOfFirst { it.id == client.id }
        if (idx >= 0) cache[idx] = client
        GlobalScope.launch { apiPut("/api/clients/${client.id}", client.toApiDto(), ClientApiDto.serializer()) }
    }

    override fun delete(id: Long) {
        cache.removeAll { it.id == id }
        GlobalScope.launch { apiDelete("/api/clients/$id") }
    }

    override fun deleteAll() {
        cache.clear()
        GlobalScope.launch { apiDelete("/api/clients") }
    }
}
