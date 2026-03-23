package org.motopartes.repository

import kotlinx.datetime.LocalDateTime
import org.motopartes.db.DatabaseFactory
import org.motopartes.model.*
import java.math.BigDecimal
import kotlin.test.*

class OrderRepositoryTest {

    private val orderRepo = OrderRepository()
    private val clientRepo = ClientRepository()
    private val productRepo = ProductRepository()

    @BeforeTest
    fun setup() {
        DatabaseFactory.initInMemory()
    }

    private fun createClient() = clientRepo.insert(Client(name = "Test Client"))

    private fun createProduct(code: String = "P1") = productRepo.insert(
        Product(code = code, name = "Producto $code", purchasePrice = BigDecimal("1000.00"), purchaseCurrency = Currency.ARS, stock = 10)
    )

    private fun sampleOrder(clientId: Long, items: List<OrderItem>): Order {
        val total = items.fold(BigDecimal.ZERO) { acc, it -> acc.add(it.subtotalArs) }
        return Order(
            clientId = clientId,
            status = OrderStatus.CREATED,
            createdAt = LocalDateTime(2026, 3, 20, 10, 0),
            items = items,
            totalArs = total
        )
    }

    @Test
    fun `insert and find by id with items`() {
        val client = createClient()
        val product = createProduct()
        val item = OrderItem(productId = product.id, quantity = 2, unitPriceArs = BigDecimal("1000.00"))
        val order = orderRepo.insert(sampleOrder(client.id, listOf(item)))

        assertTrue(order.id > 0)
        val found = orderRepo.findById(order.id)
        assertNotNull(found)
        assertEquals(client.id, found.clientId)
        assertEquals(OrderStatus.CREATED, found.status)
        assertEquals(1, found.items.size)
        assertEquals(2, found.items[0].quantity)
        assertEquals(0, BigDecimal("2000.00").compareTo(found.totalArs))
    }

    @Test
    fun `find all returns orders`() {
        val client = createClient()
        val product = createProduct()
        val item = OrderItem(productId = product.id, quantity = 1, unitPriceArs = BigDecimal("500.00"))
        orderRepo.insert(sampleOrder(client.id, listOf(item)))
        orderRepo.insert(sampleOrder(client.id, listOf(item)))

        assertEquals(2, orderRepo.findAll().size)
    }

    @Test
    fun `find by status`() {
        val client = createClient()
        val product = createProduct()
        val item = OrderItem(productId = product.id, quantity = 1, unitPriceArs = BigDecimal("500.00"))
        val order1 = orderRepo.insert(sampleOrder(client.id, listOf(item)))
        orderRepo.insert(sampleOrder(client.id, listOf(item)))

        orderRepo.updateStatus(order1.id, OrderStatus.CONFIRMED)

        assertEquals(1, orderRepo.findByStatus(OrderStatus.CONFIRMED).size)
        assertEquals(1, orderRepo.findByStatus(OrderStatus.CREATED).size)
    }

    @Test
    fun `find by client`() {
        val client1 = createClient()
        val client2 = clientRepo.insert(Client(name = "Client 2"))
        val product = createProduct()
        val item = OrderItem(productId = product.id, quantity = 1, unitPriceArs = BigDecimal("500.00"))

        orderRepo.insert(sampleOrder(client1.id, listOf(item)))
        orderRepo.insert(sampleOrder(client1.id, listOf(item)))
        orderRepo.insert(sampleOrder(client2.id, listOf(item)))

        assertEquals(2, orderRepo.findByClient(client1.id).size)
        assertEquals(1, orderRepo.findByClient(client2.id).size)
    }

    @Test
    fun `update status`() {
        val client = createClient()
        val product = createProduct()
        val item = OrderItem(productId = product.id, quantity = 1, unitPriceArs = BigDecimal("500.00"))
        val order = orderRepo.insert(sampleOrder(client.id, listOf(item)))

        assertTrue(orderRepo.updateStatus(order.id, OrderStatus.CONFIRMED))
        assertEquals(OrderStatus.CONFIRMED, orderRepo.findById(order.id)!!.status)
    }

    @Test
    fun `delete order removes items`() {
        val client = createClient()
        val product = createProduct()
        val item = OrderItem(productId = product.id, quantity = 1, unitPriceArs = BigDecimal("500.00"))
        val order = orderRepo.insert(sampleOrder(client.id, listOf(item)))

        assertTrue(orderRepo.delete(order.id))
        assertNull(orderRepo.findById(order.id))
    }

    @Test
    fun `client name lookup`() {
        val client = createClient()
        assertEquals("Test Client", orderRepo.clientName(client.id))
        assertNull(orderRepo.clientName(999))
    }
}
