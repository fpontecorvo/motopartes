package org.motopartes.repository

import org.motopartes.db.DatabaseFactory
import org.motopartes.model.Client
import java.math.BigDecimal
import kotlin.test.*

class ClientRepositoryTest {

    private val repo = ClientRepository()

    @BeforeTest
    fun setup() {
        DatabaseFactory.initInMemory()
    }

    private fun sampleClient(
        name: String = "Juan Pérez",
        phone: String = "11-5555-0001",
        address: String = "Av. Corrientes 1234"
    ) = Client(name = name, phone = phone, address = address)

    @Test
    fun `insert and find by id`() {
        val inserted = repo.insert(sampleClient())

        assertTrue(inserted.id > 0)
        val found = repo.findById(inserted.id)
        assertNotNull(found)
        assertEquals("Juan Pérez", found.name)
        assertEquals("11-5555-0001", found.phone)
    }

    @Test
    fun `find by id returns null when not found`() {
        assertNull(repo.findById(999))
    }

    @Test
    fun `search by name`() {
        repo.insert(sampleClient(name = "Juan Pérez"))
        repo.insert(sampleClient(name = "María López"))
        repo.insert(sampleClient(name = "Juan García"))

        val results = repo.search("Juan")
        assertEquals(2, results.size)
    }

    @Test
    fun `search by phone`() {
        repo.insert(sampleClient(name = "A", phone = "11-5555-0001"))
        repo.insert(sampleClient(name = "B", phone = "11-5555-0002"))
        repo.insert(sampleClient(name = "C", phone = "11-9999-0001"))

        val results = repo.search("5555")
        assertEquals(2, results.size)
    }

    @Test
    fun `update client`() {
        val inserted = repo.insert(sampleClient())
        val updated = inserted.copy(phone = "11-9999-9999", balance = BigDecimal("15000.00"))

        assertTrue(repo.update(updated))
        val found = repo.findById(inserted.id)!!
        assertEquals("11-9999-9999", found.phone)
        assertEquals(0, BigDecimal("15000.00").compareTo(found.balance))
    }

    @Test
    fun `delete client`() {
        val inserted = repo.insert(sampleClient())
        assertTrue(repo.delete(inserted.id))
        assertNull(repo.findById(inserted.id))
    }

    @Test
    fun `new client has zero balance`() {
        val inserted = repo.insert(sampleClient())
        val found = repo.findById(inserted.id)!!
        assertEquals(0, BigDecimal.ZERO.compareTo(found.balance))
    }

    @Test
    fun `findAll returns all clients`() {
        repo.insert(sampleClient(name = "A"))
        repo.insert(sampleClient(name = "B"))

        assertEquals(2, repo.findAll().size)
    }
}
