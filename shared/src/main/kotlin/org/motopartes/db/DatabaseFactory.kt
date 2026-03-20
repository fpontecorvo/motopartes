package org.motopartes.db

import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.motopartes.config.AppPaths

object DatabaseFactory {

    private val allTables = arrayOf(
        Products, Clients, Suppliers, DollarRates,
        Orders, OrderItems, FinancialMovements, AppSettings
    )

    fun init() {
        val dbPath = AppPaths.databasePath()
        dbPath.parent.toFile().mkdirs()
        Database.connect(url = "jdbc:sqlite:$dbPath", driver = "org.sqlite.JDBC")
        createSchema()
    }

    fun initInMemory() {
        val tempFile = java.io.File.createTempFile("motopartes-test", ".db")
        tempFile.deleteOnExit()
        Database.connect(url = "jdbc:sqlite:${tempFile.absolutePath}", driver = "org.sqlite.JDBC")
        createSchema()
    }

    private fun createSchema() {
        transaction {
            SchemaUtils.create(*allTables)
        }
        migrate()
    }

    private fun migrate() {
        transaction {
            // v1 → v2: rename DELIVERED to INVOICED
            exec("UPDATE orders SET status = 'INVOICED' WHERE status = 'DELIVERED'")

            // v2 → v3: add sale_price column to products (purchase price stays in 'price' column)
            try {
                exec("ALTER TABLE products ADD COLUMN sale_price DECIMAL(12,2) DEFAULT 0")
                // ARS products: sale_price = price * 1.30
                exec("UPDATE products SET sale_price = ROUND(price * 1.30, 2) WHERE sale_price = 0 AND currency = 'ARS'")
                // USD products: sale_price = price * latest_dollar_rate * 1.30
                exec("""
                    UPDATE products SET sale_price = ROUND(
                        price * COALESCE((SELECT rate FROM dollar_rates ORDER BY date DESC LIMIT 1), 1) * 1.30,
                        2
                    ) WHERE sale_price = 0 AND currency = 'USD'
                """.trimIndent())
            } catch (_: Exception) {
                // Column already exists
            }

            // v3 fix: recalculate sale_price for USD products using dollar rate
            exec("""
                UPDATE products SET sale_price = ROUND(
                    price * COALESCE((SELECT rate FROM dollar_rates ORDER BY date DESC LIMIT 1), 1) * 1.30,
                    2
                ) WHERE currency = 'USD' AND sale_price < price * 100
            """.trimIndent())
        }
    }
}
