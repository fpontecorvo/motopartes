package org.motopartes.config

import java.nio.file.Path
import kotlin.io.path.Path

object AppPaths {

    private const val APP_NAME = "motopartes"

    fun dataDir(): Path {
        val envDir = System.getenv("MOTOPARTES_DATA_DIR")
        if (envDir != null) return Path(envDir)

        val os = System.getProperty("os.name").lowercase()
        val baseDir = when {
            os.contains("win") -> Path(System.getenv("APPDATA") ?: System.getProperty("user.home"))
            os.contains("mac") || os.contains("darwin") ->
                Path(System.getProperty("user.home"), "Library", "Application Support")
            else ->
                Path(System.getProperty("user.home"), ".local", "share")
        }
        return baseDir.resolve(APP_NAME)
    }

    fun databasePath(): Path = dataDir().resolve("data.db")
}
