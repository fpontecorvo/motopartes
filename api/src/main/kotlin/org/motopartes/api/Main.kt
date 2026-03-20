package org.motopartes.api

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.datetime.LocalDateTime
import org.motopartes.db.DatabaseFactory
import org.motopartes.repository.*
import org.motopartes.service.BackupService
import org.motopartes.service.FinanceService
import org.motopartes.service.OrderService
import org.motopartes.service.PurchaseService

fun now(): LocalDateTime {
    val j = java.time.LocalDateTime.now()
    return LocalDateTime(j.year, j.monthValue, j.dayOfMonth, j.hour, j.minute, j.second)
}

fun main() {
    DatabaseFactory.init()

    val productRepo = ProductRepository()
    val clientRepo = ClientRepository()
    val supplierRepo = SupplierRepository()
    val dollarRateRepo = DollarRateRepository()
    val orderRepo = OrderRepository()
    val movementRepo = FinancialMovementRepository()
    val financeService = FinanceService(movementRepo, clientRepo, supplierRepo)
    val orderService = OrderService(orderRepo, productRepo, financeService)
    val purchaseService = PurchaseService(productRepo, financeService, supplierRepo)
    val backupService = BackupService()

    embeddedServer(Netty, port = 8080) {
        configurePlugins()
        configureRouting(
            productRepo, clientRepo, supplierRepo, dollarRateRepo,
            orderRepo, orderService, purchaseService, financeService,
            backupService, ::now
        )
    }.start(wait = true)
}
