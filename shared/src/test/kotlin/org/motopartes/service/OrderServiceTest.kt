package org.motopartes.service

import kotlinx.datetime.LocalDateTime
import org.motopartes.db.DatabaseFactory
import org.motopartes.model.*
import org.motopartes.repository.*
import java.math.BigDecimal
import kotlin.test.*

class OrderServiceTest {

    private val productRepo = ProductRepository()
    private val clientRepo = ClientRepository()
    private val orderRepo = OrderRepository()
    private val supplierRepo = SupplierRepository()
    private val movementRepo = FinancialMovementRepository()
    private val financeService = FinanceService(movementRepo, clientRepo, supplierRepo)
    private val orderService = OrderService(orderRepo, productRepo, financeService)

    private val now = LocalDateTime(2026, 3, 20, 10, 0)

    @BeforeTest
    fun setup() {
        DatabaseFactory.initInMemory()
    }

    private fun createClient() = clientRepo.insert(Client(name = "Test Client"))

    private fun createProduct(code: String, price: BigDecimal, stock: Int) =
        productRepo.insert(Product(code = code, name = "Prod $code", purchasePrice = price, purchaseCurrency = Currency.ARS, stock = stock))

    // --- createOrder ---

    @Test
    fun `create order calculates total in ARS`() {
        val client = createClient()
        val p1 = createProduct("A1", BigDecimal("1000.00"), stock = 10)
        val p2 = createProduct("A2", BigDecimal("500.00"), stock = 5)

        val order = orderService.createOrder(client.id, listOf(
            Triple(p1.id, 2, p1.purchasePrice),
            Triple(p2.id, 3, p2.purchasePrice)
        ), now)

        assertEquals(2, order.items.size)
        assertEquals(0, BigDecimal("3500.00").compareTo(order.totalArs))
    }

    @Test
    fun `create order with custom unit price`() {
        val client = createClient()
        val product = createProduct("A1", BigDecimal("1000.00"), stock = 10)

        // Apply 10% discount
        val discountedPrice = BigDecimal("900.00")
        val order = orderService.createOrder(client.id, listOf(Triple(product.id, 2, discountedPrice)), now)

        assertEquals(0, BigDecimal("900.00").compareTo(order.items[0].unitPriceArs))
        assertEquals(0, BigDecimal("1800.00").compareTo(order.totalArs))
    }

    // --- updateOrderItems ---

    @Test
    fun `update items while CREATED`() {
        val client = createClient()
        val p1 = createProduct("A1", BigDecimal("100.00"), stock = 10)
        val p2 = createProduct("A2", BigDecimal("200.00"), stock = 10)
        val order = orderService.createOrder(client.id, listOf(Triple(p1.id, 1, p1.purchasePrice)), now)

        assertTrue(orderService.updateOrderItems(order.id, listOf(
            Triple(p1.id, 3, p1.purchasePrice),
            Triple(p2.id, 2, p2.purchasePrice)
        )))

        val updated = orderRepo.findById(order.id)!!
        assertEquals(2, updated.items.size)
        assertEquals(0, BigDecimal("700.00").compareTo(updated.totalArs)) // 3*100 + 2*200
    }

    @Test
    fun `update items fails when CONFIRMED`() {
        val client = createClient()
        val product = createProduct("A1", BigDecimal("100.00"), stock = 10)
        val order = orderService.createOrder(client.id, listOf(Triple(product.id, 1, product.purchasePrice)), now)
        orderService.confirmOrder(order.id)

        assertFalse(orderService.updateOrderItems(order.id, listOf(Triple(product.id, 5, product.purchasePrice))))
    }

    // --- confirmOrder ---

    @Test
    fun `confirm order does not deduct stock`() {
        val client = createClient()
        val product = createProduct("P1", BigDecimal("100.00"), stock = 10)
        val order = orderService.createOrder(client.id, listOf(Triple(product.id, 3, product.purchasePrice)), now)

        assertTrue(orderService.confirmOrder(order.id))
        assertEquals(OrderStatus.CONFIRMED, orderRepo.findById(order.id)!!.status)
        assertEquals(10, productRepo.findById(product.id)!!.stock)
    }

