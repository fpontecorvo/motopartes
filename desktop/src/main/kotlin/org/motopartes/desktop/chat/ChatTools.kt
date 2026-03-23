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
import org.motopartes.service.CsvImportService
import org.motopartes.service.FinanceService
import org.motopartes.service.OrderService
import org.motopartes.service.PurchaseItem
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

// ── Helpers para formateo consistente ──

private fun Product.format() =
    "[id=${id}] ${code} | ${name} | compra: ${purchasePrice} ${purchaseCurrency} | stock: ${stock}"

private fun Client.format() =
    "[id=${id}] ${name} | tel: ${phone.ifBlank { "sin tel" }} | dir: ${address.ifBlank { "sin dir" }} | deuda: \$${balance} ARS"

private fun Order.formatSummary(clientName: String? = null) =
    "Venta #${id} | cliente: ${clientName ?: "id=$clientId"} | estado: ${status} | creado: ${createdAt.toString().take(16)} | total: \$${totalArs} ARS"

private fun FinancialMovement.format() =
    "[${id}] ${type} | \$${amount} ARS | ${date.toString().take(16)} | cliente:${clientId ?: "-"} | proveedor:${supplierId ?: "-"} | venta:${orderId ?: "-"} | ${description}"

class MotopartesTools(
    private val productRepo: ProductRepository,
    private val clientRepo: ClientRepository,
    private val supplierRepo: SupplierRepository,
    private val dollarRateRepo: DollarRateRepository,
    private val orderRepo: OrderRepository,
    private val orderService: OrderService,
    private val financeService: FinanceService,
    private val purchaseService: PurchaseService,
    private val csvImportService: CsvImportService
) : ToolSet {

    // ═══════════════════════════════════════════════════════════
    // PRODUCTOS
    // ═══════════════════════════════════════════════════════════

    @Tool
    @LLMDescription("""Buscar productos por texto parcial en codigo o nombre.
Usar cuando el usuario menciona un producto por nombre, marca, modelo de moto, o codigo parcial.
Ejemplos de uso: "busca cadenas", "tenes algo para CG125", "producto 1043".
Retorna lista con id, codigo, nombre, precios y stock de cada resultado.""")
    fun searchProducts(@LLMDescription("Texto parcial a buscar en codigo o nombre del producto. Puede ser codigo, nombre, marca, modelo de moto, etc.") query: String): String {
        val results = productRepo.search(query)
        return if (results.isEmpty()) "No se encontraron productos para '$query'. Intenta con otro termino o usa listProducts para ver todos."
        else "Encontrados ${results.size} productos para '$query':\n" + results.joinToString("\n") { it.format() }
    }

    @Tool
    @LLMDescription("""Listar TODOS los productos del sistema con su stock y precio de venta.
IMPORTANTE: hay ~6800 productos, la respuesta es muy larga.
Preferir searchProducts si el usuario busca algo especifico.
Usar solo si pide "todos los productos", "mi catalogo completo", o para analisis general.""")
    fun listProducts(): String {
        val products = productRepo.findAll()
        if (products.isEmpty()) return "No hay productos cargados en el sistema."
        val withStock = products.count { it.stock > 0 }
        val totalUnits = products.sumOf { it.stock }
        return "Total: ${products.size} productos (${withStock} con stock, ${totalUnits} unidades totales)\n" +
            products.joinToString("\n") { it.format() }
    }

    @Tool
    @LLMDescription("""Obtener detalle completo de UN producto por su ID numerico.
Usar cuando ya se tiene el ID del producto (obtenido de searchProducts o listProducts).
Retorna toda la informacion: codigo, nombre, descripcion, precios de compra y venta, moneda, stock.""")
    fun getProduct(@LLMDescription("ID numerico del producto (ej: 232)") productId: Long): String {
        val p = productRepo.findById(productId) ?: return "ERROR: Producto con id=$productId no encontrado. Usa searchProducts para buscarlo por nombre."
        return """Producto detalle:
  ID: ${p.id}
  Codigo: ${p.code}
  Nombre: ${p.name}
  Descripcion: ${p.description.ifBlank { "(sin descripcion)" }}
  Precio compra: ${p.purchasePrice} ${p.purchaseCurrency}
  Precio venta: $${p.purchasePrice} ${p.purchaseCurrency} ARS
  Stock actual: ${p.stock} unidades"""
    }

    @Tool
    @LLMDescription("""Ver solo los productos que tienen stock disponible (stock > 0).
Usar cuando preguntan "que tengo en stock", "mi inventario", "productos disponibles".
Mucho mas rapido y util que listProducts cuando solo interesan los disponibles.""")
    fun listProductsWithStock(): String {
        val products = productRepo.findAll().filter { it.stock > 0 }
        if (products.isEmpty()) return "No hay ningun producto con stock disponible."
        val totalUnits = products.sumOf { it.stock }
        val totalValue = products.sumOf { BigDecimal(it.stock.toLong()) * it.purchasePrice }
        return "Productos con stock: ${products.size} (${totalUnits} unidades, valor total: \$${totalValue} ARS)\n" +
            products.sortedByDescending { it.stock }.joinToString("\n") { it.format() }
    }

    @Tool
    @LLMDescription("""Crear un nuevo producto en el sistema.
Solo usar cuando el usuario explicitamente pide crear/agregar un producto nuevo.
Los precios se pasan como strings numericos (ej: "1500.00"). La moneda de compra es USD o ARS.""")
    fun createProduct(
        @LLMDescription("Codigo unico del producto (ej: '1043/00070-022')") code: String,
        @LLMDescription("Nombre descriptivo del producto (ej: 'CADENA 428H 118L')") name: String,
        @LLMDescription("Precio de compra como string numerico con decimales (ej: '15.50')") purchasePrice: String,
        @LLMDescription("Moneda de compra: exactamente 'USD' o 'ARS'") purchaseCurrency: String
    ): String {
        return try {
            val currency = try { Currency.valueOf(purchaseCurrency.uppercase()) } catch (_: Exception) {
                return "ERROR: Moneda invalida '$purchaseCurrency'. Usar 'USD' o 'ARS'."
            }
            val product = productRepo.insert(Product(
                code = code, name = name,
                purchasePrice = BigDecimal(purchasePrice),
                purchaseCurrency = currency,
            ))
            "OK: Producto creado exitosamente.\n${product.format()}"
        } catch (e: Exception) { "ERROR al crear producto: ${e.message}" }
    }

    @Tool
    @LLMDescription("""Ajustar el stock de un producto existente.
Delta positivo = entrada de mercaderia (ej: +10).
Delta negativo = salida/ajuste manual (ej: -3).
NOTA: NO usar para ventas (eso lo hace el flujo de ventas automaticamente).""")
    fun adjustStock(
        @LLMDescription("ID numerico del producto") productId: Long,
        @LLMDescription("Cantidad a ajustar: positivo para sumar stock, negativo para restar") delta: Int
    ): String {
        return if (productRepo.updateStock(productId, delta)) {
            val p = productRepo.findById(productId)!!
            "OK: Stock ajustado (${if (delta > 0) "+$delta" else "$delta"}). ${p.name} ahora tiene ${p.stock} unidades."
        } else "ERROR: No se pudo ajustar el stock. Verificar que el producto id=$productId exista y haya stock suficiente si se resta."
    }

    @Tool
    @LLMDescription("""Actualizar precio de venta, precio de compra, o nombre de un producto existente.
Usar cuando piden cambiar/actualizar precios o datos de un producto.
Primero busca el producto con searchProducts o getProduct para obtener su ID y datos actuales.""")
    fun updateProduct(
        @LLMDescription("ID numerico del producto a actualizar") productId: Long,
        @LLMDescription("Nuevo codigo (o vacio '' para mantener el actual)") code: String = "",
        @LLMDescription("Nuevo nombre (o vacio '' para mantener el actual)") name: String = "",
        @LLMDescription("Nuevo precio de compra como string (o vacio '' para mantener)") purchasePrice: String = "",
        @LLMDescription("Moneda de compra: 'USD' o 'ARS' (o vacio '' para mantener)") purchaseCurrency: String = ""
    ): String {
        val existing = productRepo.findById(productId)
            ?: return "ERROR: Producto id=$productId no encontrado. Usa searchProducts para buscarlo."
        return try {
            val updated = existing.copy(
                code = if (code.isNotBlank()) code else existing.code,
                name = if (name.isNotBlank()) name else existing.name,
                purchasePrice = if (purchasePrice.isNotBlank()) BigDecimal(purchasePrice) else existing.purchasePrice,
                purchaseCurrency = if (purchaseCurrency.isNotBlank()) Currency.valueOf(purchaseCurrency.uppercase()) else existing.purchaseCurrency
            )
            productRepo.update(updated)
            "OK: Producto actualizado.\nAntes: ${existing.format()}\nAhora: ${updated.format()}"
        } catch (e: Exception) { "ERROR al actualizar producto: ${e.message}" }
    }

    // ═══════════════════════════════════════════════════════════
    // CLIENTES
    // ═══════════════════════════════════════════════════════════

    @Tool
    @LLMDescription("""Listar todos los clientes con su nombre, telefono, direccion y deuda.
Usar cuando piden "mis clientes", "listado de clientes", "a quienes les vendo".""")
    fun listClients(): String {
        val clients = clientRepo.findAll()
        if (clients.isEmpty()) return "No hay clientes cargados. Usa createClient para agregar uno."
        val totalDebt = clients.sumOf { it.balance }
        return "${clients.size} clientes (deuda total: \$$totalDebt ARS):\n" + clients.joinToString("\n") { it.format() }
    }

    @Tool
    @LLMDescription("""Buscar clientes por nombre parcial.
Usar cuando mencionan un cliente por nombre: "busca al cliente Lopez", "el cliente Juan".""")
    fun searchClients(@LLMDescription("Texto parcial a buscar en el nombre del cliente") query: String): String {
        val results = clientRepo.search(query)
        return if (results.isEmpty()) "No se encontraron clientes para '$query'. Usa listClients para ver todos o createClient para crear uno nuevo."
        else "Encontrados ${results.size} clientes para '$query':\n" + results.joinToString("\n") { it.format() }
    }

    @Tool
    @LLMDescription("""Obtener detalle completo de un cliente por ID, incluyendo sus ventas recientes.
Usar para ver la ficha de un cliente especifico con contexto de sus ventas.""")
    fun getClient(@LLMDescription("ID numerico del cliente") clientId: Long): String {
        val c = clientRepo.findById(clientId)
            ?: return "ERROR: Cliente id=$clientId no encontrado. Usa searchClients para buscarlo por nombre."
        val orders = orderRepo.findByClient(clientId)
        val ordersSummary = if (orders.isEmpty()) "  Sin ventas."
        else orders.takeLast(5).joinToString("\n") { "  ${it.formatSummary()}" }
        return """Cliente detalle:
  ID: ${c.id}
  Nombre: ${c.name}
  Telefono: ${c.phone.ifBlank { "(sin tel)" }}
  Direccion: ${c.address.ifBlank { "(sin dir)" }}
  Deuda: $${c.balance} ARS
Ultimos ventas (max 5):
$ordersSummary"""
    }

    @Tool
    @LLMDescription("""Crear un nuevo cliente.
Solo usar cuando el usuario explicitamente pide agregar/crear un cliente nuevo.
Telefono y direccion son opcionales, pasar string vacio si no se proporcionan.""")
    fun createClient(
        @LLMDescription("Nombre completo del cliente") name: String,
        @LLMDescription("Telefono (opcional, pasar '' si no hay)") phone: String,
        @LLMDescription("Direccion (opcional, pasar '' si no hay)") address: String
    ): String {
        return try {
            val client = clientRepo.insert(Client(name = name, phone = phone, address = address))
            "OK: Cliente creado.\n${client.format()}"
        } catch (e: Exception) { "ERROR al crear cliente: ${e.message}" }
    }

    // ═══════════════════════════════════════════════════════════
    // PEDIDOS - Flujo: CREATED → CONFIRMED → ASSEMBLED → INVOICED
    //                                                 ↘ CANCELLED
    // ═══════════════════════════════════════════════════════════

    @Tool
    @LLMDescription("""Listar ventas del sistema. Opcionalmente filtrar por estado.
Estados validos: CREATED (nuevo), CONFIRMED (confirmado), ASSEMBLED (armado, stock descontado), INVOICED (facturado), CANCELLED.
Para ver los pendientes: status='CREATED'. Para los listos para armar: status='CONFIRMED'.
Dejar status vacio para ver todos.""")
    fun listOrders(@LLMDescription("Filtrar por estado: 'CREATED', 'CONFIRMED', 'ASSEMBLED', 'INVOICED', 'CANCELLED', o vacio '' para todos") status: String = ""): String {
        val orders = if (status.isBlank()) orderRepo.findAll()
        else {
            val s = try { OrderStatus.valueOf(status.uppercase()) } catch (_: Exception) {
                return "ERROR: Estado invalido '$status'. Usar: CREATED, CONFIRMED, ASSEMBLED, INVOICED o CANCELLED."
            }
            orderRepo.findByStatus(s)
        }
        if (orders.isEmpty()) return "No hay ventas${if (status.isNotBlank()) " con estado $status" else ""}."
        return "${orders.size} ventas${if (status.isNotBlank()) " ($status)" else ""}:\n" +
            orders.joinToString("\n") {
                val clientName = try { orderRepo.clientName(it.clientId) } catch (_: Exception) { null }
                it.formatSummary(clientName)
            }
    }

    @Tool
    @LLMDescription("""Ver detalle completo de un venta con todos sus items.
Muestra: cliente, estado, fecha, items (producto, cantidad, precio unitario, subtotal) y total.
Usar cuando piden ver un venta especifico: "detalle del venta 5", "que tiene el venta #3".""")
    fun getOrder(@LLMDescription("ID numerico del venta") orderId: Long): String {
        val o = orderRepo.findById(orderId) ?: return "ERROR: Pedido #$orderId no encontrado."
        val clientName = try { orderRepo.clientName(o.clientId) ?: "id=${o.clientId}" } catch (_: Exception) { "id=${o.clientId}" }
        val items = o.items.joinToString("\n") {
            val prodName = try { productRepo.findById(it.productId)?.name ?: "id=${it.productId}" } catch (_: Exception) { "id=${it.productId}" }
            "  - [producto id=${it.productId}] $prodName x${it.quantity} @ \$${it.unitPriceArs} ARS = \$${it.subtotalArs} ARS"
        }
        return """Venta #${o.id}:
  Cliente: $clientName (id=${o.clientId})
  Estado: ${o.status}
  Creado: ${o.createdAt}
  Items:
$items
  TOTAL: $${o.totalArs} ARS

Flujo: CREATED → confirmOrder → CONFIRMED → assembleOrder → ASSEMBLED → invoiceOrder → INVOICED"""
    }

    @Tool
    @LLMDescription("""Crear un nuevo venta para un cliente.
PASO 1: Necesitas el ID del cliente (usa searchClients o listClients si no lo tenes).
PASO 2: Necesitas los items como JSON array con productId, quantity, unitPrice.
PASO 3: Llama a esta herramienta.
El venta se crea en estado CREATED. Luego hay que confirmarlo, armarlo y facturarlo.

Ejemplo itemsJson: [{"productId":232,"quantity":2,"unitPrice":"5372.25"}]
IMPORTANTE: unitPrice es el precio de venta al cliente en ARS (puede ser distinto al precio de lista).""")
    fun createOrder(
        @LLMDescription("ID numerico del cliente") clientId: Long,
        @LLMDescription("""Items del venta como JSON array. Formato exacto:
[{"productId":232,"quantity":2,"unitPrice":"5372.25"},{"productId":661,"quantity":1,"unitPrice":"28275.00"}]
productId = ID del producto, quantity = cantidad, unitPrice = precio unitario de venta en ARS como string""") itemsJson: String
    ): String {
        @Serializable data class ItemInput(val productId: Long, val quantity: Int, val unitPrice: String)
        return try {
            val parsed = json.decodeFromString<List<ItemInput>>(itemsJson)
            if (parsed.isEmpty()) return "ERROR: La lista de items esta vacia."
            val items = parsed.map { Triple(it.productId, it.quantity, BigDecimal(it.unitPrice)) }
            val order = orderService.createOrder(clientId, items, now())
            "OK: Pedido creado exitosamente.\nPedido #${order.id} | ${order.items.size} items | total: \$${order.totalArs} ARS\nEstado: CREATED → Siguiente paso: confirmOrder(${order.id})"
        } catch (e: Exception) { "ERROR al crear venta: ${e.message}" }
    }

    @Tool
    @LLMDescription("""Confirmar un venta en estado CREATED → pasa a CONFIRMED.
Esto bloquea los items del venta. Siguiente paso seria assembleOrder.
Solo funciona si el venta esta en estado CREATED.""")
    fun confirmOrder(@LLMDescription("ID numerico del venta a confirmar") orderId: Long): String {
        return if (orderService.confirmOrder(orderId))
            "OK: Pedido #$orderId confirmado (CREATED → CONFIRMED). Siguiente paso: assembleOrder($orderId)"
        else "ERROR: No se pudo confirmar venta #$orderId. Verificar que este en estado CREATED. Usa getOrder($orderId) para ver su estado actual."
    }

    @Tool
    @LLMDescription("""Armar un venta en estado CONFIRMED → pasa a ASSEMBLED.
IMPORTANTE: Esto DESCUENTA el stock de los productos. Verificar disponibilidad antes.
Solo funciona si el venta esta en estado CONFIRMED y hay stock suficiente.""")
    fun assembleOrder(@LLMDescription("ID numerico del venta a armar") orderId: Long): String {
        return if (orderService.assembleOrder(orderId, emptyMap()))
            "OK: Pedido #$orderId armado (CONFIRMED → ASSEMBLED). Stock descontado. Siguiente paso: invoiceOrder($orderId)"
        else "ERROR: No se pudo armar venta #$orderId. Verificar que este en CONFIRMED y haya stock suficiente. Usa getOrder($orderId) para diagnosticar."
    }

    @Tool
    @LLMDescription("""Facturar un venta en estado ASSEMBLED → pasa a INVOICED.
Esto registra la venta como movimiento financiero y genera la deuda al cliente.
Solo funciona si el venta esta en estado ASSEMBLED.""")
    fun invoiceOrder(@LLMDescription("ID numerico del venta a facturar") orderId: Long): String {
        return if (orderService.invoiceOrder(orderId, now()))
            "OK: Pedido #$orderId facturado (ASSEMBLED → INVOICED). Venta registrada y deuda generada al cliente."
        else "ERROR: No se pudo facturar venta #$orderId. Verificar que este en ASSEMBLED. Usa getOrder($orderId) para ver su estado."
    }

    @Tool
    @LLMDescription("""Cancelar un venta. Si estaba ASSEMBLED, devuelve el stock.
Funciona desde cualquier estado excepto INVOICED.""")
    fun cancelOrder(@LLMDescription("ID numerico del venta a cancelar") orderId: Long): String {
        return if (orderService.cancelOrder(orderId))
            "OK: Pedido #$orderId cancelado."
        else "ERROR: No se pudo cancelar venta #$orderId. Los ventas INVOICED no se pueden cancelar."
    }

    @Tool
    @LLMDescription("""Procesar un venta completo de una sola vez: crear + confirmar + armar + facturar.
Atajo para cuando el usuario quiere hacer todo el flujo de golpe.
IMPORTANTE: Descuenta stock y genera deuda. Verificar que haya stock suficiente antes de usar.""")
    fun quickOrder(
        @LLMDescription("ID numerico del cliente") clientId: Long,
        @LLMDescription("""Items del venta como JSON array: [{"productId":232,"quantity":2,"unitPrice":"5372.25"}]""") itemsJson: String
    ): String {
        @Serializable data class ItemInput(val productId: Long, val quantity: Int, val unitPrice: String)
        return try {
            val parsed = json.decodeFromString<List<ItemInput>>(itemsJson)
            if (parsed.isEmpty()) return "ERROR: La lista de items esta vacia."
            val items = parsed.map { Triple(it.productId, it.quantity, BigDecimal(it.unitPrice)) }
            val order = orderService.createOrder(clientId, items, now())
            val steps = mutableListOf("Venta #${order.id} creado")

            if (!orderService.confirmOrder(order.id)) return "Pedido creado (#${order.id}) pero fallo al confirmar. Usa getOrder(${order.id}) para ver estado."
            steps.add("confirmado")

            if (!orderService.assembleOrder(order.id, emptyMap())) return "Venta #${order.id} confirmado pero fallo al armar (stock insuficiente?). Usa getOrder(${order.id})."
            steps.add("armado (stock descontado)")

            if (!orderService.invoiceOrder(order.id, now())) return "Venta #${order.id} armado pero fallo al facturar. Usa getOrder(${order.id})."
            steps.add("facturado")

            "OK: Pedido #${order.id} procesado completamente → ${steps.joinToString(" → ")}\nTotal: \$${order.totalArs} ARS | ${order.items.size} items"
        } catch (e: Exception) { "ERROR: ${e.message}" }
    }

    // ═══════════════════════════════════════════════════════════
    // COTIZACION DOLAR
    // ═══════════════════════════════════════════════════════════

    @Tool
    @LLMDescription("""Obtener la cotizacion actual del dolar (ARS por USD).
Usar cuando preguntan "a cuanto esta el dolar", "cotizacion", "tipo de cambio".
Se necesita para calcular precios de productos que se compran en USD.""")
    fun getDollarRate(): String {
        val rate = dollarRateRepo.getLatest() ?: return "No hay cotizacion del dolar configurada. Usa setDollarRate para cargar una."
        return "Cotizacion dolar vigente: \$${rate.rate} ARS por 1 USD (fecha: ${rate.date})"
    }

    @Tool
    @LLMDescription("""Cargar/actualizar la cotizacion del dolar para la fecha de hoy.
Usar cuando dicen "el dolar esta a 1500", "actualizame el dolar".""")
    fun setDollarRate(@LLMDescription("Valor del dolar en ARS como string numerico (ej: '1450.00')") rate: String): String {
        return try {
            val dollarRate = dollarRateRepo.insert(DollarRate(rate = BigDecimal(rate), date = today()))
            "OK: Cotizacion actualizada: \$${dollarRate.rate} ARS por 1 USD (fecha: ${dollarRate.date})"
        } catch (e: Exception) { "ERROR al actualizar cotizacion: ${e.message}" }
    }

    // ═══════════════════════════════════════════════════════════
    // FINANZAS
    // ═══════════════════════════════════════════════════════════

    @Tool
    @LLMDescription("""Ver los ultimos movimientos financieros del sistema.
Tipos de movimiento: SALE (venta facturada), PURCHASE (compra a proveedor), CLIENT_PAYMENT (cobro a cliente), SUPPLIER_PAYMENT (pago a proveedor).
Muestra los ultimos 20 movimientos con tipo, monto, fecha, y referencias.""")
    fun getMovements(): String {
        val movements = financeService.getAllMovements().take(20)
        return if (movements.isEmpty()) "No hay movimientos financieros registrados."
        else "Ultimos ${movements.size} movimientos:\n" + movements.joinToString("\n") { it.format() }
    }

    @Tool
    @LLMDescription("""Ver movimientos financieros de un cliente especifico (cobros y ventas).
Usar cuando preguntan "cuanto me debe Perez", "pagos del cliente X".""")
    fun getClientMovements(@LLMDescription("ID numerico del cliente") clientId: Long): String {
        val movements = financeService.getClientMovements(clientId)
        val client = clientRepo.findById(clientId)
        val clientInfo = client?.let { "Cliente: ${it.name} | Deuda actual: \$${it.balance} ARS\n" } ?: ""
        return if (movements.isEmpty()) "${clientInfo}No hay movimientos para este cliente."
        else "${clientInfo}${movements.size} movimientos:\n" + movements.joinToString("\n") { it.format() }
    }

    @Tool
    @LLMDescription("""Registrar un cobro recibido de un cliente (reduce su deuda).
Usar cuando dicen "el cliente pago", "cobramos X pesos de Y".""")
    fun recordClientPayment(
        @LLMDescription("ID numerico del cliente que paga") clientId: Long,
        @LLMDescription("Monto cobrado en ARS como string numerico (ej: '5000.00')") amount: String,
        @LLMDescription("Descripcion del cobro (ej: 'Pago parcial efectivo', 'Transferencia')") description: String
    ): String {
        return try {
            if (financeService.recordClientPayment(clientId, BigDecimal(amount), now(), description)) {
                val client = clientRepo.findById(clientId)
                "OK: Cobro de \$$amount ARS registrado. ${client?.let { "Deuda restante de ${it.name}: \$${it.balance} ARS" } ?: ""}"
            } else "ERROR: No se pudo registrar el cobro. Verificar que el cliente id=$clientId exista."
        } catch (e: Exception) { "ERROR: ${e.message}" }
    }

    @Tool
    @LLMDescription("""Registrar un pago realizado al proveedor (reduce la deuda con el).
Usar cuando dicen "le pague al proveedor", "transferencia a Tercom".""")
    fun recordSupplierPayment(
        @LLMDescription("Monto pagado en ARS como string numerico (ej: '50000.00')") amount: String,
        @LLMDescription("Descripcion del pago (ej: 'Transferencia bancaria', 'Cheque')") description: String
    ): String {
        return try {
            if (financeService.recordSupplierPayment(BigDecimal(amount), now(), description)) {
                val supplier = supplierRepo.get()
                "OK: Pago de \$$amount ARS al proveedor registrado. ${supplier?.let { "Deuda restante con ${it.name}: \$${it.balance} ARS" } ?: ""}"
            } else "ERROR: No se pudo registrar el pago. Verificar que el proveedor este configurado."
        } catch (e: Exception) { "ERROR: ${e.message}" }
    }

    // ═══════════════════════════════════════════════════════════
    // PROVEEDOR
    // ═══════════════════════════════════════════════════════════

    @Tool
    @LLMDescription("""Obtener informacion del proveedor: nombre, telefono y deuda actual.
Usar cuando preguntan "cuanto le debo al proveedor", "datos del proveedor", "deuda con Tercom".""")
    fun getSupplier(): String {
        val s = supplierRepo.get() ?: return "No hay proveedor configurado en el sistema."
        return "Proveedor: ${s.name} | tel: ${s.phone.ifBlank { "(sin tel)" }} | deuda: \$${s.balance} ARS"
    }

    // ═══════════════════════════════════════════════════════════
    // RESUMEN DEL NEGOCIO
    // ═══════════════════════════════════════════════════════════

    @Tool
    @LLMDescription("""Obtener un resumen ejecutivo completo del negocio.
Usar cuando preguntan "como va el negocio", "dame un resumen", "estado general".
Incluye: totales de productos, stock, deudas de clientes, deuda a proveedor, y ultimos movimientos.""")
    fun getBusinessSummary(): String {
        val products = productRepo.findAll()
        val withStock = products.filter { it.stock > 0 }
        val totalUnits = products.sumOf { it.stock }
        val totalStockValue = withStock.sumOf { BigDecimal(it.stock.toLong()) * it.purchasePrice }
        val clients = clientRepo.findAll()
        val totalClientDebt = clients.sumOf { it.balance }
        val supplier = supplierRepo.get()
        val dollarRate = dollarRateRepo.getLatest()
        val orders = orderRepo.findAll()
        val pendingOrders = orders.filter { it.status != OrderStatus.INVOICED && it.status != OrderStatus.CANCELLED }
        val movements = financeService.getAllMovements().take(5)

        return """═══ RESUMEN MOTOPARTES ═══
Productos: ${products.size} total | ${withStock.size} con stock | ${totalUnits} unidades
Valor stock (venta): $${totalStockValue} ARS${dollarRate?.let { " (~USD ${(totalStockValue / it.rate).setScale(0, java.math.RoundingMode.HALF_UP)})" } ?: ""}
Cotizacion dolar: ${dollarRate?.let { "$${it.rate} ARS (${it.date})" } ?: "No configurada"}
Clientes: ${clients.size} | Deuda total clientes: $${totalClientDebt} ARS
Proveedor: ${supplier?.let { "${it.name} | Deuda: $${it.balance} ARS" } ?: "No configurado"}
Ventas pendientes: ${pendingOrders.size}${pendingOrders.groupBy { it.status }.entries.joinToString("") { " | ${it.key}: ${it.value.size}" }}
Ultimos movimientos:
${if (movements.isEmpty()) "  Sin movimientos" else movements.joinToString("\n") { "  ${it.format()}" }}"""
    }

    // ═══════════════════════════════════════════════════════════
    // IMPORTACION CSV
    // ═══════════════════════════════════════════════════════════

    @Tool
    @LLMDescription("""Importar o actualizar productos masivamente desde contenido CSV.
El CSV DEBE tener una linea de header. Columnas reconocidas:
  - Obligatorias: code/codigo, name/nombre, purchasePrice/precio/costo, purchaseCurrency/moneda (USD o ARS)
  - Opcionales: description/descripcion, stock
Productos existentes (mismo codigo) se actualizan. Nuevos se crean.
El contenido CSV completo se pasa como string.""")
    fun importProducts(@LLMDescription("Contenido CSV completo incluyendo la linea de header") csvContent: String): String {
        return try {
            val result = csvImportService.import(csvContent)
            val msg = result.summary()
            if (result.errors.isEmpty()) "OK: $msg"
            else "OK con advertencias: $msg\nErrores:\n${result.errors.joinToString("\n")}"
        } catch (e: Exception) { "ERROR al importar CSV: ${e.message}" }
    }

    @Tool
    @LLMDescription("""Importar una factura de compra del proveedor desde CSV.
Incrementa el stock de cada producto y registra la deuda con el proveedor.
Formato CSV esperado: Codigo,Articulo,Cantidad,Precio Unitario,Importe
Usa los precios reales de la factura (no los de lista) para registrar la compra.
Si algun producto no existe en el sistema, se reporta como faltante.""")
    fun importPurchaseInvoice(@LLMDescription("Contenido CSV de la factura de compra del proveedor") csvContent: String): String {
        return try {
            val invoiceResult = csvImportService.importPurchaseInvoice(csvContent)
            if (invoiceResult.items.isEmpty() && invoiceResult.missingProducts.isEmpty()) {
                return "ERROR: No se pudieron importar items.${if (invoiceResult.errors.isNotEmpty()) "\nErrores: ${invoiceResult.errors.joinToString(", ")}" else ""}"
            }
            val msgs = mutableListOf<String>()
            if (invoiceResult.missingProducts.isNotEmpty()) {
                msgs.add("⚠ ${invoiceResult.missingProducts.size} productos NO encontrados en el sistema: ${invoiceResult.missingProducts.joinToString(", ") { it.code }}")
            }
            if (invoiceResult.items.isNotEmpty()) {
                val result = purchaseService.registerPurchase(invoiceResult.items, now())
                msgs.add("OK: Compra registrada: ${result.summary()}")
                if (result.errors.isNotEmpty()) msgs.add("Errores: ${result.errors.joinToString(", ")}")
            }
            if (invoiceResult.errors.isNotEmpty()) msgs.add("Advertencias CSV: ${invoiceResult.errors.joinToString(", ")}")
            msgs.joinToString("\n")
        } catch (e: Exception) { "ERROR al importar factura: ${e.message}" }
    }
}
