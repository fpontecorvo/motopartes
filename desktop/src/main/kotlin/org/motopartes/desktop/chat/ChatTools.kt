package org.motopartes.desktop.chat

import ai.koog.agents.core.tools.annotations.LLMDescription
import ai.koog.agents.core.tools.annotations.Tool
import ai.koog.agents.core.tools.reflect.ToolSet
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.motopartes.model.*
import org.motopartes.repository.*
import org.motopartes.service.FinanceService
import org.motopartes.service.OrderService
import org.motopartes.service.PurchaseService
import java.math.BigDecimal

private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

private fun now(): LocalDateTime {
    val j = java.time.LocalDateTime.now()
    return LocalDateTime(j.year, j.monthValue, j.dayOfMonth, j.hour, j.minute, j.second)
}

private fun today(): LocalDate {
    val j = java.time.LocalDate.now()
    return LocalDate(j.year, j.monthValue, j.dayOfMonth)
}

class MotopartesTools(
    private val productRepo: ProductRepository,
    private val clientRepo: ClientRepository,
    private val supplierRepo: SupplierRepository,
    private val dollarRateRepo: DollarRateRepository,
    private val orderRepo: OrderRepository,
    private val orderService: OrderService,
    private val financeService: FinanceService,
    private val purchaseService: PurchaseService
) : ToolSet {

    // ── Productos ──

    @Tool
    @LLMDescription("Buscar productos por codigo o nombre. Retorna lista de productos que coinciden.")
    fun searchProducts(@LLMDescription("Texto a buscar en codigo o nombre") query: String): String {
        val results = productRepo.search(query)
        return if (results.isEmpty()) "No se encontraron productos para '$query'"
        else results.joinToString("\n") { "- [${it.id}] ${it.code} | ${it.name} | compra: ${it.purchasePrice} ${it.purchaseCurrency} | venta: \$${it.salePrice} | stock: ${it.stock}" }
    }

    @Tool
    @LLMDescription("Listar todos los productos disponibles.")
    fun listProducts(): String {
        val products = productRepo.findAll()
        return if (products.isEmpty()) "No hay productos cargados"
        else "${products.size} productos:\n" + products.joinToString("\n") { "- [${it.id}] ${it.code} | ${it.name} | venta: \$${it.salePrice} | stock: ${it.stock}" }
    }

    @Tool
    @LLMDescription("Obtener detalle completo de un producto por su ID.")
    fun getProduct(@LLMDescription("ID del producto") productId: Long): String {
        val p = productRepo.findById(productId) ?: return "Producto $productId no encontrado"
        return "Producto [${p.id}]: ${p.code} - ${p.name}\nDescripcion: ${p.description}\nPrecio compra: ${p.purchasePrice} ${p.purchaseCurrency}\nPrecio venta: \$${p.salePrice}\nStock: ${p.stock}"
    }

    @Tool
    @LLMDescription("Crear un nuevo producto.")
    fun createProduct(
        @LLMDescription("Codigo del producto") code: String,
        @LLMDescription("Nombre del producto") name: String,
        @LLMDescription("Precio de compra como string numerico, ej '1500.00'") purchasePrice: String,
        @LLMDescription("Moneda de compra: USD o ARS") purchaseCurrency: String,
        @LLMDescription("Precio de venta en ARS como string numerico") salePrice: String
    ): String {
        val product = productRepo.insert(Product(
            code = code, name = name,
            purchasePrice = BigDecimal(purchasePrice),
            purchaseCurrency = Currency.valueOf(purchaseCurrency),
            salePrice = BigDecimal(salePrice)
        ))
        return "Producto creado: [${product.id}] ${product.code} - ${product.name}"
    }

    @Tool
    @LLMDescription("Ajustar stock de un producto. Usar delta positivo para entrada, negativo para salida.")
    fun adjustStock(
        @LLMDescription("ID del producto") productId: Long,
        @LLMDescription("Cantidad a ajustar (positivo=entrada, negativo=salida)") delta: Int
    ): String {
        return if (productRepo.updateStock(productId, delta)) {
            val p = productRepo.findById(productId)!!
            "Stock ajustado. ${p.name} ahora tiene ${p.stock} unidades."
        } else "No se pudo ajustar el stock. Verifique que el producto exista y haya stock suficiente."
    }

    // ── Clientes ──

    @Tool
    @LLMDescription("Listar todos los clientes.")
    fun listClients(): String {
        val clients = clientRepo.findAll()
        return if (clients.isEmpty()) "No hay clientes cargados"
        else "${clients.size} clientes:\n" + clients.joinToString("\n") { "- [${it.id}] ${it.name} | tel: ${it.phone} | deuda: \$${it.balance}" }
    }

    @Tool
    @LLMDescription("Buscar clientes por nombre.")
    fun searchClients(@LLMDescription("Texto a buscar en nombre") query: String): String {
        val results = clientRepo.search(query)
        return if (results.isEmpty()) "No se encontraron clientes para '$query'"
        else results.joinToString("\n") { "- [${it.id}] ${it.name} | tel: ${it.phone} | deuda: \$${it.balance}" }
    }

    @Tool
    @LLMDescription("Crear un nuevo cliente.")
    fun createClient(
        @LLMDescription("Nombre del cliente") name: String,
        @LLMDescription("Telefono del cliente") phone: String,
        @LLMDescription("Direccion del cliente") address: String
    ): String {
        val client = clientRepo.insert(Client(name = name, phone = phone, address = address))
        return "Cliente creado: [${client.id}] ${client.name}"
    }

    // ── Pedidos ──

    @Tool
    @LLMDescription("Listar pedidos. Opcionalmente filtrar por estado: CREATED, CONFIRMED, ASSEMBLED, INVOICED, CANCELLED.")
    fun listOrders(@LLMDescription("Estado para filtrar (vacio para todos)") status: String = ""): String {
        val orders = if (status.isBlank()) orderRepo.findAll()
        else {
            val s = try { OrderStatus.valueOf(status) } catch (_: Exception) { return "Estado invalido: $status" }
            orderRepo.findByStatus(s)
        }
        return if (orders.isEmpty()) "No hay pedidos"
        else "${orders.size} pedidos:\n" + orders.joinToString("\n") { "- Pedido #${it.id} | cliente: ${it.clientId} | ${it.status} | total: \$${it.totalArs}" }
    }

    @Tool
    @LLMDescription("Ver detalle de un pedido con sus items.")
    fun getOrder(@LLMDescription("ID del pedido") orderId: Long): String {
        val o = orderRepo.findById(orderId) ?: return "Pedido $orderId no encontrado"
        val clientName = orderRepo.clientName(o.clientId) ?: "?"
        val items = o.items.joinToString("\n") { "  - producto:${it.productId} x${it.quantity} @\$${it.unitPriceArs} = \$${it.subtotalArs}" }
        return "Pedido #${o.id} | $clientName | ${o.status} | ${o.createdAt}\nTotal: \$${o.totalArs}\nItems:\n$items"
    }

    @Tool
    @LLMDescription("Crear un nuevo pedido. Los items son una lista JSON: [{\"productId\":1,\"quantity\":2,\"unitPrice\":\"650.00\"}]")
    fun createOrder(
        @LLMDescription("ID del cliente") clientId: Long,
        @LLMDescription("Items del pedido como JSON array") itemsJson: String
    ): String {
        @Serializable data class ItemInput(val productId: Long, val quantity: Int, val unitPrice: String)
        return try {
            val parsed = json.decodeFromString<List<ItemInput>>(itemsJson)
            val items = parsed.map { Triple(it.productId, it.quantity, BigDecimal(it.unitPrice)) }
            val order = orderService.createOrder(clientId, items, now())
            "Pedido #${order.id} creado | ${order.items.size} items | total: \$${order.totalArs}"
        } catch (e: Exception) { "Error al crear pedido: ${e.message}" }
    }

    @Tool
    @LLMDescription("Confirmar un pedido (bloquea los items).")
    fun confirmOrder(@LLMDescription("ID del pedido") orderId: Long): String {
        return if (orderService.confirmOrder(orderId)) "Pedido #$orderId confirmado"
        else "No se pudo confirmar el pedido #$orderId. Verifique que este en estado CREATED."
    }

    @Tool
    @LLMDescription("Armar un pedido (descuenta stock).")
    fun assembleOrder(@LLMDescription("ID del pedido") orderId: Long): String {
        return if (orderService.assembleOrder(orderId, emptyMap())) "Pedido #$orderId armado. Stock descontado."
        else "No se pudo armar el pedido #$orderId. Verifique estado y stock."
    }

    @Tool
    @LLMDescription("Facturar un pedido (registra la venta y genera deuda al cliente).")
    fun invoiceOrder(@LLMDescription("ID del pedido") orderId: Long): String {
        return if (orderService.invoiceOrder(orderId, now())) "Pedido #$orderId facturado."
        else "No se pudo facturar el pedido #$orderId. Verifique que este en estado ASSEMBLED."
    }

    @Tool
    @LLMDescription("Cancelar un pedido.")
    fun cancelOrder(@LLMDescription("ID del pedido") orderId: Long): String {
        return if (orderService.cancelOrder(orderId)) "Pedido #$orderId cancelado."
        else "No se pudo cancelar el pedido #$orderId."
    }

    // ── Cotizacion dolar ──

    @Tool
    @LLMDescription("Obtener la cotizacion actual del dolar.")
    fun getDollarRate(): String {
        val rate = dollarRateRepo.getLatest() ?: return "No hay cotizacion del dolar configurada."
        return "Cotizacion: \$${rate.rate} al ${rate.date}"
    }

    @Tool
    @LLMDescription("Actualizar la cotizacion del dolar para hoy.")
    fun setDollarRate(@LLMDescription("Nuevo valor del dolar en ARS, ej '1450.00'") rate: String): String {
        val dollarRate = dollarRateRepo.insert(DollarRate(rate = BigDecimal(rate), date = today()))
        return "Cotizacion actualizada: \$${dollarRate.rate} al ${dollarRate.date}"
    }

    // ── Finanzas ──

    @Tool
    @LLMDescription("Ver ultimos movimientos financieros.")
    fun getMovements(): String {
        val movements = financeService.getAllMovements().take(20)
        return if (movements.isEmpty()) "No hay movimientos"
        else movements.joinToString("\n") { "- ${it.type} | \$${it.amount} | ${it.date.toString().take(16)} | ${it.description}" }
    }

    @Tool
    @LLMDescription("Registrar un cobro a un cliente.")
    fun recordClientPayment(
        @LLMDescription("ID del cliente") clientId: Long,
        @LLMDescription("Monto cobrado como string, ej '5000.00'") amount: String,
        @LLMDescription("Descripcion del cobro") description: String
    ): String {
        return if (financeService.recordClientPayment(clientId, BigDecimal(amount), now(), description))
            "Cobro de \$$amount registrado."
        else "No se pudo registrar el cobro."
    }

    @Tool
    @LLMDescription("Registrar un pago al proveedor.")
    fun recordSupplierPayment(
        @LLMDescription("Monto pagado como string, ej '50000.00'") amount: String,
        @LLMDescription("Descripcion del pago") description: String
    ): String {
        return if (financeService.recordSupplierPayment(BigDecimal(amount), now(), description))
            "Pago de \$$amount al proveedor registrado."
        else "No se pudo registrar el pago."
    }

    // ── Proveedor ──

    @Tool
    @LLMDescription("Obtener informacion del proveedor.")
    fun getSupplier(): String {
        val s = supplierRepo.get() ?: return "No hay proveedor configurado."
        return "Proveedor: ${s.name} | tel: ${s.phone} | deuda: \$${s.balance}"
    }
}