    @Test
    fun `confirm fails when not CREATED`() {
        val client = createClient()
        val product = createProduct("P1", BigDecimal("100.00"), stock = 10)
        val order = orderService.createOrder(client.id, listOf(Triple(product.id, 1, product.purchasePrice)), now)
        orderService.confirmOrder(order.id)

        assertFalse(orderService.confirmOrder(order.id))
    }

    // --- assembleOrder ---

    @Test
    fun `assemble order deducts stock with original quantities`() {
        val client = createClient()
        val product = createProduct("P1", BigDecimal("100.00"), stock = 10)
        val order = orderService.createOrder(client.id, listOf(Triple(product.id, 3, product.purchasePrice)), now)
        orderService.confirmOrder(order.id)

        assertTrue(orderService.assembleOrder(order.id, emptyMap()))
        assertEquals(7, productRepo.findById(product.id)!!.stock)
        assertEquals(OrderStatus.ASSEMBLED, orderRepo.findById(order.id)!!.status)
    }

    @Test
    fun `assemble order with reduced quantities`() {
        val client = createClient()
        val product = createProduct("P1", BigDecimal("1000.00"), stock = 10)
        val order = orderService.createOrder(client.id, listOf(Triple(product.id, 5, product.purchasePrice)), now)
        orderService.confirmOrder(order.id)

        assertTrue(orderService.assembleOrder(order.id, mapOf(product.id to 3)))
        assertEquals(7, productRepo.findById(product.id)!!.stock)

        val assembled = orderRepo.findById(order.id)!!
        assertEquals(3, assembled.items[0].quantity)
        assertEquals(0, BigDecimal("3000.00").compareTo(assembled.totalArs))
    }

    @Test
    fun `assemble fails when insufficient stock`() {
        val client = createClient()
        val product = createProduct("P1", BigDecimal("100.00"), stock = 2)
        val order = orderService.createOrder(client.id, listOf(Triple(product.id, 5, product.purchasePrice)), now)
        orderService.confirmOrder(order.id)

        assertFalse(orderService.assembleOrder(order.id, mapOf(product.id to 5)))
        assertEquals(2, productRepo.findById(product.id)!!.stock)
    }

    @Test
    fun `assemble fails when not CONFIRMED`() {
        val client = createClient()
        val product = createProduct("P1", BigDecimal("100.00"), stock = 10)
        val order = orderService.createOrder(client.id, listOf(Triple(product.id, 1, product.purchasePrice)), now)

        assertFalse(orderService.assembleOrder(order.id, emptyMap()))
    }

    // --- invoiceOrder ---

    @Test
    fun `invoice order records sale and updates client balance`() {
        val client = createClient()
        val product = createProduct("P1", BigDecimal("1000.00"), stock = 10)
        val order = orderService.createOrder(client.id, listOf(Triple(product.id, 2, product.purchasePrice)), now)
        orderService.confirmOrder(order.id)
        orderService.assembleOrder(order.id, emptyMap())

        assertTrue(orderService.invoiceOrder(order.id, now))
        assertEquals(OrderStatus.INVOICED, orderRepo.findById(order.id)!!.status)
        assertEquals(0, BigDecimal("2000.00").compareTo(clientRepo.findById(client.id)!!.balance))

        val movements = financeService.getClientMovements(client.id)
        assertEquals(1, movements.size)
        assertEquals(MovementType.SALE, movements[0].type)
    }

    @Test
    fun `invoice fails when not ASSEMBLED`() {
        val client = createClient()
        val product = createProduct("P1", BigDecimal("100.00"), stock = 10)
        val order = orderService.createOrder(client.id, listOf(Triple(product.id, 1, product.purchasePrice)), now)
        orderService.confirmOrder(order.id)

        assertFalse(orderService.invoiceOrder(order.id, now))
    }

    // --- cancelOrder ---

