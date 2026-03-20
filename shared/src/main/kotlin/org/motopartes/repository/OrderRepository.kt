package org.motopartes.repository

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.motopartes.db.Clients
import org.motopartes.db.OrderItems
import org.motopartes.db.Orders
import org.motopartes.model.Order
import org.motopartes.model.OrderItem
import org.motopartes.model.OrderStatus

class OrderRepository {

    fun findAll(): List<Order> = transaction {
        Orders.selectAll()
            .orderBy(Orders.createdAt, SortOrder.DESC)
            .map { it.toOrder() }
    }

    fun findById(id: Long): Order? = transaction {
        val order = Orders.selectAll().where { Orders.id eq id }
            .map { it.toOrder() }
            .singleOrNull() ?: return@transaction null

        val items = OrderItems.selectAll().where { OrderItems.orderId eq id }
            .map { it.toOrderItem() }

        order.copy(items = items)
    }

    fun findByStatus(status: OrderStatus): List<Order> = transaction {
        Orders.selectAll().where { Orders.status eq status }
            .orderBy(Orders.createdAt, SortOrder.DESC)
            .map { it.toOrder() }
    }

    fun findByClient(clientId: Long): List<Order> = transaction {
        Orders.selectAll().where { Orders.clientId eq clientId }
            .orderBy(Orders.createdAt, SortOrder.DESC)
            .map { it.toOrder() }
    }

    fun insert(order: Order): Order = transaction {
        val orderId = Orders.insertAndGetId {
            it[clientId] = order.clientId
            it[status] = order.status
            it[createdAt] = order.createdAt
            it[totalArs] = order.totalArs
        }

        val insertedItems = order.items.map { item ->
            val itemId = OrderItems.insertAndGetId {
                it[OrderItems.orderId] = orderId
                it[productId] = item.productId
                it[quantity] = item.quantity
                it[unitPriceArs] = item.unitPriceArs
            }
            item.copy(id = itemId.value, orderId = orderId.value)
        }

        order.copy(id = orderId.value, items = insertedItems)
    }

    fun updateStatus(id: Long, status: OrderStatus): Boolean = transaction {
        Orders.update({ Orders.id eq id }) {
            it[Orders.status] = status
        } > 0
    }

    fun replaceItems(orderId: Long, items: List<OrderItem>, totalArs: java.math.BigDecimal): Boolean = transaction {
        OrderItems.deleteWhere { OrderItems.orderId eq orderId }
        items.forEach { item ->
            OrderItems.insert {
                it[OrderItems.orderId] = orderId
                it[productId] = item.productId
                it[quantity] = item.quantity
                it[unitPriceArs] = item.unitPriceArs
            }
        }
        Orders.update({ Orders.id eq orderId }) {
            it[Orders.totalArs] = totalArs
        } > 0
    }

    fun delete(id: Long): Boolean = transaction {
        OrderItems.deleteWhere { orderId eq id }
        Orders.deleteWhere { Orders.id eq id } > 0
    }

    fun clientName(clientId: Long): String? = transaction {
        Clients.selectAll().where { Clients.id eq clientId }
            .map { it[Clients.name] }
            .singleOrNull()
    }

    private fun ResultRow.toOrder() = Order(
        id = this[Orders.id].value,
        clientId = this[Orders.clientId].value,
        status = this[Orders.status],
        createdAt = this[Orders.createdAt],
        totalArs = this[Orders.totalArs],
    )

    private fun ResultRow.toOrderItem() = OrderItem(
        id = this[OrderItems.id].value,
        orderId = this[OrderItems.orderId].value,
        productId = this[OrderItems.productId].value,
        quantity = this[OrderItems.quantity],
        unitPriceArs = this[OrderItems.unitPriceArs],
    )
}
