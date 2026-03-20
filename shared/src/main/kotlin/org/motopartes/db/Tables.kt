package org.motopartes.db

import org.jetbrains.exposed.dao.id.LongIdTable
import org.jetbrains.exposed.sql.kotlin.datetime.date
import org.jetbrains.exposed.sql.kotlin.datetime.datetime
import org.motopartes.model.Currency
import org.motopartes.model.MovementType
import org.motopartes.model.OrderStatus

object Products : LongIdTable("products") {
    val code = varchar("code", 50).uniqueIndex()
    val name = varchar("name", 255)
    val description = text("description").default("")
    val purchasePrice = decimal("price", 12, 2)
    val purchaseCurrency = enumerationByName<Currency>("currency", 3)
    val salePrice = decimal("sale_price", 12, 2).default(java.math.BigDecimal.ZERO)
    val stock = integer("stock").default(0)
}

object Clients : LongIdTable("clients") {
    val name = varchar("name", 255)
    val phone = varchar("phone", 50).default("")
    val address = varchar("address", 500).default("")
    val balance = decimal("balance", 12, 2).default(java.math.BigDecimal.ZERO)
}

object Suppliers : LongIdTable("suppliers") {
    val name = varchar("name", 255)
    val phone = varchar("phone", 50).default("")
    val balance = decimal("balance", 12, 2).default(java.math.BigDecimal.ZERO)
}

object DollarRates : LongIdTable("dollar_rates") {
    val rate = decimal("rate", 12, 2)
    val date = date("date").uniqueIndex()
}

object Orders : LongIdTable("orders") {
    val clientId = reference("client_id", Clients)
    val status = enumerationByName<OrderStatus>("status", 20).default(OrderStatus.CREATED)
    val createdAt = datetime("created_at")
    val totalArs = decimal("total_ars", 12, 2).default(java.math.BigDecimal.ZERO)
}

object OrderItems : LongIdTable("order_items") {
    val orderId = reference("order_id", Orders)
    val productId = reference("product_id", Products)
    val quantity = integer("quantity")
    val unitPriceArs = decimal("unit_price_ars", 12, 2)
}

object FinancialMovements : LongIdTable("financial_movements") {
    val type = enumerationByName<MovementType>("type", 20)
    val amount = decimal("amount", 12, 2)
    val date = datetime("date")
    val clientId = optReference("client_id", Clients)
    val supplierId = optReference("supplier_id", Suppliers)
    val orderId = optReference("order_id", Orders)
    val description = text("description").default("")
}
