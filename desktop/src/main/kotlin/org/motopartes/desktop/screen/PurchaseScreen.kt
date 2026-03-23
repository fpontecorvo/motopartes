package org.motopartes.desktop.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime
import org.motopartes.desktop.component.SearchField
import org.motopartes.model.Currency
import org.motopartes.model.Product
import org.motopartes.repository.DollarRateRepository
import org.motopartes.repository.ProductRepository
import org.motopartes.service.CsvImportService
import org.motopartes.service.MissingProduct
import org.motopartes.service.PurchaseInvoiceResult
import org.motopartes.service.PurchaseItem
import org.motopartes.service.PurchaseService
import java.awt.FileDialog
import java.awt.Frame
import java.math.BigDecimal
import kotlin.io.path.Path
import kotlin.io.path.readText

private fun now(): LocalDateTime {
    val j = java.time.LocalDateTime.now()
    return LocalDateTime(j.year, j.monthValue, j.dayOfMonth, j.hour, j.minute, j.second)
}

private data class PurchaseScreenEntry(val product: Product, val quantity: Int, val unitCost: BigDecimal)

@Composable
fun PurchaseScreen(purchaseService: PurchaseService, productRepo: ProductRepository, dollarRateRepo: DollarRateRepository) {
    var allProducts by remember { mutableStateOf(productRepo.findAll()) }
    var searchQuery by remember { mutableStateOf("") }
    var purchaseItems by remember { mutableStateOf<List<PurchaseScreenEntry>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var purchaseResultDialog by remember { mutableStateOf<org.motopartes.service.PurchaseResult?>(null) }
    val csvImportService = remember { CsvImportService(productRepo, dollarRateRepo) }
    var isImporting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    // State for missing products dialog
    var pendingInvoiceResult by remember { mutableStateOf<PurchaseInvoiceResult?>(null) }
    var pendingCsvContent by remember { mutableStateOf<String?>(null) }

    val filteredProducts = remember(searchQuery, allProducts) {
        if (searchQuery.isBlank()) allProducts
        else allProducts.filter { p -> p.code.contains(searchQuery, ignoreCase = true) || p.name.contains(searchQuery, ignoreCase = true) }
    }

    val totalCost = remember(purchaseItems) {
        purchaseItems.fold(BigDecimal.ZERO) { acc, e -> acc.add(e.unitCost.multiply(BigDecimal(e.quantity))) }
    }

    fun loadInvoiceResult(result: PurchaseInvoiceResult) {
        val newEntries = result.items.mapNotNull { pi ->
            productRepo.findById(pi.productId)?.let { PurchaseScreenEntry(it, pi.quantity, pi.unitCost) }
        }
        purchaseItems = newEntries
        if (result.errors.isNotEmpty()) {
            message = "${newEntries.size} items cargados, ${result.errors.size} errores"; isError = true
        } else {
            message = "${newEntries.size} items cargados desde factura"; isError = false
        }
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Registrar Compra al Proveedor", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            FilledTonalButton(onClick = {
                val dialog = FileDialog(null as Frame?, "Importar Factura CSV", FileDialog.LOAD)
                dialog.setFilenameFilter { _, name -> name.endsWith(".csv") || name.endsWith(".txt") }
                dialog.isVisible = true
                if (dialog.file != null) {
                    val content = Path(dialog.directory, dialog.file).readText()
                    isImporting = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { csvImportService.importPurchaseInvoice(content) }
                        isImporting = false
                        if (result.missingProducts.isNotEmpty()) {
                            pendingInvoiceResult = result
                            pendingCsvContent = content
                        } else {
                            loadInvoiceResult(result)
                        }
                    }
                }
            }, enabled = !isImporting) {
                if (isImporting) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurface)
                } else {
                    Icon(Icons.Default.Upload, null, Modifier.size(18.dp))
                }
                Spacer(Modifier.width(6.dp)); Text(if (isImporting) "Procesando..." else "Importar Factura")
            }
        }
        Spacer(Modifier.height(20.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            ElevatedCard(Modifier.weight(1f)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Buscar producto", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    SearchField(searchQuery, { searchQuery = it }, Modifier.fillMaxWidth(), placeholder = "Codigo o nombre...")
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        items(filteredProducts, key = { p -> p.id }) { product ->
                            Surface(modifier = Modifier.fillMaxWidth(), onClick = {
                                if (purchaseItems.none { e -> e.product.id == product.id }) {
                                    purchaseItems = purchaseItems + PurchaseScreenEntry(product, 1, product.purchasePrice)
                                }
                            }) {
                                Row(Modifier.padding(10.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("${product.code} — ${product.name}".take(60), Modifier.weight(1f), maxLines = 1, style = MaterialTheme.typography.bodySmall)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Stock: ${product.stock}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }

            ElevatedCard(Modifier.weight(1f)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Items a comprar", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    if (purchaseItems.isEmpty()) {
                        Text("Seleccione productos o importe una factura CSV", color = MaterialTheme.colorScheme.outline)
                    } else {
                        LazyColumn(Modifier.heightIn(max = 250.dp)) {
                            items(purchaseItems, key = { e -> e.product.id }) { entry ->
                                val label = "${entry.product.code} — ${entry.product.name}".take(60)
                                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(label, Modifier.weight(3f), maxLines = 1, style = MaterialTheme.typography.bodySmall)
                                    var qtyText by remember(entry.product.id, entry.quantity) { mutableStateOf(entry.quantity.toString()) }
                                    OutlinedTextField(qtyText, { n ->
                                        qtyText = n
                                        val q = n.toIntOrNull()
                                        if (q != null && q > 0) purchaseItems = purchaseItems.map { e -> if (e.product.id == entry.product.id) e.copy(quantity = q) else e }
                                    }, modifier = Modifier.width(70.dp), singleLine = true, label = { Text("Cant.") })
                                    var costText by remember(entry.product.id, entry.unitCost) { mutableStateOf(entry.unitCost.toPlainString()) }
                                    OutlinedTextField(costText, { v ->
                                        costText = v
                                        val p = v.toBigDecimalOrNull()
                                        if (p != null && p >= BigDecimal.ZERO) purchaseItems = purchaseItems.map { e -> if (e.product.id == entry.product.id) e.copy(unitCost = p) else e }
                                    }, modifier = Modifier.width(110.dp), singleLine = true, label = { Text("P.Unit") })
                                    Text("$${entry.unitCost.multiply(BigDecimal(entry.quantity)).toPlainString()}", Modifier.weight(1f), color = MaterialTheme.colorScheme.primary)
                                    IconButton(onClick = { purchaseItems = purchaseItems.filter { e -> e.product.id != entry.product.id } }) {
                                        Icon(Icons.Default.Close, "Quitar", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        Text("Total factura: $${totalCost.toPlainString()}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        message?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }

        Spacer(Modifier.height(20.dp))
        Button(onClick = {
            if (purchaseItems.isEmpty()) { message = "Agregue al menos un producto"; isError = true; return@Button }
            val items = purchaseItems.map { e -> PurchaseItem(e.product.id, e.quantity, e.unitCost) }
            val result = purchaseService.registerPurchase(items, now(), "Compra al proveedor")
            purchaseResultDialog = result
            if (result.errors.isEmpty()) {
                purchaseItems = emptyList()
            }
            allProducts = productRepo.findAll()
        }, enabled = purchaseItems.isNotEmpty()) { Text("Registrar Compra") }
    }

    // Purchase result dialog
    purchaseResultDialog?.let { result ->
        val hasErrors = result.errors.isNotEmpty()
        AlertDialog(
            onDismissRequest = { purchaseResultDialog = null },
            icon = {
                Icon(
                    if (hasErrors) Icons.Default.Warning else Icons.Default.CheckCircle, null,
                    Modifier.size(40.dp),
                    tint = if (hasErrors) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            },
            title = { Text(if (hasErrors) "Compra con advertencias" else "Compra registrada") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)) {
                        Text("  ${result.itemCount} items — Total: $${result.totalCost.toPlainString()}  ", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                    }
                    if (result.errors.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        result.errors.forEach { err ->
                            Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            },
            confirmButton = { FilledTonalButton(onClick = { purchaseResultDialog = null }) { Text("Cerrar") } }
        )
    }

    // Missing products dialog
    pendingInvoiceResult?.let { result ->
        MissingProductsDialog(
            missingProducts = result.missingProducts,
            dollarRateRepo = dollarRateRepo,
            onCreateAndContinue = { createdProducts ->
                // Insert created products
                createdProducts.forEach { productRepo.insert(it) }
                allProducts = productRepo.findAll()
                // Re-import the CSV to pick up the new products
                val reResult = csvImportService.importPurchaseInvoice(pendingCsvContent!!)
                loadInvoiceResult(reResult)
                pendingInvoiceResult = null
                pendingCsvContent = null
            },
            onSkip = {
                // Load only the items that were found
                loadInvoiceResult(result)
                pendingInvoiceResult = null
                pendingCsvContent = null
            },
            onDismiss = {
                pendingInvoiceResult = null
                pendingCsvContent = null
            }
        )
    }
}

@Composable
private fun MissingProductsDialog(
    missingProducts: List<MissingProduct>,
    dollarRateRepo: DollarRateRepository,
    onCreateAndContinue: (List<Product>) -> Unit,
    onSkip: () -> Unit,
    onDismiss: () -> Unit
) {
    val dollarRate = remember { dollarRateRepo.getLatest()?.rate }

    // State: list of (MissingProduct, selected: Boolean, currency: Currency)
    var entries by remember {
        mutableStateOf(missingProducts.map { mp ->
            Triple(mp, true, Currency.ARS) // default selected, ARS
        })
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${missingProducts.size} productos no encontrados") },
        text = {
            Column(Modifier.widthIn(min = 500.dp, max = 800.dp).heightIn(max = 450.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "La factura tiene productos que no estan en la base de datos. Selecciona los que queres crear:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))

                LazyColumn(Modifier.weight(1f)) {
                    items(entries.size) { idx ->
                        val (mp, selected, currency) = entries[idx]
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = selected,
                                onCheckedChange = { checked ->
                                    entries = entries.toMutableList().also { it[idx] = Triple(mp, checked, currency) }
                                },
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(mp.code, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                Text(mp.name.take(50), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text("$${mp.invoiceUnitCost.toPlainString()}", style = MaterialTheme.typography.bodySmall)
                            Spacer(Modifier.width(12.dp))
                            FilterChip(
                                selected = currency == Currency.ARS,
                                onClick = { entries = entries.toMutableList().also { it[idx] = Triple(mp, selected, Currency.ARS) } },
                                label = { Text("ARS") }
                            )
                            Spacer(Modifier.width(4.dp))
                            FilterChip(
                                selected = currency == Currency.USD,
                                onClick = { entries = entries.toMutableList().also { it[idx] = Triple(mp, selected, Currency.USD) } },
                                label = { Text("USD") }
                            )
                        }
                        if (idx < entries.size - 1) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val selectedCount = entries.count { it.second }
                Button(onClick = {
                    val products = entries.filter { it.second }.map { (mp, _, currency) ->
                        Product(
                            code = mp.code,
                            name = mp.name,
                            purchasePrice = mp.invoiceUnitCost,
                            purchaseCurrency = currency
                        )
                    }
                    onCreateAndContinue(products)
                }, enabled = selectedCount > 0) {
                    Text("Crear $selectedCount productos y continuar")
                }
                TextButton(onClick = onSkip) { Text("Omitir faltantes") }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
