package org.motopartes.desktop.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.RequestQuote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDateTime
import org.motopartes.desktop.component.*
import org.motopartes.model.Client
import org.motopartes.model.MovementType
import org.motopartes.repository.ClientRepository
import org.motopartes.repository.SupplierRepository
import org.motopartes.service.FinanceService
import java.math.BigDecimal

private val MovementType.displayName: String
    get() = when (this) {
        MovementType.SALE -> "Venta"
        MovementType.PURCHASE -> "Compra"
        MovementType.CLIENT_PAYMENT -> "Cobro"
        MovementType.SUPPLIER_PAYMENT -> "Pago"
    }

private val MovementType.color: androidx.compose.ui.graphics.Color
    get() = when (this) {
        MovementType.SALE -> androidx.compose.ui.graphics.Color(0xFFEF5350)
        MovementType.PURCHASE -> androidx.compose.ui.graphics.Color(0xFFEF5350)
        MovementType.CLIENT_PAYMENT -> androidx.compose.ui.graphics.Color(0xFF66BB6A)
        MovementType.SUPPLIER_PAYMENT -> androidx.compose.ui.graphics.Color(0xFF66BB6A)
    }

private fun now(): LocalDateTime {
    val j = java.time.LocalDateTime.now()
    return LocalDateTime(j.year, j.monthValue, j.dayOfMonth, j.hour, j.minute, j.second)
}

@Composable
fun FinanceScreen(financeService: FinanceService, clientRepo: ClientRepository, supplierRepo: SupplierRepository) {
    var clients by remember { mutableStateOf(clientRepo.findAll()) }
    var supplier by remember { mutableStateOf(supplierRepo.get()) }
    var movements by remember { mutableStateOf(financeService.getAllMovements()) }
    var showClientPayment by remember { mutableStateOf(false) }
    var showSupplierPayment by remember { mutableStateOf(false) }
    var currentPage by remember { mutableStateOf(0) }
    var pageSize by remember { mutableStateOf(10) }

    fun refresh() {
        clients = clientRepo.findAll(); supplier = supplierRepo.get(); movements = financeService.getAllMovements()
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Finanzas", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(20.dp))

        // Summary cards
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            val totalClientDebt = clients.fold(BigDecimal.ZERO) { acc, c -> acc.add(c.balance) }
            ElevatedCard(Modifier.weight(1f)) {
                Column(Modifier.padding(20.dp)) {
                    Text("Deuda total clientes", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("$${totalClientDebt.toPlainString()}", style = MaterialTheme.typography.headlineMedium, color = if (totalClientDebt > BigDecimal.ZERO) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
            }
            ElevatedCard(Modifier.weight(1f)) {
                Column(Modifier.padding(20.dp)) {
                    Text("Deuda con proveedor", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val bal = supplier?.balance ?: BigDecimal.ZERO
                    Text("$${bal.toPlainString()}", style = MaterialTheme.typography.headlineMedium, color = if (bal > BigDecimal.ZERO) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilledTonalButton(onClick = { showClientPayment = true }) {
                Icon(Icons.Default.RequestQuote, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Registrar Cobro")
            }
            FilledTonalButton(onClick = { showSupplierPayment = true }) {
                Icon(Icons.Default.Payments, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Pago a Proveedor")
            }
        }
        Spacer(Modifier.height(16.dp))

        // Clients with debt
        val clientsWithDebt = clients.filter { it.balance > BigDecimal.ZERO }
        if (clientsWithDebt.isNotEmpty()) {
            Text("Clientes con deuda", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                clientsWithDebt.forEach { client ->
                    SuggestionChip(onClick = {}, label = { Text("${client.name}: \$${client.balance.toPlainString()}") })
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // Movement history
        Text("Historial de Movimientos", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text("Fecha", Modifier.weight(1.5f), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Tipo", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Monto", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Descripcion", Modifier.weight(2f), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        val pagedMovements = movements.paginate(currentPage, pageSize)
        val listState = rememberLazyListState()
        LazyColumn(Modifier.weight(1f), state = listState) {
            if (pagedMovements.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 40.dp), contentAlignment = Alignment.Center) {
                        Text("No hay movimientos registrados", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            items(pagedMovements, key = { it.id }) { m ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(m.date.toString().take(16), Modifier.weight(1.5f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Box(Modifier.weight(1f)) {
                        Surface(shape = MaterialTheme.shapes.small, color = m.type.color.copy(alpha = 0.12f)) {
                            Text(m.type.displayName, Modifier.padding(horizontal = 8.dp, vertical = 3.dp), color = m.type.color, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    Text("$${m.amount.toPlainString()}", Modifier.weight(1f), fontWeight = FontWeight.Medium)
                    Text(m.description, Modifier.weight(2f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        PaginationBar(currentPage, movements.size, pageSize, { currentPage = it }, { pageSize = it; currentPage = 0 }, listState = listState)
    }

    if (showClientPayment) {
        ClientPaymentDialog(clients, onDismiss = { showClientPayment = false }, onConfirm = { clientId, amount, desc ->
            financeService.recordClientPayment(clientId, amount, now(), desc); showClientPayment = false; refresh()
        })
    }
    if (showSupplierPayment) {
        SupplierPaymentDialog(supplier?.name ?: "Proveedor", onDismiss = { showSupplierPayment = false }, onConfirm = { amount, desc ->
            financeService.recordSupplierPayment(amount, now(), desc); showSupplierPayment = false; refresh()
        })
    }
}

@Composable
private fun ClientPaymentDialog(clients: List<Client>, onDismiss: () -> Unit, onConfirm: (Long, BigDecimal, String) -> Unit) {
    var selectedClient by remember { mutableStateOf<Client?>(null) }
    var amountText by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    val clientsWithDebt = remember { clients.filter { it.balance > BigDecimal.ZERO } }

    FormDialog("Registrar Cobro a Cliente", onDismiss, onConfirm = {
        val c = selectedClient ?: return@FormDialog
        val amount = amountText.toBigDecimalOrNull() ?: return@FormDialog
        if (amount <= BigDecimal.ZERO) return@FormDialog
        onConfirm(c.id, amount, description)
    }) {
        Dropdown(
            items = clientsWithDebt,
            selected = selectedClient,
            onSelect = { selectedClient = it },
            label = "Cliente",
            itemLabel = { "${it.name} — deuda: \$${it.balance.toPlainString()}" }
        )
        OutlinedTextField(amountText, { amountText = it }, label = { Text("Monto") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(description, { description = it }, label = { Text("Descripcion (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SupplierPaymentDialog(supplierName: String, onDismiss: () -> Unit, onConfirm: (BigDecimal, String) -> Unit) {
    var amountText by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    FormDialog("Pago a $supplierName", onDismiss, onConfirm = {
        val amount = amountText.toBigDecimalOrNull() ?: return@FormDialog
        if (amount <= BigDecimal.ZERO) return@FormDialog
        onConfirm(amount, description)
    }) {
        OutlinedTextField(amountText, { amountText = it }, label = { Text("Monto") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(description, { description = it }, label = { Text("Descripcion (opcional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
}
