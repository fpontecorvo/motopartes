package org.motopartes.mobile.api

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json

class ApiClient(
    private val baseUrl: String,
    private val apiKey: String
) {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) { json(json) }
    }

    private fun HttpRequestBuilder.auth() {
        header("X-API-Key", apiKey)
    }

    // Health
    suspend fun health(): Result<HealthResponse> = runCatching {
        client.get("$baseUrl/health").body()
    }

    // Products
    suspend fun searchProducts(query: String): Result<List<ProductResponse>> = runCatching {
        client.get("$baseUrl/api/v1/products/search") {
            auth()
            parameter("q", query)
        }.body()
    }

    suspend fun getProducts(): Result<List<ProductResponse>> = runCatching {
        client.get("$baseUrl/api/v1/products") { auth() }.body()
    }

    suspend fun getProduct(id: Long): Result<ProductResponse> = runCatching {
        client.get("$baseUrl/api/v1/products/$id") { auth() }.body()
    }

    // Clients
    suspend fun getClients(): Result<List<ClientResponse>> = runCatching {
        client.get("$baseUrl/api/v1/clients") { auth() }.body()
    }

    suspend fun searchClients(query: String): Result<List<ClientResponse>> = runCatching {
        client.get("$baseUrl/api/v1/clients/search") {
            auth()
            parameter("q", query)
        }.body()
    }

    suspend fun recordClientPayment(request: ClientPaymentRequest): Result<Unit> = runCatching {
        client.post("$baseUrl/api/v1/finance/client-payment") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(request)
        }
    }

    // Orders
    suspend fun getOrders(status: String? = null): Result<List<OrderSummaryResponse>> = runCatching {
        client.get("$baseUrl/api/v1/orders") {
            auth()
            status?.let { parameter("status", it) }
        }.body()
    }

    suspend fun getOrder(id: Long): Result<OrderDetailResponse> = runCatching {
        client.get("$baseUrl/api/v1/orders/$id") { auth() }.body()
    }

    suspend fun createOrder(request: CreateOrderRequest): Result<OrderDetailResponse> = runCatching {
        client.post("$baseUrl/api/v1/orders") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    suspend fun confirmOrder(id: Long): Result<OrderDetailResponse> = runCatching {
        client.post("$baseUrl/api/v1/orders/$id/confirm") { auth() }.body()
    }

    suspend fun assembleOrder(id: Long): Result<OrderDetailResponse> = runCatching {
        client.post("$baseUrl/api/v1/orders/$id/assemble") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(mapOf("assembledQuantities" to emptyMap<String, Int>()))
        }.body()
    }

    suspend fun invoiceOrder(id: Long): Result<OrderDetailResponse> = runCatching {
        client.post("$baseUrl/api/v1/orders/$id/invoice") { auth() }.body()
    }

    // Dollar rate
    suspend fun getLatestDollarRate(): Result<DollarRateResponse> = runCatching {
        client.get("$baseUrl/api/v1/dollar-rates/latest") { auth() }.body()
    }

    suspend fun setDollarRate(request: CreateDollarRateRequest): Result<DollarRateResponse> = runCatching {
        client.post("$baseUrl/api/v1/dollar-rates") {
            auth()
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    fun close() {
        client.close()
    }
}
