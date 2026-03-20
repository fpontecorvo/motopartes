package org.motopartes.api.route

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.LocalDateTime
import org.motopartes.api.dto.*
import org.motopartes.service.FinanceService

fun Route.financeRoutes(financeService: FinanceService, nowProvider: () -> LocalDateTime) {
    route("/finance") {
        get("/movements") {
            val clientId = call.queryParameters["clientId"]?.toLongOrNull()
            val movements = if (clientId != null) {
                financeService.getClientMovements(clientId)
            } else {
                financeService.getAllMovements()
            }
            call.respond(movements.map { it.toResponse() })
        }
        get("/movements/supplier") {
            call.respond(financeService.getSupplierMovements().map { it.toResponse() })
        }
        post("/client-payment") {
            val req = call.receive<ClientPaymentRequest>()
            if (!financeService.recordClientPayment(req.clientId, req.amount, nowProvider(), req.description)) {
                throw BadRequestException("No se pudo registrar el cobro. Verifique el cliente y el monto.")
            }
            call.respond(HttpStatusCode.Created, SuccessResponse())
        }
        post("/supplier-payment") {
            val req = call.receive<SupplierPaymentRequest>()
            if (!financeService.recordSupplierPayment(req.amount, nowProvider(), req.description)) {
                throw BadRequestException("No se pudo registrar el pago. Verifique que el proveedor este configurado.")
            }
            call.respond(HttpStatusCode.Created, SuccessResponse())
        }
    }
}
