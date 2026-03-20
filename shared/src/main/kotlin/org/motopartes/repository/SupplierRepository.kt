package org.motopartes.repository

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.motopartes.db.Suppliers
import org.motopartes.model.Supplier

class SupplierRepository {

    fun get(): Supplier? = transaction {
        Suppliers.selectAll().map { it.toSupplier() }.firstOrNull()
    }

    fun insert(supplier: Supplier): Supplier = transaction {
        val id = Suppliers.insertAndGetId {
            it[name] = supplier.name
            it[phone] = supplier.phone
            it[balance] = supplier.balance
        }
        supplier.copy(id = id.value)
    }

    fun update(supplier: Supplier): Boolean = transaction {
        Suppliers.update({ Suppliers.id eq supplier.id }) {
            it[name] = supplier.name
            it[phone] = supplier.phone
            it[balance] = supplier.balance
        } > 0
    }

    private fun ResultRow.toSupplier() = Supplier(
        id = this[Suppliers.id].value,
        name = this[Suppliers.name],
        phone = this[Suppliers.phone],
        balance = this[Suppliers.balance]
    )
}
