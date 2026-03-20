package org.motopartes.api.route

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.motopartes.api.dto.*
import org.motopartes.model.Client
import org.motopartes.repository.ClientRepository

fun Route.clientRoutes(clientRepo: ClientRepository) {
    route("/clients") {
        get {
            call.respond(clientRepo.findAll().map { it.toResponse() })
        }
        get("/search") {
            val q = call.queryParameters["q"] ?: throw BadRequestException("Parametro 'q' requerido")
            call.respond(clientRepo.search(q).map { it.toResponse() })
        }
        get("/{id}") {
            val id = call.parameters["id"]?.toLongOrNull() ?: throw BadRequestException("ID invalido")
            val client = clientRepo.findById(id) ?: throw NotFoundException("Cliente $id no encontrado")
            call.respond(client.toResponse())
        }
        post {
            val req = call.receive<CreateClientRequest>()
            val client = clientRepo.insert(Client(name = req.name, phone = req.phone, address = req.address))
            call.respond(HttpStatusCode.Created, client.toResponse())
        }
        put("/{id}") {
            val id = call.parameters["id"]?.toLongOrNull() ?: throw BadRequestException("ID invalido")
            val existing = clientRepo.findById(id) ?: throw NotFoundException("Cliente $id no encontrado")
            val req = call.receive<UpdateClientRequest>()
            val updated = existing.copy(name = req.name, phone = req.phone, address = req.address)
            clientRepo.update(updated)
            call.respond(updated.toResponse())
        }
        delete("/{id}") {
            val id = call.parameters["id"]?.toLongOrNull() ?: throw BadRequestException("ID invalido")
            if (!clientRepo.delete(id)) throw NotFoundException("Cliente $id no encontrado")
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
