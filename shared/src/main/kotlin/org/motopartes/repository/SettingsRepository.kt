package org.motopartes.repository

import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.transaction
import org.motopartes.db.AppSettings
import java.math.BigDecimal

class SettingsRepository {

    private fun get(key: String): String? = transaction {
        AppSettings.selectAll().where { AppSettings.key eq key }
            .map { it[AppSettings.value] }
            .singleOrNull()
    }

    private fun set(key: String, value: String) = transaction {
        val exists = AppSettings.selectAll().where { AppSettings.key eq key }.count() > 0
        if (exists) {
            AppSettings.update({ AppSettings.key eq key }) { it[AppSettings.value] = value }
        } else {
            AppSettings.insert { it[AppSettings.key] = key; it[AppSettings.value] = value }
        }
    }

    fun getMarkupArs(): BigDecimal = get("markup_ars")?.toBigDecimalOrNull() ?: DEFAULT_MARKUP
    fun getMarkupUsd(): BigDecimal = get("markup_usd")?.toBigDecimalOrNull() ?: DEFAULT_MARKUP

    fun setMarkupArs(value: BigDecimal) = set("markup_ars", value.toPlainString())
    fun setMarkupUsd(value: BigDecimal) = set("markup_usd", value.toPlainString())

    companion object {
        val DEFAULT_MARKUP: BigDecimal = BigDecimal("1.30")
    }
}
