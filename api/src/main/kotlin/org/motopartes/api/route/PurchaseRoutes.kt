package org.motopartes.api.route

import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.LocalDateTime
import org.motopartes.api.dto.*
import org.motopartes.service.PurchaseService

fun Route.purchaseRoutes(purchaseService: PurchaseService, nowProvider: () -> LocalDateTime) {
    route("/purchases") {
        post {
            val req = call.receive<RegisterPurchaseRequest>()
            if (req.items.isEmpty()) throw BadRequestException("Debe incluir al menos un item")
            val items = req.items.map { it.productId to it.quantity }
            if (!purchaseService.registerPurchase(items, req.totalCost, nowProvider())) {
                throw BadRequestException("No se pudo registrar la compra. Verifique productos y proveedor.")
            }
            call.respond(SuccessResponse())
        }
    }
}
