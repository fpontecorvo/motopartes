package org.motopartes.mobile.api

import kotlinx.serialization.Serializable

@Serializable
data class ProductResponse(
    val id: Long,
    val code: String,
    val name: String,
    val description: String = "",
    val purchasePrice: String,
    val purchaseCurrency: String,
    val stock: Int
)

@Serializable
data class ClientResponse(
    val id: Long,
    val name: String,
    val phone: String = "",
    val address: String = "",
    val balance: String
)

@Serializable
data class OrderSummaryResponse(
    val id: Long,
    val clientId: Long,
    val clientName: String = "",
    val status: String,
    val totalArs: String,
    val createdAt: String
)

@Serializable
data class OrderDetailResponse(
    val id: Long,
    val clientId: Long,
    val status: String,
    val totalArs: String,
    val createdAt: String,
    val items: List<OrderItemResponse>
)

@Serializable
data class OrderItemResponse(
    val id: Long,
    val productId: Long,
    val productName: String = "",
    val quantity: Int,
    val unitPriceArs: String,
    val subtotalArs: String
)

@Serializable
data class DollarRateResponse(
    val id: Long,
    val rate: String,
    val date: String
)

@Serializable
data class CreateOrderRequest(
    val clientId: Long,
    val items: List<CreateOrderItemRequest>
)

@Serializable
data class CreateOrderItemRequest(
    val productId: Long,
    val quantity: Int,
    val unitPriceArs: String
)

@Serializable
data class CreateDollarRateRequest(
    val rate: String,
    val date: String
)

@Serializable
data class ClientPaymentRequest(
    val clientId: Long,
    val amount: String,
    val description: String = ""
)

@Serializable
data class AdjustStockRequest(
    val delta: Int
)

@Serializable
data class ErrorResponse(
    val error: String
)

@Serializable
data class HealthResponse(
    val status: String
)
