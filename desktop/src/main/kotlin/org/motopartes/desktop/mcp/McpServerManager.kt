package org.motopartes.desktop.mcp

import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.mcp
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import kotlinx.serialization.json.jsonPrimitive
import org.motopartes.model.*
import org.motopartes.repository.*
import org.motopartes.service.*
import java.math.BigDecimal
import kotlin.concurrent.thread

class McpServerManager(
    private val productRepo: ProductRepository,
    private val clientRepo: ClientRepository,
    private val supplierRepo: SupplierRepository,
    private val dollarRateRepo: DollarRateRepository,
    private val orderRepo: OrderRepository,
    private val orderService: OrderService,
    private val financeService: FinanceService,
    private val purchaseService: PurchaseService,
    private val csvImportService: CsvImportService,
    private val serviceManager: ServiceManager
) {
    private var server: EmbeddedServer<*, *>? = null
    private var port: Int = 3001

    val isRunning: Boolean
        get() = server != null

    fun start(port: Int = 3001) {
        this.port = port
        serviceManager.updateStatus(ServiceType.MCP_SERVER, ServiceStatus.STARTING)
        serviceManager.log(LogLevel.INFO, "MCP", "Iniciando MCP SSE en puerto $port")

        thread(isDaemon = true, name = "mcp-server") {
            try {
                val mcpServer = createServer()

                server = embeddedServer(Netty, port = port) {
                    mcp { mcpServer }
                }.apply {
                    start(wait = false)
                }

                serviceManager.updateStatus(ServiceType.MCP_SERVER, ServiceStatus.RUNNING, port = port)
                serviceManager.log(LogLevel.INFO, "MCP", "MCP SSE corriendo en puerto $port")
            } catch (e: Exception) {
                serviceManager.updateStatus(ServiceType.MCP_SERVER, ServiceStatus.ERROR, errorMessage = e.message)
                serviceManager.log(LogLevel.ERROR, "MCP", "Error al iniciar: ${e.message}")
            }
        }
    }

    fun stop() {
        try {
            if (server != null) {
                server?.stop(gracePeriodMillis = 1000, timeoutMillis = 5000)
                server = null
                serviceManager.updateStatus(ServiceType.MCP_SERVER, ServiceStatus.STOPPED)
                serviceManager.log(LogLevel.INFO, "MCP", "MCP SSE detenido")
            }
        } catch (e: Exception) {
            serviceManager.updateStatus(ServiceType.MCP_SERVER, ServiceStatus.ERROR, errorMessage = "Error al detener: ${e.message}")
            serviceManager.log(LogLevel.ERROR, "MCP", "Error al detener: ${e.message}")
        }
    }

    fun restart(port: Int = this.port) {
        stop()
        Thread.sleep(500)
        start(port)
    }

    private fun createServer(): Server {
        val server = Server(
            serverInfo = Implementation(
                name = "motopartes-mcp",
                version = org.motopartes.config.Version.NAME
            ),
            options = ServerOptions(
                capabilities = ServerCapabilities(
                    tools = ServerCapabilities.Tools(listChanged = false)
                )
            )
        )

        // ═══════════════════════════════════════════════════
        // PRODUCTOS
        // ═══════════════════════════════════════════════════

        server.addTool(
            name = "search_products",
            description = """Buscar productos por texto parcial en codigo o nombre.
Usar cuando el usuario menciona un producto por nombre, marca, modelo de moto, o codigo parcial.
Ejemplos: "busca cadenas", "tenes algo para CG125", "producto 1043".
Retorna lista con id, codigo, nombre, precio de compra y stock."""
        ) { request ->
            val query = request.arguments!!["query"]?.jsonPrimitive?.content ?: ""
            val results = productRepo.search(query)
            val text = if (results.isEmpty()) "No se encontraron productos para '$query'."
            else "Encontrados ${results.size} productos:\n" + results.joinToString("\n") { it.format() }
            CallToolResult(content = listOf(TextContent(text)))
        }

        server.addTool(
            name = "list_products_with_stock",
            description = """Ver solo los productos que tienen stock disponible (stock > 0).
Usar cuando preguntan "que tengo en stock", "mi inventario", "productos disponibles".
Retorna productos ordenados por stock descendente."""
        ) { _ ->
            val products = productRepo.findAll().filter { it.stock > 0 }
            val text = if (products.isEmpty()) "No hay productos con stock disponible."
            else {
                val totalUnits = products.sumOf { it.stock }
                "Productos con stock: ${products.size} (${totalUnits} unidades)\n" +
                    products.sortedByDescending { it.stock }.joinToString("\n") { it.format() }
            }
            CallToolResult(content = listOf(TextContent(text)))
        }

        server.addTool(
            name = "list_all_products",
            description = """Listar TODOS los productos (~6800). Respuesta muy larga.
Preferir search_products o list_products_with_stock.
Usar solo si piden "catalogo completo" o analisis general."""
        ) { _ ->
            val products = productRepo.findAll()
            val withStock = products.count { it.stock > 0 }
            val totalUnits = products.sumOf { it.stock }
            val text = "Total: ${products.size} productos (${withStock} con stock, ${totalUnits} unidades)\n" +
                products.joinToString("\n") { it.format() }
            CallToolResult(content = listOf(TextContent(text)))
        }

        server.addTool(
            name = "get_product",
            description = """Detalle completo de un producto por ID.
Usar cuando ya se tiene el ID (de search_products)."""
        ) { request ->
            val id = request.arguments!!["productId"]?.jsonPrimitive?.content?.toLongOrNull()
                ?: return@addTool errorResult("Falta productId")
            val p = productRepo.findById(id) ?: return@addTool errorResult("Producto id=$id no encontrado.")
            CallToolResult(content = listOf(TextContent("""Producto:
  ID: ${p.id} | Codigo: ${p.code} | Nombre: ${p.name}
  Descripcion: ${p.description.ifBlank { "(sin descripcion)" }}
  Compra: ${p.purchasePrice} ${p.purchaseCurrency} | Stock: ${p.stock}""")))
        }

        server.addTool(
            name = "create_product",
            description = """Crear un nuevo producto. Parametros: code, name, purchasePrice (string numerico),
purchaseCurrency (USD o ARS). Opcionales: description, stock (default 0)."""
        ) { request ->
            try {
                val code = request.arguments!!["code"]?.jsonPrimitive?.content ?: return@addTool errorResult("Falta code")
                val name = request.arguments!!["name"]?.jsonPrimitive?.content ?: return@addTool errorResult("Falta name")
                val pp = request.arguments!!["purchasePrice"]?.jsonPrimitive?.content ?: return@addTool errorResult("Falta purchasePrice")
                val pc = request.arguments!!["purchaseCurrency"]?.jsonPrimitive?.content ?: return@addTool errorResult("Falta purchaseCurrency")
                val desc = request.arguments!!["description"]?.jsonPrimitive?.content ?: ""
                val stock = request.arguments!!["stock"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                val product = productRepo.insert(Product(
                    code = code, name = name, description = desc,
                    purchasePrice = BigDecimal(pp),
                    purchaseCurrency = Currency.valueOf(pc.uppercase()),
                    stock = stock
                ))
                CallToolResult(content = listOf(TextContent("OK: Producto creado. ${product.format()}")))
            } catch (e: Exception) { errorResult("Error: ${e.message}") }
        }

        server.addTool(
            name = "update_product",
            description = """Actualizar un producto existente. Pasar productId y los campos a cambiar.
Campos opcionales: code, name, description, purchasePrice, purchaseCurrency (pasar vacio para no cambiar)."""
        ) { request ->
            try {
                val id = request.arguments!!["productId"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@addTool errorResult("Falta productId")
                val existing = productRepo.findById(id) ?: return@addTool errorResult("Producto id=$id no encontrado.")
                val updated = existing.copy(
                    code = request.arguments!!["code"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: existing.code,
                    name = request.arguments!!["name"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: existing.name,
                    description = request.arguments!!["description"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: existing.description,
                    purchasePrice = request.arguments!!["purchasePrice"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }?.let { BigDecimal(it) } ?: existing.purchasePrice,
                    purchaseCurrency = request.arguments!!["purchaseCurrency"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }?.let { Currency.valueOf(it.uppercase()) } ?: existing.purchaseCurrency
                )
                productRepo.update(updated)
                CallToolResult(content = listOf(TextContent("OK: Actualizado.\nAntes: ${existing.format()}\nAhora: ${updated.format()}")))
            } catch (e: Exception) { errorResult("Error: ${e.message}") }
        }

        server.addTool(
            name = "adjust_stock",
            description = """Ajustar stock de un producto. delta positivo=entrada, negativo=salida.
NO usar para ventas (el flujo de pedidos lo hace automaticamente)."""
        ) { request ->
            val id = request.arguments!!["productId"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@addTool errorResult("Falta productId")
            val delta = request.arguments!!["delta"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@addTool errorResult("Falta delta")
            if (productRepo.updateStock(id, delta)) {
                val p = productRepo.findById(id)!!
                CallToolResult(content = listOf(TextContent("OK: Stock ajustado (${if (delta > 0) "+$delta" else "$delta"}). ${p.name}: ${p.stock} unidades.")))
            } else errorResult("No se pudo ajustar. Verificar producto y stock suficiente.")
        }

        // ═══════════════════════════════════════════════════
        // CLIENTES
        // ═══════════════════════════════════════════════════

        server.addTool(
            name = "list_clients",
            description = "Listar todos los clientes con nombre, telefono, direccion y deuda."
        ) { _ ->
            val clients = clientRepo.findAll()
            val text = if (clients.isEmpty()) "No hay clientes. Usar create_client para agregar."
            else {
                val totalDebt = clients.sumOf { it.balance }
                "${clients.size} clientes (deuda total: \$$totalDebt ARS):\n" + clients.joinToString("\n") { it.formatClient() }
            }
            CallToolResult(content = listOf(TextContent(text)))
        }

        server.addTool(
            name = "search_clients",
            description = "Buscar clientes por nombre parcial."
        ) { request ->
            val query = request.arguments!!["query"]?.jsonPrimitive?.content ?: ""
            val results = clientRepo.search(query)
            val text = if (results.isEmpty()) "No se encontraron clientes para '$query'."
            else results.joinToString("\n") { it.formatClient() }
            CallToolResult(content = listOf(TextContent(text)))
        }

        server.addTool(
            name = "get_client",
            description = "Detalle de un cliente por ID, incluyendo sus pedidos recientes."
        ) { request ->
            val id = request.arguments!!["clientId"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@addTool errorResult("Falta clientId")
            val c = clientRepo.findById(id) ?: return@addTool errorResult("Cliente id=$id no encontrado.")
            val orders = orderRepo.findByClient(id)
            val orderInfo = if (orders.isEmpty()) "  Sin pedidos."
            else orders.takeLast(5).joinToString("\n") { "  Pedido #${it.id} | ${it.status} | \$${it.totalArs} ARS | ${it.createdAt.toString().take(16)}" }
            CallToolResult(content = listOf(TextContent("Cliente [${c.id}]: ${c.name} | tel: ${c.phone} | dir: ${c.address} | deuda: \$${c.balance} ARS\nUltimos pedidos:\n$orderInfo")))
        }

        server.addTool(
            name = "create_client",
            description = "Crear un nuevo cliente. Parametros: name (obligatorio), phone (opcional), address (opcional)."
        ) { request ->
            try {
                val name = request.arguments!!["name"]?.jsonPrimitive?.content ?: return@addTool errorResult("Falta name")
                val phone = request.arguments!!["phone"]?.jsonPrimitive?.content ?: ""
                val address = request.arguments!!["address"]?.jsonPrimitive?.content ?: ""
                val client = clientRepo.insert(Client(name = name, phone = phone, address = address))
                CallToolResult(content = listOf(TextContent("OK: Cliente creado. ${client.formatClient()}")))
            } catch (e: Exception) { errorResult("Error: ${e.message}") }
        }

        // ═══════════════════════════════════════════════════
        // PEDIDOS / VENTAS
        // ═══════════════════════════════════════════════════

        server.addTool(
            name = "list_orders",
            description = """Listar pedidos. Filtrar por estado: CREATED, CONFIRMED, ASSEMBLED, INVOICED, CANCELLED.
Dejar status vacio para todos."""
        ) { request ->
            val status = request.arguments!!["status"]?.jsonPrimitive?.content ?: ""
            val orders = if (status.isBlank()) orderRepo.findAll()
            else {
                val s = try { OrderStatus.valueOf(status.uppercase()) } catch (_: Exception) { return@addTool errorResult("Estado invalido: $status") }
                orderRepo.findByStatus(s)
            }
            val text = if (orders.isEmpty()) "No hay pedidos${if (status.isNotBlank()) " ($status)" else ""}."
            else "${orders.size} pedidos:\n" + orders.joinToString("\n") {
                "Pedido #${it.id} | cliente:${it.clientId} | ${it.status} | \$${it.totalArs} ARS | ${it.createdAt.toString().take(16)}"
            }
            CallToolResult(content = listOf(TextContent(text)))
        }

        server.addTool(
            name = "get_order",
            description = "Detalle de un pedido con todos sus items y nombres de productos."
        ) { request ->
            val id = request.arguments!!["orderId"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@addTool errorResult("Falta orderId")
            val o = orderRepo.findById(id) ?: return@addTool errorResult("Pedido #$id no encontrado.")
            val items = o.items.joinToString("\n") {
                val pn = try { productRepo.findById(it.productId)?.name ?: "id=${it.productId}" } catch (_: Exception) { "id=${it.productId}" }
                "  - $pn x${it.quantity} @ \$${it.unitPriceArs} = \$${it.subtotalArs} ARS"
            }
            CallToolResult(content = listOf(TextContent("Pedido #${o.id} | cliente:${o.clientId} | ${o.status} | ${o.createdAt}\nItems:\n$items\nTOTAL: \$${o.totalArs} ARS")))
        }

        server.addTool(
            name = "create_order",
            description = """Crear pedido. clientId + itemsJson (JSON array).
Ejemplo: [{"productId":232,"quantity":2,"unitPrice":"5372.25"}]"""
        ) { request ->
            try {
                val clientId = request.arguments!!["clientId"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@addTool errorResult("Falta clientId")
                val itemsJson = request.arguments!!["itemsJson"]?.jsonPrimitive?.content ?: return@addTool errorResult("Falta itemsJson")
                @kotlinx.serialization.Serializable data class II(val productId: Long, val quantity: Int, val unitPrice: String)
                val parsed = kotlinx.serialization.json.Json.decodeFromString<List<II>>(itemsJson)
                val items = parsed.map { Triple(it.productId, it.quantity, BigDecimal(it.unitPrice)) }
                val order = orderService.createOrder(clientId, items, now())
                CallToolResult(content = listOf(TextContent("OK: Pedido #${order.id} creado | ${order.items.size} items | \$${order.totalArs} ARS\nSiguiente: confirm_order")))
            } catch (e: Exception) { errorResult("Error: ${e.message}") }
        }

        server.addTool(
            name = "confirm_order",
            description = "Confirmar pedido CREATED → CONFIRMED."
        ) { request ->
            val id = request.arguments!!["orderId"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@addTool errorResult("Falta orderId")
            if (orderService.confirmOrder(id)) CallToolResult(content = listOf(TextContent("OK: Pedido #$id confirmado. Siguiente: assemble_order")))
            else errorResult("No se pudo confirmar #$id. Verificar estado con get_order.")
        }

        server.addTool(
            name = "assemble_order",
            description = "Armar pedido CONFIRMED → ASSEMBLED. DESCUENTA STOCK."
        ) { request ->
            val id = request.arguments!!["orderId"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@addTool errorResult("Falta orderId")
            if (orderService.assembleOrder(id, emptyMap())) CallToolResult(content = listOf(TextContent("OK: Pedido #$id armado, stock descontado. Siguiente: invoice_order")))
            else errorResult("No se pudo armar #$id. Verificar stock y estado.")
        }

        server.addTool(
            name = "invoice_order",
            description = "Facturar pedido ASSEMBLED → INVOICED. Registra venta y genera deuda al cliente."
        ) { request ->
            val id = request.arguments!!["orderId"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@addTool errorResult("Falta orderId")
            if (orderService.invoiceOrder(id, now())) CallToolResult(content = listOf(TextContent("OK: Pedido #$id facturado. Venta registrada.")))
            else errorResult("No se pudo facturar #$id. Verificar estado.")
        }

        server.addTool(
            name = "cancel_order",
            description = "Cancelar pedido (devuelve stock si estaba armado). No funciona en INVOICED."
        ) { request ->
            val id = request.arguments!!["orderId"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@addTool errorResult("Falta orderId")
            if (orderService.cancelOrder(id)) CallToolResult(content = listOf(TextContent("OK: Pedido #$id cancelado.")))
            else errorResult("No se pudo cancelar #$id.")
        }

        server.addTool(
            name = "quick_order",
            description = """Procesar pedido completo de una vez: crear + confirmar + armar + facturar.
Descuenta stock y genera deuda. Verificar stock antes de usar."""
        ) { request ->
            try {
                val clientId = request.arguments!!["clientId"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@addTool errorResult("Falta clientId")
                val itemsJson = request.arguments!!["itemsJson"]?.jsonPrimitive?.content ?: return@addTool errorResult("Falta itemsJson")
                @kotlinx.serialization.Serializable data class II(val productId: Long, val quantity: Int, val unitPrice: String)
                val parsed = kotlinx.serialization.json.Json.decodeFromString<List<II>>(itemsJson)
                val items = parsed.map { Triple(it.productId, it.quantity, BigDecimal(it.unitPrice)) }
                val order = orderService.createOrder(clientId, items, now())
                if (!orderService.confirmOrder(order.id)) return@addTool errorResult("Creado #${order.id} pero fallo al confirmar.")
                if (!orderService.assembleOrder(order.id, emptyMap())) return@addTool errorResult("#${order.id} confirmado pero fallo al armar (stock?).")
                if (!orderService.invoiceOrder(order.id, now())) return@addTool errorResult("#${order.id} armado pero fallo al facturar.")
                CallToolResult(content = listOf(TextContent("OK: Pedido #${order.id} procesado completo (creado→confirmado→armado→facturado). Total: \$${order.totalArs} ARS")))
            } catch (e: Exception) { errorResult("Error: ${e.message}") }
        }

        // ═══════════════════════════════════════════════════
        // COTIZACION DOLAR
        // ═══════════════════════════════════════════════════

        server.addTool(
            name = "get_dollar_rate",
            description = "Cotizacion actual del dolar (ARS por USD)."
        ) { _ ->
            val rate = dollarRateRepo.getLatest()
            val text = if (rate != null) "Dolar: \$${rate.rate} ARS por 1 USD (${rate.date})"
            else "No hay cotizacion configurada. Usar set_dollar_rate."
            CallToolResult(content = listOf(TextContent(text)))
        }

        server.addTool(
            name = "set_dollar_rate",
            description = "Actualizar cotizacion del dolar para hoy. Parametro: rate (ej: '1450.00')."
        ) { request ->
            try {
                val rate = request.arguments!!["rate"]?.jsonPrimitive?.content ?: return@addTool errorResult("Falta rate")
                val dr = dollarRateRepo.insert(DollarRate(rate = BigDecimal(rate), date = today()))
                CallToolResult(content = listOf(TextContent("OK: Dolar actualizado a \$${dr.rate} ARS (${dr.date})")))
            } catch (e: Exception) { errorResult("Error: ${e.message}") }
        }

        // ═══════════════════════════════════════════════════
        // FINANZAS
        // ═══════════════════════════════════════════════════

        server.addTool(
            name = "get_movements",
            description = "Ultimos 20 movimientos financieros (SALE, PURCHASE, CLIENT_PAYMENT, SUPPLIER_PAYMENT)."
        ) { _ ->
            val movements = financeService.getAllMovements().take(20)
            val text = if (movements.isEmpty()) "Sin movimientos."
            else "Ultimos ${movements.size} movimientos:\n" + movements.joinToString("\n") { it.formatMovement() }
            CallToolResult(content = listOf(TextContent(text)))
        }

        server.addTool(
            name = "get_client_movements",
            description = "Movimientos financieros de un cliente (cobros y ventas)."
        ) { request ->
            val id = request.arguments!!["clientId"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@addTool errorResult("Falta clientId")
            val client = clientRepo.findById(id)
            val movements = financeService.getClientMovements(id)
            val header = client?.let { "Cliente: ${it.name} | Deuda: \$${it.balance} ARS\n" } ?: ""
            val text = if (movements.isEmpty()) "${header}Sin movimientos."
            else "${header}${movements.size} movimientos:\n" + movements.joinToString("\n") { it.formatMovement() }
            CallToolResult(content = listOf(TextContent(text)))
        }

        server.addTool(
            name = "record_client_payment",
            description = "Registrar cobro a un cliente (reduce su deuda). Params: clientId, amount, description."
        ) { request ->
            try {
                val clientId = request.arguments!!["clientId"]?.jsonPrimitive?.content?.toLongOrNull() ?: return@addTool errorResult("Falta clientId")
                val amount = request.arguments!!["amount"]?.jsonPrimitive?.content ?: return@addTool errorResult("Falta amount")
                val desc = request.arguments!!["description"]?.jsonPrimitive?.content ?: ""
                if (financeService.recordClientPayment(clientId, BigDecimal(amount), now(), desc)) {
                    val c = clientRepo.findById(clientId)
                    CallToolResult(content = listOf(TextContent("OK: Cobro \$$amount ARS registrado.${c?.let { " Deuda de ${it.name}: \$${it.balance} ARS" } ?: ""}")))
                } else errorResult("No se pudo registrar. Verificar cliente.")
            } catch (e: Exception) { errorResult("Error: ${e.message}") }
        }

        server.addTool(
            name = "record_supplier_payment",
            description = "Registrar pago al proveedor (reduce deuda). Params: amount, description."
        ) { request ->
            try {
                val amount = request.arguments!!["amount"]?.jsonPrimitive?.content ?: return@addTool errorResult("Falta amount")
                val desc = request.arguments!!["description"]?.jsonPrimitive?.content ?: ""
                if (financeService.recordSupplierPayment(BigDecimal(amount), now(), desc)) {
                    val s = supplierRepo.get()
                    CallToolResult(content = listOf(TextContent("OK: Pago \$$amount ARS registrado.${s?.let { " Deuda con ${it.name}: \$${it.balance} ARS" } ?: ""}")))
                } else errorResult("No se pudo registrar. Verificar proveedor.")
            } catch (e: Exception) { errorResult("Error: ${e.message}") }
        }

        // ═══════════════════════════════════════════════════
        // PROVEEDOR
        // ═══════════════════════════════════════════════════

        server.addTool(
            name = "get_supplier",
            description = "Info del proveedor: nombre, telefono, deuda."
        ) { _ ->
            val s = supplierRepo.get()
            val text = if (s != null) "Proveedor: ${s.name} | tel: ${s.phone.ifBlank { "-" }} | deuda: \$${s.balance} ARS"
            else "No hay proveedor configurado."
            CallToolResult(content = listOf(TextContent(text)))
        }

        // ═══════════════════════════════════════════════════
        // RESUMEN
        // ═══════════════════════════════════════════════════

        server.addTool(
            name = "get_business_summary",
            description = """Resumen ejecutivo completo: productos, stock, clientes, deudas, proveedor, pedidos pendientes.
Usar cuando preguntan "como va el negocio", "resumen", "estado general"."""
        ) { _ ->
            val products = productRepo.findAll()
            val withStock = products.filter { it.stock > 0 }
            val totalUnits = products.sumOf { it.stock }
            val clients = clientRepo.findAll()
            val totalDebt = clients.sumOf { it.balance }
            val supplier = supplierRepo.get()
            val dollar = dollarRateRepo.getLatest()
            val orders = orderRepo.findAll()
            val pending = orders.filter { it.status != OrderStatus.INVOICED && it.status != OrderStatus.CANCELLED }

            CallToolResult(content = listOf(TextContent("""═══ RESUMEN MOTOPARTES ═══
Productos: ${products.size} total | ${withStock.size} con stock | ${totalUnits} unidades
Dolar: ${dollar?.let { "$${it.rate} (${it.date})" } ?: "No configurado"}
Clientes: ${clients.size} | Deuda total: $${totalDebt} ARS
Proveedor: ${supplier?.let { "${it.name} | Deuda: $${it.balance} ARS" } ?: "No configurado"}
Pedidos pendientes: ${pending.size}${pending.groupBy { it.status }.entries.joinToString("") { " | ${it.key}: ${it.value.size}" }}""")))
        }

        // ═══════════════════════════════════════════════════
        // IMPORTACION CSV
        // ═══════════════════════════════════════════════════

        server.addTool(
            name = "import_products_csv",
            description = """Importar/actualizar productos desde CSV.
Header obligatorio: code/codigo, name/nombre, purchasePrice/precio, purchaseCurrency/moneda.
Opcional: description, stock. Existentes se actualizan por codigo."""
        ) { request ->
            try {
                val csv = request.arguments!!["csvContent"]?.jsonPrimitive?.content ?: return@addTool errorResult("Falta csvContent")
                val result = csvImportService.import(csv)
                val text = "OK: ${result.summary()}" + if (result.errors.isNotEmpty()) "\nErrores: ${result.errors.joinToString("\n")}" else ""
                CallToolResult(content = listOf(TextContent(text)))
            } catch (e: Exception) { errorResult("Error: ${e.message}") }
        }

        server.addTool(
            name = "import_purchase_invoice",
            description = """Importar factura de compra (CSV). Incrementa stock y registra deuda.
Formato: Codigo,Articulo,Cantidad,Precio Unitario,Importe."""
        ) { request ->
            try {
                val csv = request.arguments!!["csvContent"]?.jsonPrimitive?.content ?: return@addTool errorResult("Falta csvContent")
                val invoiceResult = csvImportService.importPurchaseInvoice(csv)
                val msgs = mutableListOf<String>()
                if (invoiceResult.missingProducts.isNotEmpty()) msgs.add("${invoiceResult.missingProducts.size} productos no encontrados: ${invoiceResult.missingProducts.joinToString(", ") { it.code }}")
                if (invoiceResult.items.isNotEmpty()) {
                    val result = purchaseService.registerPurchase(invoiceResult.items, now())
                    msgs.add("Compra registrada: ${result.summary()}")
                }
                if (invoiceResult.errors.isNotEmpty()) msgs.add("Advertencias: ${invoiceResult.errors.joinToString(", ")}")
                CallToolResult(content = listOf(TextContent(msgs.joinToString("\n").ifBlank { "No se importaron items." })))
            } catch (e: Exception) { errorResult("Error: ${e.message}") }
        }

        return server
    }
}

// ── Helpers ──

private fun errorResult(message: String) = CallToolResult(
    content = listOf(TextContent("ERROR: $message")),
    isError = true
)

private fun Product.format() =
    "[id=${id}] ${code} | ${name} | compra: ${purchasePrice} ${purchaseCurrency} | stock: ${stock}"

private fun Client.formatClient() =
    "[id=${id}] ${name} | tel: ${phone.ifBlank { "-" }} | dir: ${address.ifBlank { "-" }} | deuda: \$${balance} ARS"

private fun FinancialMovement.formatMovement() =
    "${type} | \$${amount} ARS | ${date.toString().take(16)} | ${description}"

private fun now(): kotlinx.datetime.LocalDateTime {
    val j = java.time.LocalDateTime.now()
    return kotlinx.datetime.LocalDateTime(j.year, j.monthValue, j.dayOfMonth, j.hour, j.minute, j.second)
}

private fun today(): kotlinx.datetime.LocalDate {
    val j = java.time.LocalDate.now()
    return kotlinx.datetime.LocalDate(j.year, j.monthValue, j.dayOfMonth)
}
