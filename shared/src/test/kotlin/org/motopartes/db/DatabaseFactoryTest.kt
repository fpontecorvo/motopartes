package org.motopartes.db

import org.jetbrains.exposed.sql.transactions.transaction
import kotlin.test.Test
import kotlin.test.assertTrue

class DatabaseFactoryTest {

    @Test
    fun `init in memory creates all tables`() {
        DatabaseFactory.initInMemory()

        transaction {
            val tables = exec("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'") { rs ->
                buildList {
                    while (rs.next()) {
                        add(rs.getString("name"))
                    }
                }
            } ?: emptyList()

            assertTrue(tables.contains("products"), "products table should exist")
            assertTrue(tables.contains("clients"), "clients table should exist")
            assertTrue(tables.contains("suppliers"), "suppliers table should exist")
            assertTrue(tables.contains("dollar_rates"), "dollar_rates table should exist")
            assertTrue(tables.contains("orders"), "orders table should exist")
            assertTrue(tables.contains("order_items"), "order_items table should exist")
            assertTrue(tables.contains("financial_movements"), "financial_movements table should exist")
        }
    }

    @Test
    fun `init in memory is idempotent`() {
        DatabaseFactory.initInMemory()
        DatabaseFactory.initInMemory()
        // no exception = success
    }
}
