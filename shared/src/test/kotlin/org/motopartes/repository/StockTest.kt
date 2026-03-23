package org.motopartes.repository

import org.motopartes.db.DatabaseFactory
import org.motopartes.model.Currency
import org.motopartes.model.Product
import java.math.BigDecimal
import kotlin.test.*

class StockTest {

    private val repo = ProductRepository()

    @BeforeTest
    fun setup() {
        DatabaseFactory.initInMemory()
    }

    private fun sampleProduct(stock: Int = 10) = Product(
        code = "STK-001", name = "Filtro", purchasePrice = BigDecimal("500.00"),
        purchaseCurrency = Currency.ARS, stock = stock
    )

    @Test
    fun `add stock increases quantity`() {
        val product = repo.insert(sampleProduct(stock = 5))

        assertTrue(repo.updateStock(product.id, 10))
        assertEquals(15, repo.findById(product.id)!!.stock)
    }

    @Test
    fun `remove stock decreases quantity`() {
        val product = repo.insert(sampleProduct(stock = 10))

        assertTrue(repo.updateStock(product.id, -3))
        assertEquals(7, repo.findById(product.id)!!.stock)
    }

    @Test
    fun `remove stock fails when insufficient`() {
        val product = repo.insert(sampleProduct(stock = 2))

        assertFalse(repo.updateStock(product.id, -5))
        assertEquals(2, repo.findById(product.id)!!.stock)
    }

    @Test
    fun `remove exact stock leaves zero`() {
        val product = repo.insert(sampleProduct(stock = 3))

        assertTrue(repo.updateStock(product.id, -3))
        assertEquals(0, repo.findById(product.id)!!.stock)
    }

    @Test
    fun `update stock on nonexistent product returns false`() {
        assertFalse(repo.updateStock(999, 5))
    }
}
