package org.motopartes.repository

import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.motopartes.db.DollarRates
import org.motopartes.model.DollarRate

class DollarRateRepository {

    fun getLatest(): DollarRate? = transaction {
        DollarRates.selectAll()
            .orderBy(DollarRates.date, SortOrder.DESC)
            .limit(1)
            .map { it.toDollarRate() }
            .firstOrNull()
    }

    fun getByDate(date: LocalDate): DollarRate? = transaction {
        DollarRates.selectAll().where { DollarRates.date eq date }
            .map { it.toDollarRate() }
            .singleOrNull()
    }

    fun getAll(): List<DollarRate> = transaction {
        DollarRates.selectAll()
            .orderBy(DollarRates.date, SortOrder.DESC)
            .map { it.toDollarRate() }
    }

    fun insert(rate: DollarRate): DollarRate = transaction {
        val existing = DollarRates.selectAll().where { DollarRates.date eq rate.date }
            .map { it.toDollarRate() }
            .singleOrNull()
        if (existing != null) {
            DollarRates.update({ DollarRates.date eq rate.date }) {
                it[DollarRates.rate] = rate.rate
            }
            existing.copy(rate = rate.rate)
        } else {
            val id = DollarRates.insertAndGetId {
                it[DollarRates.rate] = rate.rate
                it[date] = rate.date
            }
            rate.copy(id = id.value)
        }
    }

    private fun ResultRow.toDollarRate() = DollarRate(
        id = this[DollarRates.id].value,
        rate = this[DollarRates.rate],
        date = this[DollarRates.date]
    )
}
