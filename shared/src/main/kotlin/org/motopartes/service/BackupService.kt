package org.motopartes.service

import org.motopartes.config.AppPaths
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.exists
import kotlin.io.path.extension

class BackupService {

    fun backup(destination: Path): Result<Path> = runCatching {
        val dbPath = AppPaths.databasePath()
        require(dbPath.exists()) { "Base de datos no encontrada en $dbPath" }
        Files.copy(dbPath, destination, StandardCopyOption.REPLACE_EXISTING)
        destination
    }

    fun restore(source: Path): Result<Unit> = runCatching {
        require(source.exists()) { "Archivo de backup no encontrado: $source" }
        require(source.extension == "db") { "El archivo debe tener extension .db" }
        // Validate it's a valid SQLite file (magic bytes: "SQLite format 3\000")
        val header = Files.newInputStream(source).use { it.readNBytes(16) }
        require(header.size >= 16 && String(header, 0, 15) == "SQLite format 3") {
            "El archivo no es una base de datos SQLite valida"
        }
        val dbPath = AppPaths.databasePath()
        Files.copy(source, dbPath, StandardCopyOption.REPLACE_EXISTING)
    }

    fun databasePath(): Path = AppPaths.databasePath()
}
