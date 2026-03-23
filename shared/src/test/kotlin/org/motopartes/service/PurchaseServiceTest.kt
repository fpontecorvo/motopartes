package org.motopartes.service

import kotlinx.datetime.LocalDateTime
import org.motopartes.db.DatabaseFactory
import org.motopartes.model.*
import org.motopartes.repository.*
import java.math.BigDecimal
import kotlin.test.*

class PurchaseServiceTest {

    private val productRepo = ProductRepository()
    private val clientRepo = ClientRepository()
    private val supplierRepo = SupplierRepository()
    private val movementRepo = FinancialMovementRepository()
    private val financeService = FinanceService(movementRepo, clientRepo, supplierRepo)
    private val purchaseService = PurchaseService(productRepo, financeService, supplierRepo)

    private val now = LocalDateTime(2026, 3, 20, 10, 0)

    @BeforeTest
    fun setup() {
        DatabaseFactory.initInMemory()
        supplierRepo.insert(Supplier(name = "Proveedor Test"))
    }

    private fun createProduct(code: String, stock: Int) = productRepo.insert(
        Product(code = code, name = "Prod $code", purchasePrice = BigDecimal("100.00"), purchaseCurrency = Currency.ARS, stock = stock)
    )

    @Test
    fun `register purchase increases stock and supplier debt`() {
        val p1 = createProduct("A1", stock = 5)
        val p2 = createProduct("A2", stock = 3)

        assertTrue(purchaseService.registerPurchase(listOf(p1.id to 10, p2.id to 5), BigDecimal("30000.00"), now))

        assertEquals(15, productRepo.findById(p1.id)!!.stock)
        assertEquals(8, productRepo.findById(p2.id)!!.stock)

        // Supplier balance should increase
        val supplier = supplierRepo.get()!!
        assertEquals(0, BigDecimal("30000.00").compareTo(supplier.balance))

        // Purchase movement should be recorded
        val movements = financeService.getSupplierMovements()
        assertEquals(1, movements.size)
        assertEquals(MovementType.PURCHASE, movements[0].type)
    }

    @Test
    fun `register purchase fails with zero quantity`() {
        val product = createProduct("A1", stock = 5)
        assertFalse(purchaseService.registerPurchase(listOf(product.id to 0), BigDecimal("100"), now))
        assertEquals(5, productRepo.findById(product.id)!!.stock)
    }

    @Test
    fun `register purchase fails with negative quantity`() {
        val product = createProduct("A1", stock = 5)
        assertFalse(purchaseService.registerPurchase(listOf(product.id to -3), BigDecimal("100"), now))
        assertEquals(5, productRepo.findById(product.id)!!.stock)
    }

    @Test
    fun `register purchase fails for nonexistent product`() {
        assertFalse(purchaseService.registerPurchase(listOf(999L to 10), BigDecimal("100"), now))
    }

    @Test
    fun `register purchase fails without supplier configured`() {
        // Fresh DB without supplier
        DatabaseFactory.initInMemory()
        val product = createProduct("A1", stock = 5)
        assertFalse(purchaseService.registerPurchase(listOf(product.id to 1), BigDecimal("100"), now))
    }
}
