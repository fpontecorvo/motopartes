package org.motopartes.repository

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.motopartes.db.FinancialMovements
import org.motopartes.model.FinancialMovement

class FinancialMovementRepository {

    fun findAll(): List<FinancialMovement> = transaction {
        FinancialMovements.selectAll()
            .orderBy(FinancialMovements.date, SortOrder.DESC)
            .map { it.toMovement() }
    }

    fun findByClient(clientId: Long): List<FinancialMovement> = transaction {
        FinancialMovements.selectAll().where { FinancialMovements.clientId eq clientId }
            .orderBy(FinancialMovements.date, SortOrder.DESC)
            .map { it.toMovement() }
    }

    fun findBySupplier(supplierId: Long): List<FinancialMovement> = transaction {
        FinancialMovements.selectAll().where { FinancialMovements.supplierId eq supplierId }
            .orderBy(FinancialMovements.date, SortOrder.DESC)
            .map { it.toMovement() }
    }

    fun insert(movement: FinancialMovement): FinancialMovement = transaction {
        val id = FinancialMovements.insertAndGetId {
            it[type] = movement.type
            it[amount] = movement.amount
            it[date] = movement.date
            it[clientId] = movement.clientId
            it[supplierId] = movement.supplierId
            it[orderId] = movement.orderId
            it[description] = movement.description
        }
        movement.copy(id = id.value)
    }

    private fun ResultRow.toMovement() = FinancialMovement(
        id = this[FinancialMovements.id].value,
        type = this[FinancialMovements.type],
        amount = this[FinancialMovements.amount],
        date = this[FinancialMovements.date],
        clientId = this[FinancialMovements.clientId]?.value,
        supplierId = this[FinancialMovements.supplierId]?.value,
        orderId = this[FinancialMovements.orderId]?.value,
        description = this[FinancialMovements.description]
    )
}
