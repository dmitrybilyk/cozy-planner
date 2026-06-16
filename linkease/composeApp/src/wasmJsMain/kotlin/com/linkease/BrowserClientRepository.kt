package com.linkease

class BrowserClientRepository : ClientRepository {
    private val clients = mutableListOf<Client>()
    private var nextId = 1L

    override fun getAll(): List<Client> = clients.toList()

    override fun save(name: String, phone: String, email: String, colorHex: String): Client {
        val client = Client(nextId++, name, phone, email, colorHex)
        clients.add(client)
        return client
    }

    override fun update(client: Client) {
        val idx = clients.indexOfFirst { it.id == client.id }
        if (idx >= 0) clients[idx] = client
    }

    override fun delete(id: Long) { clients.removeAll { it.id == id } }

    override fun deleteAll() { clients.clear() }
}
