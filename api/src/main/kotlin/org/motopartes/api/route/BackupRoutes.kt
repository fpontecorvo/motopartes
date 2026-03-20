package org.motopartes.api.route

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.motopartes.api.dto.BadRequestException
import org.motopartes.api.dto.SuccessResponse
import org.motopartes.service.BackupService
import java.nio.file.Files
import kotlin.io.path.name

fun Route.backupRoutes(backupService: BackupService) {
    route("/backup") {
        // GET /api/v1/backup — download DB file
        get {
            val tempFile = Files.createTempFile("motopartes-backup-", ".db")
            backupService.backup(tempFile).fold(
                onSuccess = {
                    call.response.header(HttpHeaders.ContentDisposition, "attachment; filename=\"${tempFile.name}\"")
                    call.respondFile(tempFile.toFile())
                    Files.deleteIfExists(tempFile)
                },
                onFailure = { throw BadRequestException(it.message ?: "Error al crear backup") }
            )
        }
        // POST /api/v1/backup/restore — upload DB file as raw body (application/octet-stream)
        post("/restore") {
            val bytes = call.receive<ByteArray>()
            if (bytes.isEmpty()) throw BadRequestException("No se recibio archivo de backup")
            val tempFile = Files.createTempFile("motopartes-restore-", ".db")
            Files.write(tempFile, bytes)
            backupService.restore(tempFile).fold(
                onSuccess = {
                    Files.deleteIfExists(tempFile)
                    call.respond(SuccessResponse())
                },
                onFailure = {
                    Files.deleteIfExists(tempFile)
                    throw BadRequestException(it.message ?: "Error al restaurar backup")
                }
            )
        }
    }
}
