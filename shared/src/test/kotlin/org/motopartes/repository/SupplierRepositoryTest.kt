package org.motopartes.repository

import org.motopartes.db.DatabaseFactory
import org.motopartes.model.Supplier
import java.math.BigDecimal
import kotlin.test.*

class SupplierRepositoryTest {

    private val repo = SupplierRepository()

    @BeforeTest
    fun setup() {
        DatabaseFactory.initInMemory()
    }

    @Test
    fun `get returns null when no supplier exists`() {
        assertNull(repo.get())
    }

    @Test
    fun `insert and get supplier`() {
        val inserted = repo.insert(Supplier(name = "Distribuidora Central", phone = "11-4444-0000"))

        assertTrue(inserted.id > 0)
        val found = repo.get()
        assertNotNull(found)
        assertEquals("Distribuidora Central", found.name)
        assertEquals("11-4444-0000", found.phone)
        assertEquals(0, BigDecimal.ZERO.compareTo(found.balance))
    }

    @Test
    fun `update supplier`() {
        val inserted = repo.insert(Supplier(name = "Proveedor A"))
        val updated = inserted.copy(name = "Proveedor B", balance = BigDecimal("50000.00"))

        assertTrue(repo.update(updated))
        val found = repo.get()!!
        assertEquals("Proveedor B", found.name)
        assertEquals(0, BigDecimal("50000.00").compareTo(found.balance))
    }
}
