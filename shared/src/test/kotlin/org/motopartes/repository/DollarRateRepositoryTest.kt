package org.motopartes.repository

import kotlinx.datetime.LocalDate
import org.motopartes.db.DatabaseFactory
import org.motopartes.model.DollarRate
import java.math.BigDecimal
import kotlin.test.*

class DollarRateRepositoryTest {

    private val repo = DollarRateRepository()

    @BeforeTest
    fun setup() {
        DatabaseFactory.initInMemory()
    }

    @Test
    fun `get latest returns null when empty`() {
        assertNull(repo.getLatest())
    }

    @Test
    fun `insert and get latest`() {
        repo.insert(DollarRate(rate = BigDecimal("1100.00"), date = LocalDate(2026, 3, 18)))
        repo.insert(DollarRate(rate = BigDecimal("1200.00"), date = LocalDate(2026, 3, 19)))
        repo.insert(DollarRate(rate = BigDecimal("1150.00"), date = LocalDate(2026, 3, 20)))

        val latest = repo.getLatest()
        assertNotNull(latest)
        assertEquals(0, BigDecimal("1150.00").compareTo(latest.rate))
        assertEquals(LocalDate(2026, 3, 20), latest.date)
    }

    @Test
    fun `get by date`() {
        repo.insert(DollarRate(rate = BigDecimal("1100.00"), date = LocalDate(2026, 3, 18)))
        repo.insert(DollarRate(rate = BigDecimal("1200.00"), date = LocalDate(2026, 3, 19)))

        val found = repo.getByDate(LocalDate(2026, 3, 18))
        assertNotNull(found)
        assertEquals(0, BigDecimal("1100.00").compareTo(found.rate))
    }

    @Test
    fun `get by date returns null for missing date`() {
        assertNull(repo.getByDate(LocalDate(2026, 1, 1)))
    }

    @Test
    fun `get all returns history ordered by date desc`() {
        repo.insert(DollarRate(rate = BigDecimal("1100.00"), date = LocalDate(2026, 3, 18)))
        repo.insert(DollarRate(rate = BigDecimal("1200.00"), date = LocalDate(2026, 3, 20)))
        repo.insert(DollarRate(rate = BigDecimal("1150.00"), date = LocalDate(2026, 3, 19)))

        val all = repo.getAll()
        assertEquals(3, all.size)
        assertEquals(LocalDate(2026, 3, 20), all[0].date)
        assertEquals(LocalDate(2026, 3, 19), all[1].date)
        assertEquals(LocalDate(2026, 3, 18), all[2].date)
    }
}
