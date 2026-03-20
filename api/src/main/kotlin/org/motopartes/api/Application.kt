package org.motopartes.api

import io.ktor.server.application.*
import io.ktor.server.routing.*
import kotlinx.datetime.LocalDateTime
import org.motopartes.api.route.*
import org.motopartes.repository.*
import org.motopartes.service.BackupService
import org.motopartes.service.FinanceService
import org.motopartes.service.OrderService
import org.motopartes.service.PurchaseService

fun Application.configureRouting(
    productRepo: ProductRepository,
    clientRepo: ClientRepository,
    supplierRepo: SupplierRepository,
    dollarRateRepo: DollarRateRepository,
    orderRepo: OrderRepository,
    orderService: OrderService,
    purchaseService: PurchaseService,
    financeService: FinanceService,
    backupService: BackupService,
    nowProvider: () -> LocalDateTime
) {
    routing {
        route("/api/v1") {
            productRoutes(productRepo)
            clientRoutes(clientRepo)
            supplierRoutes(supplierRepo)
            dollarRateRoutes(dollarRateRepo)
            orderRoutes(orderService, orderRepo, nowProvider)
            financeRoutes(financeService, nowProvider)
            purchaseRoutes(purchaseService, nowProvider)
            backupRoutes(backupService)
        }
    }
}