    @Test
    fun `cancel CREATED order`() {
        val client = createClient()
        val product = createProduct("P1", BigDecimal("100.00"), stock = 10)
        val order = orderService.createOrder(client.id, listOf(Triple(product.id, 3, product.purchasePrice)), now)

        assertTrue(orderService.cancelOrder(order.id))
        assertEquals(OrderStatus.CANCELLED, orderRepo.findById(order.id)!!.status)
        assertEquals(10, productRepo.findById(product.id)!!.stock)
    }

    @Test
    fun `cancel CONFIRMED order`() {
        val client = createClient()
        val product = createProduct("P1", BigDecimal("100.00"), stock = 10)
        val order = orderService.createOrder(client.id, listOf(Triple(product.id, 3, product.purchasePrice)), now)
        orderService.confirmOrder(order.id)

        assertTrue(orderService.cancelOrder(order.id))
        assertEquals(OrderStatus.CANCELLED, orderRepo.findById(order.id)!!.status)
        assertEquals(10, productRepo.findById(product.id)!!.stock)
    }

    @Test
    fun `cancel ASSEMBLED order reverts stock`() {
        val client = createClient()
        val product = createProduct("P1", BigDecimal("100.00"), stock = 10)
        val order = orderService.createOrder(client.id, listOf(Triple(product.id, 3, product.purchasePrice)), now)
        orderService.confirmOrder(order.id)
        orderService.assembleOrder(order.id, emptyMap())
        assertEquals(7, productRepo.findById(product.id)!!.stock)

        assertTrue(orderService.cancelOrder(order.id))
        assertEquals(OrderStatus.CANCELLED, orderRepo.findById(order.id)!!.status)
        assertEquals(10, productRepo.findById(product.id)!!.stock)
    }

    @Test
    fun `cancel INVOICED order fails`() {
        val client = createClient()
        val product = createProduct("P1", BigDecimal("100.00"), stock = 10)
        val order = orderService.createOrder(client.id, listOf(Triple(product.id, 1, product.purchasePrice)), now)
        orderService.confirmOrder(order.id)
        orderService.assembleOrder(order.id, emptyMap())
        orderService.invoiceOrder(order.id, now)

        assertFalse(orderService.cancelOrder(order.id))
    }

    @Test
    fun `cancel already cancelled order fails`() {
        val client = createClient()
        val product = createProduct("P1", BigDecimal("100.00"), stock = 10)
        val order = orderService.createOrder(client.id, listOf(Triple(product.id, 1, product.purchasePrice)), now)
        orderService.cancelOrder(order.id)

        assertFalse(orderService.cancelOrder(order.id))
    }

    // --- full lifecycle ---

    @Test
    fun `full lifecycle CREATED to INVOICED`() {
        val client = createClient()
        val p1 = createProduct("P1", BigDecimal("500.00"), stock = 20)
        val p2 = createProduct("P2", BigDecimal("300.00"), stock = 10)

        val order = orderService.createOrder(client.id, listOf(
            Triple(p1.id, 5, p1.purchasePrice),
            Triple(p2.id, 3, p2.purchasePrice)
        ), now)
        assertEquals(OrderStatus.CREATED, orderRepo.findById(order.id)!!.status)

        assertTrue(orderService.updateOrderItems(order.id, listOf(
            Triple(p1.id, 4, p1.purchasePrice),
            Triple(p2.id, 2, p2.purchasePrice)
        )))

        assertTrue(orderService.confirmOrder(order.id))
        assertEquals(20, productRepo.findById(p1.id)!!.stock)

        assertTrue(orderService.assembleOrder(order.id, mapOf(p1.id to 3)))
        assertEquals(17, productRepo.findById(p1.id)!!.stock)
        assertEquals(8, productRepo.findById(p2.id)!!.stock)

        val assembled = orderRepo.findById(order.id)!!
        assertEquals(0, BigDecimal("2100.00").compareTo(assembled.totalArs))

        assertTrue(orderService.invoiceOrder(order.id, now))
        assertEquals(OrderStatus.INVOICED, orderRepo.findById(order.id)!!.status)
        assertEquals(0, BigDecimal("2100.00").compareTo(clientRepo.findById(client.id)!!.balance))
    }
}
