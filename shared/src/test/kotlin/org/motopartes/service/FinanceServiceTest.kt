package org.motopartes.service

import kotlinx.datetime.LocalDateTime
import org.motopartes.db.DatabaseFactory
import org.motopartes.model.*
import org.motopartes.repository.*
import java.math.BigDecimal
import kotlin.test.*

class FinanceServiceTest {

    private val clientRepo = ClientRepository()
    private val supplierRepo = SupplierRepository()
    private val movementRepo = FinancialMovementRepository()
    private val financeService = FinanceService(movementRepo, clientRepo, supplierRepo)

    private val now = LocalDateTime(2026, 3, 20, 10, 0)

    @BeforeTest
    fun setup() {
        DatabaseFactory.initInMemory()
    }

    @Test
    fun `record sale increases client balance`() {
        val client = clientRepo.insert(Client(name = "Test"))
        financeService.recordSale(orderId = 1, clientId = client.id, amount = BigDecimal("5000.00"), now = now)

        val updated = clientRepo.findById(client.id)!!
        assertEquals(0, BigDecimal("5000.00").compareTo(updated.balance))
    }

    @Test
    fun `record sale creates movement`() {
        val client = clientRepo.insert(Client(name = "Test"))
        financeService.recordSale(orderId = 1, clientId = client.id, amount = BigDecimal("3000.00"), now = now)

        val movements = financeService.getClientMovements(client.id)
        assertEquals(1, movements.size)
        assertEquals(MovementType.SALE, movements[0].type)
        assertEquals(0, BigDecimal("3000.00").compareTo(movements[0].amount))
    }

    @Test
    fun `record client payment decreases balance`() {
        val client = clientRepo.insert(Client(name = "Test", balance = BigDecimal("10000.00")))
        clientRepo.update(client)

        assertTrue(financeService.recordClientPayment(client.id, BigDecimal("4000.00"), now))

        val updated = clientRepo.findById(client.id)!!
        assertEquals(0, BigDecimal("6000.00").compareTo(updated.balance))
    }

    @Test
    fun `record client payment fails with zero amount`() {
        val client = clientRepo.insert(Client(name = "Test"))
        assertFalse(financeService.recordClientPayment(client.id, BigDecimal.ZERO, now))
    }

    @Test
    fun `record client payment fails with negative amount`() {
        val client = clientRepo.insert(Client(name = "Test"))
        assertFalse(financeService.recordClientPayment(client.id, BigDecimal("-100"), now))
    }

    @Test
    fun `record client payment fails for nonexistent client`() {
        assertFalse(financeService.recordClientPayment(999, BigDecimal("100"), now))
    }

    @Test
    fun `record purchase increases supplier balance`() {
        val supplier = supplierRepo.insert(Supplier(name = "Proveedor"))
        financeService.recordPurchase(supplier.id, BigDecimal("50000.00"), now)

        val updated = supplierRepo.get()!!
        assertEquals(0, BigDecimal("50000.00").compareTo(updated.balance))
    }

    @Test
    fun `record supplier payment decreases balance`() {
        val supplier = supplierRepo.insert(Supplier(name = "Proveedor", balance = BigDecimal("50000.00")))
        supplierRepo.update(supplier)

        assertTrue(financeService.recordSupplierPayment(BigDecimal("20000.00"), now))

        val updated = supplierRepo.get()!!
        assertEquals(0, BigDecimal("30000.00").compareTo(updated.balance))
    }

    @Test
    fun `record supplier payment fails with zero amount`() {
        supplierRepo.insert(Supplier(name = "Proveedor"))
        assertFalse(financeService.recordSupplierPayment(BigDecimal.ZERO, now))
    }

    @Test
    fun `record supplier payment fails without supplier`() {
        assertFalse(financeService.recordSupplierPayment(BigDecimal("100"), now))
    }

    @Test
    fun `get all movements returns full history`() {
        val client = clientRepo.insert(Client(name = "Test"))
        val supplier = supplierRepo.insert(Supplier(name = "Proveedor"))

        financeService.recordSale(1, client.id, BigDecimal("1000"), now)
        financeService.recordClientPayment(client.id, BigDecimal("500"), now)
        financeService.recordPurchase(supplier.id, BigDecimal("2000"), now)
        financeService.recordSupplierPayment(BigDecimal("1000"), now)

        assertEquals(4, financeService.getAllMovements().size)
    }
}
