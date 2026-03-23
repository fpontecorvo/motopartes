package org.motopartes.desktop.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDateTime
import org.motopartes.desktop.component.*
import org.motopartes.model.*
import org.motopartes.repository.*
import org.motopartes.service.OrderService
import java.math.BigDecimal

private val OrderStatus.displayName: String
    get() = when (this) {
        OrderStatus.CREATED -> "Creado"
        OrderStatus.CONFIRMED -> "Confirmado"
        OrderStatus.ASSEMBLED -> "Armado"
        OrderStatus.INVOICED -> "Facturado"
        OrderStatus.CANCELLED -> "Cancelado"
    }

private val OrderStatus.chipColor: androidx.compose.ui.graphics.Color
    get() = when (this) {
        OrderStatus.CREATED -> androidx.compose.ui.graphics.Color(0xFF90CAF9)
        OrderStatus.CONFIRMED -> androidx.compose.ui.graphics.Color(0xFFFFB74D)
        OrderStatus.ASSEMBLED -> androidx.compose.ui.graphics.Color(0xFFCE93D8)
        OrderStatus.INVOICED -> androidx.compose.ui.graphics.Color(0xFF81C784)
        OrderStatus.CANCELLED -> androidx.compose.ui.graphics.Color(0xFFE57373)
    }

private fun now(): LocalDateTime {
    val j = java.time.LocalDateTime.now()
    return LocalDateTime(j.year, j.monthValue, j.dayOfMonth, j.hour, j.minute, j.second)
}

