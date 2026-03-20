package org.motopartes.config

import kotlin.test.Test
import kotlin.test.assertTrue

class AppPathsTest {

    @Test
    fun `data dir contains motopartes`() {
        val dir = AppPaths.dataDir()
        assertTrue(dir.toString().contains("motopartes"))
    }

    @Test
    fun `database path ends with data db`() {
        val path = AppPaths.databasePath()
        assertTrue(path.toString().endsWith("data.db"))
    }

    @Test
    fun `database path is inside data dir`() {
        val dataDir = AppPaths.dataDir()
        val dbPath = AppPaths.databasePath()
        assertTrue(dbPath.startsWith(dataDir))
    }
}
