package org.motopartes.service

import kotlinx.datetime.LocalDateTime
import org.motopartes.repository.ProductRepository
import org.motopartes.repository.SupplierRepository
import java.math.BigDecimal

class PurchaseService(
    private val productRepo: ProductRepository,
    private val financeService: FinanceService,
    private val supplierRepo: SupplierRepository
) {

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
