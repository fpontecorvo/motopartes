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
        }
    }
}
