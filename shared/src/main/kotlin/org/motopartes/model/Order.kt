package org.motopartes.model

import kotlinx.datetime.LocalDateTime
import java.math.BigDecimal

data class Order(
    val id: Long = 0,
    val clientId: Long,
    val status: OrderStatus = OrderStatus.CREATED,
    val createdAt: LocalDateTime,
    val items: List<OrderItem> = emptyList(),
    val totalArs: BigDecimal = BigDecimal.ZERO
)

data class OrderItem(
    val id: Long = 0,
    val orderId: Long = 0,
    val productId: Long,
    val quantity: Int,
    val unitPriceArs: BigDecimal,
) {
    val subtotalArs: BigDecimal get() = unitPriceArs.multiply(BigDecimal(quantity))
}
