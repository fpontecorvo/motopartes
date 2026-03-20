package org.motopartes.repository

import kotlinx.datetime.LocalDateTime
import org.motopartes.db.DatabaseFactory
import org.motopartes.model.Client
import org.motopartes.model.FinancialMovement
import org.motopartes.model.MovementType
import org.motopartes.model.Supplier
import java.math.BigDecimal
import kotlin.test.*

class FinancialMovementRepositoryTest {

    private val repo = FinancialMovementRepository()
    private val clientRepo = ClientRepository()
    private val supplierRepo = SupplierRepository()

    @BeforeTest
    fun setup() {
        DatabaseFactory.initInMemory()
    }

    private val now = LocalDateTime(2026, 3, 20, 10, 0)

    @Test
    fun `insert and find all`() {
        val client = clientRepo.insert(Client(name = "Test"))
        repo.insert(FinancialMovement(type = MovementType.SALE, amount = BigDecimal("5000"), date = now, clientId = client.id))
        repo.insert(FinancialMovement(type = MovementType.CLIENT_PAYMENT, amount = BigDecimal("2000"), date = now, clientId = client.id))

        assertEquals(2, repo.findAll().size)
    }

    @Test
    fun `find by client`() {
        val c1 = clientRepo.insert(Client(name = "A"))
        val c2 = clientRepo.insert(Client(name = "B"))
        repo.insert(FinancialMovement(type = MovementType.SALE, amount = BigDecimal("1000"), date = now, clientId = c1.id))
        repo.insert(FinancialMovement(type = MovementType.SALE, amount = BigDecimal("2000"), date = now, clientId = c2.id))

        assertEquals(1, repo.findByClient(c1.id).size)
        assertEquals(1, repo.findByClient(c2.id).size)
    }

    @Test
    fun `find by supplier`() {
        val supplier = supplierRepo.insert(Supplier(name = "Proveedor"))
        repo.insert(FinancialMovement(type = MovementType.PURCHASE, amount = BigDecimal("10000"), date = now, supplierId = supplier.id))

        assertEquals(1, repo.findBySupplier(supplier.id).size)
        assertEquals(0, repo.findBySupplier(999).size)
    }

    @Test
    fun `inserted movement has correct fields`() {
        val client = clientRepo.insert(Client(name = "Test"))
        val inserted = repo.insert(
            FinancialMovement(type = MovementType.SALE, amount = BigDecimal("5000.50"), date = now, clientId = client.id, description = "Venta test")
        )

        assertTrue(inserted.id > 0)
        val found = repo.findAll().first()
        assertEquals(MovementType.SALE, found.type)
        assertEquals(0, BigDecimal("5000.50").compareTo(found.amount))
        assertEquals(client.id, found.clientId)
        assertEquals("Venta test", found.description)
    }
}
