package org.motopartes.service

import kotlinx.datetime.LocalDateTime
import org.motopartes.repository.ProductRepository
import org.motopartes.repository.SupplierRepository
import java.math.BigDecimal

data class PurchaseItem(val productId: Long, val quantity: Int, val unitCost: BigDecimal) {
    val totalCost: BigDecimal get() = unitCost.multiply(BigDecimal(quantity))
}

data class PurchaseResult(
    val itemCount: Int,
    val totalCost: BigDecimal,
    val errors: List<String> = emptyList()
) {
    fun summary(): String {
        val parts = mutableListOf("$itemCount items, total: \$$totalCost")
        if (errors.isNotEmpty()) parts.add("${errors.size} errores")
        return parts.joinToString(", ")
    }
}

class PurchaseService(
    private val productRepo: ProductRepository,
    private val financeService: FinanceService,
    private val supplierRepo: SupplierRepository
) {

    /** Register purchase with individual unit costs. Total debt = sum of (qty * unitCost). */
    fun registerPurchase(items: List<PurchaseItem>, now: LocalDateTime, description: String = ""): PurchaseResult {
        val errors = mutableListOf<String>()
        var processed = 0
        var totalCost = BigDecimal.ZERO

        for (item in items) {
            if (item.quantity <= 0) { errors.add("${item.productId}: cantidad invalida"); continue }
            if (!productRepo.updateStock(item.productId, item.quantity)) {
                errors.add("${item.productId}: producto no encontrado"); continue
            }
            totalCost = totalCost.add(item.totalCost)
            processed++
        }

        if (processed > 0) {
            val supplier = supplierRepo.get()
            if (supplier != null) {
                financeService.recordPurchase(supplier.id, totalCost, now, description)
            } else {
                errors.add("Proveedor no configurado — stock actualizado pero deuda no registrada")
            }
        }

        return PurchaseResult(processed, totalCost, errors)
    }

    /** Legacy: register with flat total cost (for manual entry) */
    fun registerPurchase(items: List<Pair<Long, Int>>, totalCost: BigDecimal, now: LocalDateTime): Boolean {
        for ((productId, quantity) in items) {
            if (quantity <= 0) return false
            if (!productRepo.updateStock(productId, quantity)) return false
        }
        val supplier = supplierRepo.get() ?: return false
        financeService.recordPurchase(supplier.id, totalCost, now)
        return true
    }
}
