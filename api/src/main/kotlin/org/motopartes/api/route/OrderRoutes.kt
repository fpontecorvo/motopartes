package org.motopartes.api.route

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.LocalDateTime
import org.motopartes.api.dto.*
import org.motopartes.model.OrderStatus
import org.motopartes.repository.OrderRepository
import org.motopartes.service.OrderService

fun Route.orderRoutes(orderService: OrderService, orderRepo: OrderRepository, nowProvider: () -> LocalDateTime) {
    route("/orders") {
        get {
            val statusParam = call.queryParameters["status"]
            val clientId = call.queryParameters["clientId"]?.toLongOrNull()
            val orders = when {
                statusParam != null -> {
                    val status = try { OrderStatus.valueOf(statusParam) } catch (_: Exception) {
                        throw BadRequestException("Estado invalido: $statusParam")
                    }
                    orderRepo.findByStatus(status)
                }
                clientId != null -> orderRepo.findByClient(clientId)
                else -> orderRepo.findAll()
            }
            call.respond(orders.map { it.toSummaryResponse() })
        }
        get("/{id}") {
            val id = call.parameters["id"]?.toLongOrNull() ?: throw BadRequestException("ID invalido")
            val order = orderRepo.findById(id) ?: throw NotFoundException("Pedido $id no encontrado")
            call.respond(order.toDetailResponse())
        }
        post {
            val req = call.receive<CreateOrderRequest>()
            if (req.items.isEmpty()) throw BadRequestException("Debe incluir al menos un item")
            val items = req.items.map { Triple(it.productId, it.quantity, it.unitPriceArs) }
            val order = orderService.createOrder(req.clientId, items, nowProvider())
            call.respond(HttpStatusCode.Created, order.toDetailResponse())
        }
        put("/{id}/items") {
            val id = call.parameters["id"]?.toLongOrNull() ?: throw BadRequestException("ID invalido")
            val req = call.receive<UpdateOrderItemsRequest>()
            if (req.items.isEmpty()) throw BadRequestException("Debe incluir al menos un item")
            val items = req.items.map { Triple(it.productId, it.quantity, it.unitPriceArs) }
            if (!orderService.updateOrderItems(id, items)) throw ConflictException("No se pudo actualizar. El pedido debe estar en estado CREATED.")
            val updated = orderRepo.findById(id)!!
            call.respond(updated.toDetailResponse())
        }
        post("/{id}/confirm") {
            val id = call.parameters["id"]?.toLongOrNull() ?: throw BadRequestException("ID invalido")
            if (!orderService.confirmOrder(id)) throw ConflictException("No se pudo confirmar. Verifique el estado del pedido.")
            call.respond(orderRepo.findById(id)!!.toDetailResponse())
        }
        post("/{id}/assemble") {
            val id = call.parameters["id"]?.toLongOrNull() ?: throw BadRequestException("ID invalido")
            val req = call.receive<AssembleOrderRequest>()
            if (!orderService.assembleOrder(id, req.assembledQuantities)) throw ConflictException("No se pudo armar. Verifique estado y stock.")
            call.respond(orderRepo.findById(id)!!.toDetailResponse())
        }
        post("/{id}/invoice") {
            val id = call.parameters["id"]?.toLongOrNull() ?: throw BadRequestException("ID invalido")
            if (!orderService.invoiceOrder(id, nowProvider())) throw ConflictException("No se pudo facturar. Verifique el estado del pedido.")
            call.respond(orderRepo.findById(id)!!.toDetailResponse())
        }
        post("/{id}/cancel") {
            val id = call.parameters["id"]?.toLongOrNull() ?: throw BadRequestException("ID invalido")
            if (!orderService.cancelOrder(id)) throw ConflictException("No se pudo cancelar. Verifique el estado del pedido.")
            call.respond(orderRepo.findById(id)!!.toDetailResponse())
        }
        delete("/{id}") {
            val id = call.parameters["id"]?.toLongOrNull() ?: throw BadRequestException("ID invalido")
            if (!orderRepo.delete(id)) throw NotFoundException("Pedido $id no encontrado")
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
