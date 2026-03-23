package org.motopartes.repository

import org.motopartes.db.DatabaseFactory
import org.motopartes.model.Currency
import org.motopartes.model.Product
import java.math.BigDecimal
import kotlin.test.*

class ProductRepositoryTest {

    private val repo = ProductRepository()

    @BeforeTest
    fun setup() {
        DatabaseFactory.initInMemory()
    }

    private fun sampleProduct(
        code: String = "MOT-001",
        name: String = "Pastilla de freno",
        purchasePrice: BigDecimal = BigDecimal("1500.00"),
        purchaseCurrency: Currency = Currency.ARS
    ) = Product(code = code, name = name, purchasePrice = purchasePrice, purchaseCurrency = purchaseCurrency)

    @Test
    fun `insert and find by id`() {
        val inserted = repo.insert(sampleProduct())

        assertTrue(inserted.id > 0)
        val found = repo.findById(inserted.id)
        assertNotNull(found)
        assertEquals("MOT-001", found.code)
        assertEquals("Pastilla de freno", found.name)
        assertEquals(0, BigDecimal("1500.00").compareTo(found.purchasePrice))
    }

    @Test
    fun `find by code`() {
        repo.insert(sampleProduct(code = "FIL-100"))

        val found = repo.findByCode("FIL-100")
        assertNotNull(found)
        assertEquals("FIL-100", found.code)
    }

    @Test
    fun `find by code returns null when not found`() {
        assertNull(repo.findByCode("NONEXISTENT"))
    }

    @Test
    fun `search by name`() {
        repo.insert(sampleProduct(code = "A1", name = "Kit de arrastre"))
        repo.insert(sampleProduct(code = "A2", name = "Filtro de aceite"))
        repo.insert(sampleProduct(code = "A3", name = "Kit de embrague"))

        val results = repo.search("Kit")
        assertEquals(2, results.size)
    }

    @Test
    fun `search by code`() {
        repo.insert(sampleProduct(code = "FIL-001", name = "Filtro aceite"))
        repo.insert(sampleProduct(code = "FIL-002", name = "Filtro aire"))
        repo.insert(sampleProduct(code = "MOT-001", name = "Motor"))

        val results = repo.search("FIL")
        assertEquals(2, results.size)
    }

    @Test
    fun `update product`() {
        val inserted = repo.insert(sampleProduct())
        val updated = inserted.copy(name = "Pastilla de freno trasera", purchasePrice = BigDecimal("2000.00"))

        assertTrue(repo.update(updated))
        val found = repo.findById(inserted.id)!!
        assertEquals("Pastilla de freno trasera", found.name)
        assertEquals(0, BigDecimal("2000.00").compareTo(found.purchasePrice))
    }

    @Test
    fun `delete product`() {
        val inserted = repo.insert(sampleProduct())
        assertTrue(repo.delete(inserted.id))
        assertNull(repo.findById(inserted.id))
    }

    @Test
    fun `delete nonexistent returns false`() {
        assertFalse(repo.delete(999))
    }

    @Test
    fun `findAll returns all products`() {
        repo.insert(sampleProduct(code = "A1"))
        repo.insert(sampleProduct(code = "A2"))
        repo.insert(sampleProduct(code = "A3"))

        assertEquals(3, repo.findAll().size)
    }

    @Test
    fun `purchase price in ars with usd currency`() {
        val product = sampleProduct(purchasePrice = BigDecimal("10.00"), purchaseCurrency = Currency.USD)
        val dollarRate = BigDecimal("1200.00")

        val priceArs = product.purchasePriceInArs(dollarRate)
        assertEquals(0, BigDecimal("12000.00").compareTo(priceArs))
    }

    @Test
    fun `purchase price in ars with ars currency ignores dollar rate`() {
        val product = sampleProduct(purchasePrice = BigDecimal("5000.00"), purchaseCurrency = Currency.ARS)
        val dollarRate = BigDecimal("1200.00")

        val priceArs = product.purchasePriceInArs(dollarRate)
        assertEquals(0, BigDecimal("5000.00").compareTo(priceArs))
    }

    @Test
    fun `suggested sale price with ARS markup`() {
        val product = sampleProduct(purchasePrice = BigDecimal("1000.00"), purchaseCurrency = Currency.ARS)
        val price = product.suggestedSalePrice(BigDecimal("1200.00"), BigDecimal("1.30"), BigDecimal("1.40"))
        assertEquals(0, BigDecimal("1300.00").compareTo(price))
    }

    @Test
    fun `suggested sale price with USD markup`() {
        val product = sampleProduct(purchasePrice = BigDecimal("10.00"), purchaseCurrency = Currency.USD)
        val price = product.suggestedSalePrice(BigDecimal("1200.00"), BigDecimal("1.30"), BigDecimal("1.40"))
        assertEquals(0, BigDecimal("16800.00").compareTo(price)) // 10 * 1200 * 1.40
    }
}
