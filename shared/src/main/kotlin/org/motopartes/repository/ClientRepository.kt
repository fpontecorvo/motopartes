package org.motopartes.repository

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.motopartes.db.Clients
import org.motopartes.model.Client

class ClientRepository {

    fun findAll(): List<Client> = transaction {
        Clients.selectAll().map { it.toClient() }
    }

    fun findById(id: Long): Client? = transaction {
        Clients.selectAll().where { Clients.id eq id }
            .map { it.toClient() }
            .singleOrNull()
    }

    fun search(query: String): List<Client> = transaction {
        Clients.selectAll().where {
            (Clients.name like "%$query%") or (Clients.phone like "%$query%")
        }.map { it.toClient() }
    }

    fun insert(client: Client): Client = transaction {
        val id = Clients.insertAndGetId {
            it[name] = client.name
            it[phone] = client.phone
            it[address] = client.address
            it[balance] = client.balance
        }
        client.copy(id = id.value)
    }

    fun update(client: Client): Boolean = transaction {
        Clients.update({ Clients.id eq client.id }) {
            it[name] = client.name
            it[phone] = client.phone
            it[address] = client.address
            it[balance] = client.balance
        } > 0
    }

    fun delete(id: Long): Boolean = transaction {
        Clients.deleteWhere { Clients.id eq id } > 0
    }

    private fun ResultRow.toClient() = Client(
        id = this[Clients.id].value,
        name = this[Clients.name],
        phone = this[Clients.phone],
        address = this[Clients.address],
        balance = this[Clients.balance]
    )
}
