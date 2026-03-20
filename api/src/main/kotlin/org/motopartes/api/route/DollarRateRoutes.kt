package org.motopartes.api.route

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.datetime.LocalDate
import org.motopartes.api.dto.*
import org.motopartes.model.DollarRate
import org.motopartes.repository.DollarRateRepository

fun Route.dollarRateRoutes(dollarRateRepo: DollarRateRepository) {
    route("/dollar-rates") {
        get {
            call.respond(dollarRateRepo.getAll().map { it.toResponse() })
        }
        get("/latest") {
            val rate = dollarRateRepo.getLatest() ?: throw NotFoundException("No hay cotizacion configurada")
            call.respond(rate.toResponse())
        }
        get("/{date}") {
            val dateStr = call.parameters["date"]!!
            val date = try { LocalDate.parse(dateStr) } catch (_: Exception) { throw BadRequestException("Fecha invalida: $dateStr (usar YYYY-MM-DD)") }
            val rate = dollarRateRepo.getByDate(date) ?: throw NotFoundException("No hay cotizacion para $dateStr")
            call.respond(rate.toResponse())
        }
        post {
            val req = call.receive<SetDollarRateRequest>()
            val rate = dollarRateRepo.insert(DollarRate(rate = req.rate, date = req.date))
            call.respond(HttpStatusCode.Created, rate.toResponse())
        }
    }
}
