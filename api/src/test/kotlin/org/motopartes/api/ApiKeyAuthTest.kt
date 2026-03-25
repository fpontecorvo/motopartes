package org.motopartes.api

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import org.motopartes.db.DatabaseFactory
import org.motopartes.repository.*
import org.motopartes.service.*
import kotlin.test.*

class ApiKeyAuthTest {

    private lateinit var settingsRepo: SettingsRepository
    private lateinit var apiKey: String

    @BeforeTest
    fun setup() {
        DatabaseFactory.initInMemory()
        settingsRepo = SettingsRepository()
        apiKey = settingsRepo.getOrCreateApiKey()
    }

    private fun ApplicationTestBuilder.configureApp() {
        application {
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

            configurePlugins()
            configureRouting(
                productRepo, clientRepo, supplierRepo, dollarRateRepo,
                orderRepo, orderService, purchaseService, financeService,
                backupService, settingsRepo, ::now, allowLocalhostBypass = false
            )
        }
    }

    @Test
    fun `health endpoint is public`() = testApplication {
        configureApp()
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `request without API key returns 401`() = testApplication {
        configureApp()
        val response = client.get("/api/v1/products")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `request with invalid API key returns 401`() = testApplication {
        configureApp()
        val response = client.get("/api/v1/products") {
            header("X-API-Key", "invalid-key")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `request with valid API key returns 200`() = testApplication {
        configureApp()
        val response = client.get("/api/v1/products") {
            header("X-API-Key", apiKey)
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }
}
