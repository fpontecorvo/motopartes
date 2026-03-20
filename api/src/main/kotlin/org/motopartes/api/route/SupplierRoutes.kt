package org.motopartes.api.route

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.motopartes.api.dto.*
import org.motopartes.model.Supplier
import org.motopartes.repository.SupplierRepository

fun Route.supplierRoutes(supplierRepo: SupplierRepository) {
    route("/supplier") {
        get {
            val supplier = supplierRepo.get() ?: throw NotFoundException("Proveedor no configurado")
            call.respond(supplier.toResponse())
        }
        post {
            if (supplierRepo.get() != null) throw ConflictException("Ya existe un proveedor configurado")
            val req = call.receive<CreateSupplierRequest>()
            val supplier = supplierRepo.insert(Supplier(name = req.name, phone = req.phone))
            call.respond(HttpStatusCode.Created, supplier.toResponse())
        }
        put {
            val existing = supplierRepo.get() ?: throw NotFoundException("Proveedor no configurado")
            val req = call.receive<UpdateSupplierRequest>()
            val updated = existing.copy(name = req.name, phone = req.phone)
            supplierRepo.update(updated)
            call.respond(updated.toResponse())
        }
    }
}
