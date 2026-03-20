package org.motopartes.service

import kotlinx.datetime.LocalDateTime
import org.motopartes.model.FinancialMovement
import org.motopartes.model.MovementType
import org.motopartes.repository.ClientRepository
import org.motopartes.repository.FinancialMovementRepository
import org.motopartes.repository.SupplierRepository
import java.math.BigDecimal

class FinanceService(
    private val movementRepo: FinancialMovementRepository,
    private val clientRepo: ClientRepository,
    private val supplierRepo: SupplierRepository
) {

    fun recordSale(orderId: Long, clientId: Long, amount: BigDecimal, now: LocalDateTime) {
        movementRepo.insert(
            FinancialMovement(
                type = MovementType.SALE,
                amount = amount,
                date = now,
                clientId = clientId,
                orderId = orderId,
                description = "Venta pedido #$orderId"
            )
        )
        val client = clientRepo.findById(clientId)!!
        clientRepo.update(client.copy(balance = client.balance.add(amount)))
    }

    fun recordPurchase(supplierId: Long, amount: BigDecimal, now: LocalDateTime, description: String = "") {
        movementRepo.insert(
            FinancialMovement(
                type = MovementType.PURCHASE,
                amount = amount,
                date = now,
                supplierId = supplierId,
                description = description.ifBlank { "Compra al proveedor" }
            )
        )
        val supplier = supplierRepo.get()!!
        supplierRepo.update(supplier.copy(balance = supplier.balance.add(amount)))
    }

    fun recordClientPayment(clientId: Long, amount: BigDecimal, now: LocalDateTime, description: String = ""): Boolean {
        if (amount <= BigDecimal.ZERO) return false
        val client = clientRepo.findById(clientId) ?: return false

        movementRepo.insert(
            FinancialMovement(
                type = MovementType.CLIENT_PAYMENT,
                amount = amount,
                date = now,
                clientId = clientId,
                description = description.ifBlank { "Cobro a ${client.name}" }
            )
        )
        clientRepo.update(client.copy(balance = client.balance.subtract(amount)))
        return true
    }

    fun recordSupplierPayment(amount: BigDecimal, now: LocalDateTime, description: String = ""): Boolean {
        if (amount <= BigDecimal.ZERO) return false
        val supplier = supplierRepo.get() ?: return false

        movementRepo.insert(
            FinancialMovement(
                type = MovementType.SUPPLIER_PAYMENT,
                amount = amount,
                date = now,
                supplierId = supplier.id,
                description = description.ifBlank { "Pago a ${supplier.name}" }
            )
        )
        supplierRepo.update(supplier.copy(balance = supplier.balance.subtract(amount)))
        return true
    }

    fun getClientMovements(clientId: Long): List<FinancialMovement> = movementRepo.findByClient(clientId)

    fun getSupplierMovements(): List<FinancialMovement> {
        val supplier = supplierRepo.get() ?: return emptyList()
        return movementRepo.findBySupplier(supplier.id)
    }

    fun getAllMovements(): List<FinancialMovement> = movementRepo.findAll()
}
