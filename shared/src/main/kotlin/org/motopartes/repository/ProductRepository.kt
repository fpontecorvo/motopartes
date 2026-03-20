package org.motopartes.repository

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.motopartes.db.Products
import org.motopartes.model.Product

class ProductRepository {

    fun findAll(): List<Product> = transaction {
        Products.selectAll().map { it.toProduct() }
    }

    fun findById(id: Long): Product? = transaction {
        Products.selectAll().where { Products.id eq id }
            .map { it.toProduct() }
            .singleOrNull()
    }

    fun findByCode(code: String): Product? = transaction {
        Products.selectAll().where { Products.code eq code }
            .map { it.toProduct() }
            .singleOrNull()
    }

    fun search(query: String): List<Product> = transaction {
        Products.selectAll().where {
            (Products.code like "%$query%") or (Products.name like "%$query%")
        }.map { it.toProduct() }
    }

    fun insert(product: Product): Product = transaction {
        val id = Products.insertAndGetId {
            it[code] = product.code
            it[name] = product.name
            it[description] = product.description
            it[purchasePrice] = product.purchasePrice
            it[purchaseCurrency] = product.purchaseCurrency
            it[salePrice] = product.salePrice
            it[stock] = product.stock
        }
        product.copy(id = id.value)
    }

    fun update(product: Product): Boolean = transaction {
        Products.update({ Products.id eq product.id }) {
            it[code] = product.code
            it[name] = product.name
            it[description] = product.description
            it[purchasePrice] = product.purchasePrice
            it[purchaseCurrency] = product.purchaseCurrency
            it[salePrice] = product.salePrice
            it[stock] = product.stock
        } > 0
    }

    fun updateStock(id: Long, delta: Int): Boolean = transaction {
        val current = Products.selectAll().where { Products.id eq id }
            .map { it[Products.stock] }
            .singleOrNull() ?: return@transaction false

        val newStock = current + delta
        if (newStock < 0) return@transaction false

        Products.update({ Products.id eq id }) {
            it[stock] = newStock
        } > 0
    }

    fun delete(id: Long): Boolean = transaction {
        Products.deleteWhere { Products.id eq id } > 0
    }

    private fun ResultRow.toProduct() = Product(
        id = this[Products.id].value,
        code = this[Products.code],
        name = this[Products.name],
        description = this[Products.description],
        purchasePrice = this[Products.purchasePrice],
        purchaseCurrency = this[Products.purchaseCurrency],
        salePrice = this[Products.salePrice],
        stock = this[Products.stock]
    )
}
