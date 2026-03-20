package org.motopartes.api.route

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.motopartes.api.dto.*
import org.motopartes.repository.ProductRepository

fun Route.productRoutes(productRepo: ProductRepository) {
    route("/products") {
        get {
            call.respond(productRepo.findAll().map { it.toResponse() })
        }
        get("/search") {
            val q = call.queryParameters["q"] ?: throw BadRequestException("Parametro 'q' requerido")
            call.respond(productRepo.search(q).map { it.toResponse() })
        }
        get("/code/{code}") {
            val code = call.parameters["code"]!!
            val product = productRepo.findByCode(code) ?: throw NotFoundException("Producto con codigo '$code' no encontrado")
            call.respond(product.toResponse())
        }
        get("/{id}") {
            val id = call.parameters["id"]?.toLongOrNull() ?: throw BadRequestException("ID invalido")
            val product = productRepo.findById(id) ?: throw NotFoundException("Producto $id no encontrado")
            call.respond(product.toResponse())
        }
        post {
            val req = call.receive<CreateProductRequest>()
            val product = productRepo.insert(req.toDomain())
            call.respond(HttpStatusCode.Created, product.toResponse())
        }
        put("/{id}") {
            val id = call.parameters["id"]?.toLongOrNull() ?: throw BadRequestException("ID invalido")
            productRepo.findById(id) ?: throw NotFoundException("Producto $id no encontrado")
            val req = call.receive<UpdateProductRequest>()
            val updated = org.motopartes.model.Product(
                id = id, code = req.code, name = req.name, description = req.description,
                purchasePrice = req.purchasePrice, purchaseCurrency = req.purchaseCurrency,
                salePrice = req.salePrice, stock = req.stock
            )
            productRepo.update(updated)
            call.respond(updated.toResponse())
        }
        patch("/{id}/stock") {
            val id = call.parameters["id"]?.toLongOrNull() ?: throw BadRequestException("ID invalido")
            productRepo.findById(id) ?: throw NotFoundException("Producto $id no encontrado")
            val req = call.receive<StockAdjustRequest>()
            if (!productRepo.updateStock(id, req.delta)) throw ConflictException("No se pudo ajustar stock (stock insuficiente?)")
            call.respond(productRepo.findById(id)!!.toResponse())
        }
        delete("/{id}") {
            val id = call.parameters["id"]?.toLongOrNull() ?: throw BadRequestException("ID invalido")
            if (!productRepo.delete(id)) throw NotFoundException("Producto $id no encontrado")
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
