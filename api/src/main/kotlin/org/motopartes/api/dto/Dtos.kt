package org.motopartes.api.dto

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import org.motopartes.api.serialization.BigDecimalSerializer
import org.motopartes.model.*
import java.math.BigDecimal

// ── Products ──

@Serializable
data class CreateProductRequest(
    val code: String,
    val name: String,
    val description: String = "",
    @Serializable(with = BigDecimalSerializer::class) val purchasePrice: BigDecimal,
    val purchaseCurrency: Currency,
    val stock: Int = 0
)

@Serializable
data class UpdateProductRequest(
    val code: String,
    val name: String,
    val description: String = "",
    @Serializable(with = BigDecimalSerializer::class) val purchasePrice: BigDecimal,
    val purchaseCurrency: Currency,
    val stock: Int
)

@Serializable
data class StockAdjustRequest(val delta: Int)

@Serializable
data class ProductResponse(
    val id: Long,
    val code: String,
    val name: String,
    val description: String,
    @Serializable(with = BigDecimalSerializer::class) val purchasePrice: BigDecimal,
    val purchaseCurrency: Currency,
    val stock: Int
)

fun Product.toResponse() = ProductResponse(id, code, name, description, purchasePrice, purchaseCurrency, stock)

fun CreateProductRequest.toDomain() = Product(
    code = code, name = name, description = description,
    purchasePrice = purchasePrice, purchaseCurrency = purchaseCurrency,
    stock = stock
)

// ── Clients ──

@Serializable
data class CreateClientRequest(val name: String, val phone: String = "", val address: String = "")

@Serializable
data class UpdateClientRequest(val name: String, val phone: String = "", val address: String = "")

@Serializable
data class ClientResponse(
    val id: Long,
    val name: String,
    val phone: String,
    val address: String,
    @Serializable(with = BigDecimalSerializer::class) val balance: BigDecimal
)

fun Client.toResponse() = ClientResponse(id, name, phone, address, balance)

// ── Supplier ──

@Serializable
data class CreateSupplierRequest(val name: String, val phone: String = "")

@Serializable
data class UpdateSupplierRequest(val name: String, val phone: String = "")

@Serializable
data class SupplierResponse(
    val id: Long,
    val name: String,
    val phone: String,
    @Serializable(with = BigDecimalSerializer::class) val balance: BigDecimal
)

fun Supplier.toResponse() = SupplierResponse(id, name, phone, balance)

// ── Dollar Rates ──

@Serializable
data class SetDollarRateRequest(
    @Serializable(with = BigDecimalSerializer::class) val rate: BigDecimal,
    val date: LocalDate
)

@Serializable
data class DollarRateResponse(
    val id: Long,
    @Serializable(with = BigDecimalSerializer::class) val rate: BigDecimal,
    val date: LocalDate
)

fun DollarRate.toResponse() = DollarRateResponse(id, rate, date)

// ── Orders ──

@Serializable
data class OrderItemInput(
    val productId: Long,
    val quantity: Int,
    @Serializable(with = BigDecimalSerializer::class) val unitPriceArs: BigDecimal
)

@Serializable
data class CreateOrderRequest(val clientId: Long, val items: List<OrderItemInput>)

@Serializable
data class UpdateOrderItemsRequest(val items: List<OrderItemInput>)

@Serializable
data class AssembleOrderRequest(val assembledQuantities: Map<Long, Int> = emptyMap())

@Serializable
data class OrderItemResponse(
    val id: Long,
    val productId: Long,
    val quantity: Int,
    @Serializable(with = BigDecimalSerializer::class) val unitPriceArs: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class) val subtotalArs: BigDecimal
)

@Serializable
data class OrderDetailResponse(
    val id: Long,
    val clientId: Long,
    val status: OrderStatus,
    val createdAt: LocalDateTime,
    val items: List<OrderItemResponse>,
    @Serializable(with = BigDecimalSerializer::class) val totalArs: BigDecimal
)

@Serializable
data class OrderSummaryResponse(
    val id: Long,
    val clientId: Long,
    val status: OrderStatus,
    val createdAt: LocalDateTime,
    @Serializable(with = BigDecimalSerializer::class) val totalArs: BigDecimal
)

fun Order.toSummaryResponse() = OrderSummaryResponse(id, clientId, status, createdAt, totalArs)

fun Order.toDetailResponse() = OrderDetailResponse(
    id, clientId, status, createdAt,
    items.map { OrderItemResponse(it.id, it.productId, it.quantity, it.unitPriceArs, it.subtotalArs) },
    totalArs
)

// ── Finance ──

@Serializable
data class ClientPaymentRequest(
    val clientId: Long,
    @Serializable(with = BigDecimalSerializer::class) val amount: BigDecimal,
    val description: String = ""
)

@Serializable
data class SupplierPaymentRequest(
    @Serializable(with = BigDecimalSerializer::class) val amount: BigDecimal,
    val description: String = ""
)

@Serializable
data class MovementResponse(
    val id: Long,
    val type: MovementType,
    @Serializable(with = BigDecimalSerializer::class) val amount: BigDecimal,
    val date: LocalDateTime,
    val clientId: Long?,
    val supplierId: Long?,
    val orderId: Long?,
    val description: String
)

fun FinancialMovement.toResponse() = MovementResponse(id, type, amount, date, clientId, supplierId, orderId, description)

// ── Purchases ──

@Serializable
data class PurchaseItemInput(val productId: Long, val quantity: Int)

@Serializable
data class RegisterPurchaseRequest(
    val items: List<PurchaseItemInput>,
    @Serializable(with = BigDecimalSerializer::class) val totalCost: BigDecimal
)
