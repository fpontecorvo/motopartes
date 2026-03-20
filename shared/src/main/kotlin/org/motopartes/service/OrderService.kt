package org.motopartes.service

import kotlinx.datetime.LocalDateTime
import org.motopartes.model.*
import org.motopartes.repository.OrderRepository
import org.motopartes.repository.ProductRepository
import java.math.BigDecimal

class OrderService(
    private val orderRepo: OrderRepository,
    private val productRepo: ProductRepository,
    private val financeService: FinanceService
) {

    /** items: (productId, quantity, unitPriceArs) — price comes from UI, defaulting to salePrice */
    fun createOrder(clientId: Long, items: List<Triple<Long, Int, BigDecimal>>, now: LocalDateTime): Order {
        val orderItems = items.map { (productId, quantity, unitPrice) ->
            productRepo.findById(productId)
                ?: throw IllegalArgumentException("Producto $productId no encontrado")
            OrderItem(productId = productId, quantity = quantity, unitPriceArs = unitPrice)
        }

        val totalArs = orderItems.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.subtotalArs) }

        return orderRepo.insert(
            Order(clientId = clientId, status = OrderStatus.CREATED, createdAt = now, items = orderItems, totalArs = totalArs)
        )
    }

    /** Update items while order is in CREATED status */
    fun updateOrderItems(orderId: Long, items: List<Triple<Long, Int, BigDecimal>>): Boolean {
        val order = orderRepo.findById(orderId) ?: return false
        if (order.status != OrderStatus.CREATED) return false

        val newItems = items.map { (productId, quantity, unitPrice) ->
            productRepo.findById(productId) ?: return false
            OrderItem(orderId = orderId, productId = productId, quantity = quantity, unitPriceArs = unitPrice)
        }
        val totalArs = newItems.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.subtotalArs) }
        return orderRepo.replaceItems(orderId, newItems, totalArs)
    }

    /** Confirm order — locks items, ready for picking (no stock deduction) */
    fun confirmOrder(orderId: Long): Boolean {
        val order = orderRepo.findById(orderId) ?: return false
        if (order.status != OrderStatus.CREATED) return false
        return orderRepo.updateStatus(orderId, OrderStatus.CONFIRMED)
    }

    /** Assemble order — set actual picked quantities, deduct stock */
    fun assembleOrder(orderId: Long, assembledQuantities: Map<Long, Int>): Boolean {
        val order = orderRepo.findById(orderId) ?: return false
        if (order.status != OrderStatus.CONFIRMED) return false

        // Build new items with actual assembled quantities
        val assembledItems = order.items.mapNotNull { item ->
            val actualQty = assembledQuantities[item.productId] ?: item.quantity
            if (actualQty <= 0) return@mapNotNull null
            item.copy(quantity = actualQty)
        }
        if (assembledItems.isEmpty()) return false

        // Validate stock for all items
        for (item in assembledItems) {
            val product = productRepo.findById(item.productId) ?: return false
            if (product.stock < item.quantity) return false
        }

        // Deduct stock
        for (item in assembledItems) {
            if (!productRepo.updateStock(item.productId, -item.quantity)) return false
        }

        // Update items and total
        val totalArs = assembledItems.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.subtotalArs) }
        orderRepo.replaceItems(orderId, assembledItems, totalArs)
        return orderRepo.updateStatus(orderId, OrderStatus.ASSEMBLED)
    }

    /** Invoice order — record sale, update client balance */
    fun invoiceOrder(orderId: Long, now: LocalDateTime): Boolean {
        val order = orderRepo.findById(orderId) ?: return false
        if (order.status != OrderStatus.ASSEMBLED) return false
        if (!orderRepo.updateStatus(orderId, OrderStatus.INVOICED)) return false
        financeService.recordSale(orderId, order.clientId, order.totalArs, now)
        return true
    }

    /** Cancel order — revert stock if assembled, cannot cancel invoiced */
    fun cancelOrder(orderId: Long): Boolean {
        val order = orderRepo.findById(orderId) ?: return false
        if (order.status == OrderStatus.INVOICED || order.status == OrderStatus.CANCELLED) return false

        // If ASSEMBLED, revert stock
        if (order.status == OrderStatus.ASSEMBLED) {
            for (item in order.items) {
                productRepo.updateStock(item.productId, item.quantity)
            }
        }

        return orderRepo.updateStatus(orderId, OrderStatus.CANCELLED)
    }
}