@Composable
fun OrderScreen(
    orderRepo: OrderRepository,
    orderService: OrderService,
    productRepo: ProductRepository,
    clientRepo: ClientRepository,
    dollarRateRepo: DollarRateRepository,
    settingsRepo: org.motopartes.repository.SettingsRepository
) {
    var orders by remember { mutableStateOf(orderRepo.findAll()) }
    var showCreate by remember { mutableStateOf(false) }
    var selectedOrder by remember { mutableStateOf<Order?>(null) }
    var statusFilter by remember { mutableStateOf<OrderStatus?>(null) }
    var currentPage by remember { mutableStateOf(0) }
    var pageSize by remember { mutableStateOf(10) }

    fun refresh() {
        orders = if (statusFilter != null) orderRepo.findByStatus(statusFilter!!) else orderRepo.findAll()
        selectedOrder = selectedOrder?.let { orderRepo.findById(it.id) }
        currentPage = currentPage.coerceAtMost(org.motopartes.desktop.component.totalPages(orders.size, pageSize) - 1).coerceAtLeast(0)
    }

    if (showCreate) {
        EditOrderView(null, clientRepo, productRepo, orderService, orderRepo, settingsRepo, dollarRateRepo,
            onDone = { showCreate = false; refresh() }, onCancel = { showCreate = false })
        return
    }

    if (selectedOrder != null) {
        if (selectedOrder!!.status == OrderStatus.CREATED) {
            EditOrderView(selectedOrder, clientRepo, productRepo, orderService, orderRepo, settingsRepo, dollarRateRepo,
                onDone = { selectedOrder = null; refresh() }, onCancel = { selectedOrder = null })
        } else {
            OrderDetailView(selectedOrder!!, orderRepo, productRepo, clientRepo, orderService,
                onBack = { selectedOrder = null; refresh() })
        }
        return
    }

    // Order list
    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Ventas", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            FilterChip(selected = statusFilter == null, onClick = { statusFilter = null; refresh() }, label = { Text("Todos") })
            Spacer(Modifier.width(6.dp))
            OrderStatus.entries.forEach { s ->
                FilterChip(selected = statusFilter == s, onClick = { statusFilter = s; refresh() }, label = { Text(s.displayName) })
                Spacer(Modifier.width(6.dp))
            }
            Spacer(Modifier.width(12.dp))
            FilledTonalButton(onClick = { showCreate = true }) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Nueva Venta")
            }
        }
        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text("#", Modifier.weight(0.5f), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Cliente", Modifier.weight(2f), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Fecha", Modifier.weight(1.5f), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Total", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Estado", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        val pagedOrders = orders.paginate(currentPage, pageSize)
        val listState = rememberLazyListState()
        LazyColumn(Modifier.weight(1f), state = listState) {
            items(pagedOrders, key = { it.id }) { order ->
                val clientName = remember(order.clientId) { orderRepo.clientName(order.clientId) ?: "—" }
                Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background,
                    onClick = { selectedOrder = orderRepo.findById(order.id) }) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("${order.id}", Modifier.weight(0.5f))
                        Text(clientName, Modifier.weight(2f))
                        Text(order.createdAt.toString().take(16), Modifier.weight(1.5f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$${order.totalArs.toPlainString()}", Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        Box(Modifier.weight(1f)) {
                            Surface(shape = MaterialTheme.shapes.small, color = order.status.chipColor.copy(alpha = 0.15f)) {
                                Text(order.status.displayName, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = order.status.chipColor, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        org.motopartes.desktop.component.PaginationBar(currentPage, orders.size, pageSize, { currentPage = it }, { pageSize = it; currentPage = 0 }, listState = listState)
    }
}

// --- Create / Edit order (CREATED status) ---

@Composable
private fun EditOrderView(
    existingOrder: Order?,
    clientRepo: ClientRepository,
    productRepo: ProductRepository,
    orderService: OrderService,
    orderRepo: OrderRepository,
    settingsRepo: org.motopartes.repository.SettingsRepository,
    dollarRateRepo: DollarRateRepository,
    onDone: () -> Unit,
    onCancel: () -> Unit
) {
    // Triple: Product, quantity, unitPrice
    val clients = remember { clientRepo.findAll() }
    val allProducts = remember { productRepo.findAll() }
    val dollarRate = remember { dollarRateRepo.getLatest()?.rate ?: java.math.BigDecimal.ONE }
    val markupArs = remember { settingsRepo.getMarkupArs() }
    val markupUsd = remember { settingsRepo.getMarkupUsd() }

    var selectedClient by remember { mutableStateOf(existingOrder?.let { o -> clients.find { c -> c.id == o.clientId } }) }
    var searchQuery by remember { mutableStateOf("") }
    var orderItems by remember {
        mutableStateOf(
            existingOrder?.items?.mapNotNull { item ->
                allProducts.find { p -> p.id == item.productId }?.let { p ->
                    Triple(p, item.quantity, item.unitPriceArs)
                }
            } ?: emptyList<Triple<Product, Int, BigDecimal>>()
        )
    }
    var error by remember { mutableStateOf<String?>(null) }

    val filteredProducts = remember(searchQuery) {
        if (searchQuery.isBlank()) allProducts
        else allProducts.filter { p -> p.code.contains(searchQuery, ignoreCase = true) || p.name.contains(searchQuery, ignoreCase = true) }
    }

    val total = remember(orderItems) {
        orderItems.fold(BigDecimal.ZERO) { acc, (_, qty, price) -> acc.add(price.multiply(BigDecimal(qty))) }
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel) { Icon(Icons.AutoMirrored.Default.ArrowBack, "Volver") }
            Text(if (existingOrder != null) "Editar Venta #${existingOrder.id}" else "Nueva Venta", style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(Modifier.height(16.dp))

        if (existingOrder == null) {
            Dropdown(clients, selectedClient, { selectedClient = it }, "Cliente", { it.name }, Modifier.width(400.dp))
            Spacer(Modifier.height(20.dp))
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            ElevatedCard(Modifier.weight(1f)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Buscar producto", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    SearchField(searchQuery, { searchQuery = it }, Modifier.fillMaxWidth(), placeholder = "Codigo o nombre...")
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.heightIn(max = 250.dp)) {
                        items(filteredProducts, key = { p -> p.id }) { product ->
                            Surface(modifier = Modifier.fillMaxWidth(), onClick = {
                                if (orderItems.none { (p, _, _) -> p.id == product.id }) {
                                    orderItems = orderItems + Triple(product, 1, product.suggestedSalePrice(dollarRate, markupArs, markupUsd))
                                }
                            }) {
                                Row(Modifier.padding(10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${product.code} — ${product.name}", Modifier.weight(1f))
                                    Text("$${product.suggestedSalePrice(dollarRate, markupArs, markupUsd).toPlainString()} | Stock: ${product.stock}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            ElevatedCard(Modifier.weight(1f)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Items dla venta", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.heightIn(max = 250.dp)) {
                        items(orderItems, key = { (p, _, _) -> p.id }) { (product, qty, unitPrice) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("${product.code} — ${product.name}", Modifier.weight(2f))
                                var qtyText by remember(product.id, qty) { mutableStateOf(qty.toString()) }
                                OutlinedTextField(qtyText, { n ->
                                    qtyText = n
                                    val q = n.toIntOrNull()
                                    if (q != null && q > 0) orderItems = orderItems.map { (p, oq, op) -> if (p.id == product.id) Triple(p, q, op) else Triple(p, oq, op) }
                                }, modifier = Modifier.width(70.dp), singleLine = true, label = { Text("Cant.") })
                                var priceText by remember(product.id, unitPrice) { mutableStateOf(unitPrice.toPlainString()) }
                                OutlinedTextField(priceText, { v ->
                                    priceText = v
                                    val p = v.toBigDecimalOrNull()
                                    if (p != null && p >= BigDecimal.ZERO) orderItems = orderItems.map { (prod, oq, op) -> if (prod.id == product.id) Triple(prod, oq, p) else Triple(prod, oq, op) }
                                }, modifier = Modifier.width(110.dp), singleLine = true, label = { Text("Precio") })
                                Text("$${unitPrice.multiply(BigDecimal(qty)).toPlainString()}", Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
                                IconButton(onClick = { orderItems = orderItems.filter { (p, _, _) -> p.id != product.id } }) {
                                    Icon(Icons.Default.Close, "Quitar", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))
                    Text("Total: $${total.toPlainString()}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }

        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = {
                val client = selectedClient
                if (client == null) { error = "Seleccione un cliente"; return@Button }
                if (orderItems.isEmpty()) { error = "Agregue al menos un producto"; return@Button }
                try {
                    val items = orderItems.map { (p, q, price) -> Triple(p.id, q, price) }
                    if (existingOrder != null) {
                        orderService.updateOrderItems(existingOrder.id, items)
                    } else {
                        orderService.createOrder(client.id, items, now())
                    }
                    onDone()
                } catch (e: Exception) { error = e.message }
            }) { Text(if (existingOrder != null) "Guardar Cambios" else "Crear Pedido") }

            if (existingOrder != null) {
                var showConfirmDialog by remember { mutableStateOf(false) }
                var showCancelDialog by remember { mutableStateOf(false) }

                FilledTonalButton(onClick = { showConfirmDialog = true }) {
                    Icon(Icons.Default.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Confirmar Venta")
                }
                OutlinedButton(onClick = { showCancelDialog = true }, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Icon(Icons.Default.Cancel, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Cancelar Venta")
                }

                if (showConfirmDialog) {
                    ConfirmDialog("Confirmar venta", "Confirmar venta #${existingOrder.id}? Se guardaran los items actuales y quedaran bloqueados.",
                        onConfirm = {
                            try {
                                val items = orderItems.map { (p, q, price) -> Triple(p.id, q, price) }
                                orderService.updateOrderItems(existingOrder.id, items)
                                if (orderService.confirmOrder(existingOrder.id)) onDone()
                                else error = "No se pudo confirmar."
                            } catch (e: Exception) { error = e.message }
                            showConfirmDialog = false
                        }, onDismiss = { showConfirmDialog = false })
                }
                if (showCancelDialog) {
                    ConfirmDialog("Cancelar venta", "Cancelar venta #${existingOrder.id}?",
                        onConfirm = {
                            if (orderService.cancelOrder(existingOrder.id)) onDone()
                            else error = "No se pudo cancelar."
                            showCancelDialog = false
                        }, onDismiss = { showCancelDialog = false })
                }
            }
        }
    }
}

// --- Order detail with status actions ---

@Composable
private fun OrderDetailView(
    order: Order,
    orderRepo: OrderRepository,
    productRepo: ProductRepository,
    clientRepo: ClientRepository,
    orderService: OrderService,
    onBack: () -> Unit
) {
    var currentOrder by remember { mutableStateOf(order) }
    var error by remember { mutableStateOf<String?>(null) }
    var showAssembleDialog by remember { mutableStateOf(false) }
    var showConfirmAction by remember { mutableStateOf(false) }
    var showInvoiceAction by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }
    var showDocument by remember { mutableStateOf<String?>(null) }

    val clientName = remember(order.clientId) { orderRepo.clientName(order.clientId) ?: "—" }
    val clientInfo = remember(order.clientId) { clientRepo.findById(order.clientId) }

    fun refreshOrder() { currentOrder = orderRepo.findById(currentOrder.id)!! }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Default.ArrowBack, "Volver") }
            Text("Venta #${currentOrder.id}", style = MaterialTheme.typography.headlineMedium)
        }
        Spacer(Modifier.height(16.dp))

        ElevatedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(32.dp)) {
                    Column { Text("Cliente", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(clientName, style = MaterialTheme.typography.titleMedium) }
                    Column { Text("Fecha", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(currentOrder.createdAt.toString().take(16)) }
                    Column {
                        Text("Estado", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Surface(shape = MaterialTheme.shapes.small, color = currentOrder.status.chipColor.copy(alpha = 0.15f)) {
                            Text(currentOrder.status.displayName, Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = currentOrder.status.chipColor, style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    Column { Text("Total", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text("$${currentOrder.totalArs.toPlainString()}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when (currentOrder.status) {
                OrderStatus.CREATED -> {
                    Button(onClick = { showConfirmAction = true }) { Icon(Icons.Default.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Confirmar") }
                }
                OrderStatus.CONFIRMED -> {
                    FilledTonalButton(onClick = { showDocument = "remito" }) { Icon(Icons.Default.Print, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Ver Remito") }
                    Button(onClick = { showAssembleDialog = true }) { Icon(Icons.Default.Inventory, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Armar Venta") }
                }
                OrderStatus.ASSEMBLED -> {
                    Button(onClick = { showInvoiceAction = true }) { Icon(Icons.Default.Receipt, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Facturar") }
                }
                OrderStatus.INVOICED -> {
                    FilledTonalButton(onClick = { showDocument = "factura" }) { Icon(Icons.Default.Print, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Ver Factura") }
                }
                OrderStatus.CANCELLED -> {}
            }
            if (currentOrder.status != OrderStatus.INVOICED && currentOrder.status != OrderStatus.CANCELLED) {
                OutlinedButton(onClick = { showCancelDialog = true }, colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                    Icon(Icons.Default.Cancel, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Cancelar Venta")
                }
            }
        }
        error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(20.dp))

        Text("Items", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text("Producto", Modifier.weight(2f), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Cantidad", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Precio Unit.", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Subtotal", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        LazyColumn {
            items(currentOrder.items, key = { it.id }) { item ->
                val pName = remember(item.productId) { productRepo.findById(item.productId)?.let { "${it.code} — ${it.name}" } ?: "—" }
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
                    Text(pName, Modifier.weight(2f))
                    Text("${item.quantity}", Modifier.weight(1f))
                    Text("$${item.unitPriceArs.toPlainString()}", Modifier.weight(1f))
                    Text("$${item.subtotalArs.toPlainString()}", Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }

    if (showConfirmAction) {
        ConfirmDialog("Confirmar venta", "Confirmar venta #${currentOrder.id}? Los items quedan bloqueados.",
            onConfirm = {
                if (orderService.confirmOrder(currentOrder.id)) { refreshOrder(); error = null } else error = "No se pudo confirmar."
                showConfirmAction = false
            }, onDismiss = { showConfirmAction = false })
    }

    if (showAssembleDialog) {
        AssembleDialog(currentOrder, productRepo, onDismiss = { showAssembleDialog = false }, onConfirm = { quantities ->
            if (orderService.assembleOrder(currentOrder.id, quantities)) { refreshOrder(); error = null }
            else error = "No se pudo armar. Verifique stock disponible."
            showAssembleDialog = false
        })
    }

    if (showInvoiceAction) {
        showDocument = "factura"
        showInvoiceAction = false
    }

    if (showCancelDialog) {
        val msg = if (currentOrder.status == OrderStatus.ASSEMBLED)
            "Cancelar venta #${currentOrder.id}? Se revertira el stock descontado."
        else
            "Cancelar venta #${currentOrder.id}?"
        ConfirmDialog("Cancelar venta", msg,
            onConfirm = {
                if (orderService.cancelOrder(currentOrder.id)) { refreshOrder(); error = null }
                else error = "No se pudo cancelar."
                showCancelDialog = false
            }, onDismiss = { showCancelDialog = false })
    }

    showDocument?.let { docType ->
        DocumentDialog(
            type = docType,
            order = currentOrder,
            clientName = clientName,
            clientInfo = clientInfo,
            productRepo = productRepo,
            onDismiss = { showDocument = null },
            onInvoice = if (docType == "factura" && currentOrder.status == OrderStatus.ASSEMBLED) {
                {
                    if (orderService.invoiceOrder(currentOrder.id, now())) { refreshOrder(); error = null }
                    else error = "No se pudo facturar."
                    showDocument = null
                }
            } else null
        )
    }
}

@Composable
private fun AssembleDialog(
    order: Order,
    productRepo: ProductRepository,
    onDismiss: () -> Unit,
    onConfirm: (Map<Long, Int>) -> Unit
) {
    var quantities by remember {
        mutableStateOf(order.items.associate { it.productId to it.quantity })
    }

    FormDialog("Armar Venta #${order.id}", onDismiss, onConfirm = { onConfirm(quantities) }, confirmText = "Confirmar Armado") {
        Text("Ajuste las cantidades segun disponibilidad:", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(8.dp))
        order.items.forEach { item ->
            val product = remember(item.productId) { productRepo.findById(item.productId) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(2f)) {
                    Text("${product?.code ?: "—"} — ${product?.name ?: ""}")
                    Text("Stock disponible: ${product?.stock ?: 0}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Pedido: ${item.quantity}", Modifier.weight(0.8f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(
                    value = (quantities[item.productId] ?: 0).toString(),
                    onValueChange = { v ->
                        val q = v.toIntOrNull() ?: return@OutlinedTextField
                        if (q >= 0) quantities = quantities + (item.productId to q)
                    },
                    label = { Text("Armado") },
                    modifier = Modifier.width(100.dp),
                    singleLine = true
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun DocumentDialog(
    type: String,
    order: Order,
    clientName: String,
    clientInfo: Client?,
    productRepo: ProductRepository,
    onDismiss: () -> Unit,
    onInvoice: (() -> Unit)?
) {
    val title = if (type == "remito") "Remito" else "Factura"
    val products = remember {
        order.items.associate { item -> item.productId to productRepo.findById(item.productId)!! }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$title — Pedido #${order.id}") },
        text = {
            Column(Modifier.width(500.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                HorizontalDivider()
                Text("Cliente: $clientName")
                if (clientInfo != null) {
                    if (clientInfo.phone.isNotBlank()) Text("Tel: ${clientInfo.phone}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (clientInfo.address.isNotBlank()) Text("Dir: ${clientInfo.address}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Fecha: ${order.createdAt.toString().take(16)}")
                HorizontalDivider()
                Spacer(Modifier.height(4.dp))

                Row(Modifier.fillMaxWidth()) {
                    Text("Codigo", Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    Text("Descripcion", Modifier.weight(2f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    Text("Cant.", Modifier.weight(0.5f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    if (type == "factura") {
                        Text("P.Unit", Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        Text("Subtotal", Modifier.weight(1f), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    }
                }
                HorizontalDivider()

                order.items.forEach { item ->
                    val product = products[item.productId]
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(product?.code ?: "—", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        Text(product?.name ?: "—", Modifier.weight(2f), style = MaterialTheme.typography.bodySmall)
                        Text("${item.quantity}", Modifier.weight(0.5f), style = MaterialTheme.typography.bodySmall)
                        if (type == "factura") {
                            Text("$${item.unitPriceArs.toPlainString()}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            Text("$${item.subtotalArs.toPlainString()}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                HorizontalDivider()
                if (type == "factura") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        Text("TOTAL: $${order.totalArs.toPlainString()}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (onInvoice != null) {
                    Button(onClick = onInvoice) { Text("Facturar") }
                }
                FilledTonalButton(onClick = {
                    val pdf = if (type == "remito")
                        org.motopartes.desktop.print.DocumentGenerator.generateRemito(order, clientName, clientInfo, products)
                    else
                        org.motopartes.desktop.print.DocumentGenerator.generateFactura(order, clientName, clientInfo, products)
                    org.motopartes.desktop.print.DocumentGenerator.savePdfDialog(pdf, "${title}_${order.id}.pdf")
                }) { Icon(Icons.Default.Download, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Descargar PDF") }

                FilledTonalButton(onClick = {
                    val pdf = if (type == "remito")
                        org.motopartes.desktop.print.DocumentGenerator.generateRemito(order, clientName, clientInfo, products)
                    else
                        org.motopartes.desktop.print.DocumentGenerator.generateFactura(order, clientName, clientInfo, products)
                    org.motopartes.desktop.print.DocumentGenerator.printPdf(pdf)
                }) { Icon(Icons.Default.Print, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Imprimir") }

                TextButton(onClick = onDismiss) { Text("Cerrar") }
            }
        },
        dismissButton = {}
    )
}
