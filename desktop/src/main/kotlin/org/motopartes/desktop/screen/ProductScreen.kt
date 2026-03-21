package org.motopartes.desktop.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.motopartes.desktop.component.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.motopartes.model.Currency
import org.motopartes.model.Product
import org.motopartes.repository.DollarRateRepository
import org.motopartes.repository.ProductRepository
import org.motopartes.service.CsvImportService
import org.motopartes.service.ImportResult
import java.awt.FileDialog
import java.awt.Frame
import java.math.BigDecimal
import kotlin.io.path.Path
import kotlin.io.path.readText

@Composable
fun ProductScreen(productRepo: ProductRepository, dollarRateRepo: DollarRateRepository) {
    var products by remember { mutableStateOf(productRepo.findAll()) }
    var searchQuery by remember { mutableStateOf("") }
    var showForm by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var deletingProduct by remember { mutableStateOf<Product?>(null) }
    var showStockDialog by remember { mutableStateOf<Product?>(null) }
    var currentPage by remember { mutableStateOf(0) }
    var pageSize by remember { mutableStateOf(10) }
    val dollarRate = remember { dollarRateRepo.getLatest()?.rate }
    val csvImportService = remember { CsvImportService(productRepo, dollarRateRepo) }
    var importResult by remember { mutableStateOf<ImportResult?>(null) }
    var isImporting by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        products = if (searchQuery.isBlank()) productRepo.findAll() else productRepo.search(searchQuery)
        currentPage = currentPage.coerceAtMost(totalPages(products.size, pageSize) - 1).coerceAtLeast(0)
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Productos", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            if (dollarRate != null) {
                Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer) {
                    Text("USD: \$${dollarRate.toPlainString()}", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.width(12.dp))
            }
            SearchField(searchQuery, { searchQuery = it; refresh() }, Modifier.width(250.dp))
            Spacer(Modifier.width(12.dp))
            FilledTonalButton(onClick = {
                val dialog = FileDialog(null as Frame?, "Importar CSV", FileDialog.LOAD)
                dialog.setFilenameFilter { _, name -> name.endsWith(".csv") || name.endsWith(".txt") }
                dialog.isVisible = true
                if (dialog.file != null) {
                    val content = Path(dialog.directory, dialog.file).readText()
                    isImporting = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { csvImportService.import(content) }
                        importResult = result
                        isImporting = false
                        refresh()
                    }
                }
            }, enabled = !isImporting) {
                if (isImporting) {
                    CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onSurface)
                } else {
                    Icon(Icons.Default.Upload, null, Modifier.size(18.dp))
                }
                Spacer(Modifier.width(6.dp)); Text(if (isImporting) "Importando..." else "Importar")
            }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = { editingProduct = null; showForm = true }) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Nuevo")
            }
        }

        if (dollarRate == null) {
            Spacer(Modifier.height(8.dp))
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.errorContainer) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("No hay cotizacion del dolar configurada. Configure una en la seccion Cotizacion para poder calcular precios de productos en USD.", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text("Codigo", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Nombre", Modifier.weight(2f), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("P. Compra", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Mon.", Modifier.weight(0.5f), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("P. Venta", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Stock", Modifier.weight(0.5f), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("", Modifier.weight(1.2f))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        val pagedProducts = products.paginate(currentPage, pageSize)
        LazyColumn(Modifier.weight(1f)) {
            items(pagedProducts, key = { it.id }) { product ->
                Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background, onClick = { editingProduct = product; showForm = true }) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(product.code, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text(product.name, Modifier.weight(2f))
                        Text(product.purchasePrice.toPlainString(), Modifier.weight(1f))
                        Text(product.purchaseCurrency.name, Modifier.weight(0.5f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$${product.salePrice.toPlainString()}", Modifier.weight(1f), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                        Text("${product.stock}", Modifier.weight(0.5f), color = if (product.stock == 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                        Row(Modifier.weight(1.2f), horizontalArrangement = Arrangement.End) {
                            IconButton(onClick = { editingProduct = product; showForm = true }) { Icon(Icons.Default.Edit, "Editar", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
                            IconButton(onClick = { showStockDialog = product }) { Icon(Icons.Default.Inventory, "Stock", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(18.dp)) }
                            IconButton(onClick = { deletingProduct = product }) { Icon(Icons.Default.Delete, "Eliminar", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        PaginationBar(currentPage, products.size, pageSize, { currentPage = it }, { pageSize = it; currentPage = 0 })
    }

    if (showForm) {
        ProductFormDialog(editingProduct, dollarRate, onDismiss = { showForm = false }, onSave = { p ->
            if (editingProduct != null) productRepo.update(p) else productRepo.insert(p)
            showForm = false; refresh()
        })
    }
    deletingProduct?.let { p ->
        ConfirmDialog("Eliminar producto", "Eliminar ${p.name} (${p.code})?",
            onConfirm = { productRepo.delete(p.id); deletingProduct = null; refresh() },
            onDismiss = { deletingProduct = null })
    }
    showStockDialog?.let { p ->
        StockDialog(p, onDismiss = { showStockDialog = null }, onConfirm = { delta ->
            productRepo.updateStock(p.id, delta); showStockDialog = null; refresh()
        })
    }
    importResult?.let { result ->
        val hasErrors = result.errors.isNotEmpty()
        val icon = if (hasErrors) Icons.Default.Warning else Icons.Default.CheckCircle
        val iconColor = if (hasErrors) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

        AlertDialog(
            onDismissRequest = { importResult = null },
            icon = { Icon(icon, null, Modifier.size(40.dp), tint = iconColor) },
            title = { Text(if (hasErrors) "Importacion con advertencias" else "Importacion exitosa") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (result.created > 0) {
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)) {
                            Text("  ${result.created} productos creados  ", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                        }
                    }
                    if (result.updated > 0) {
                        Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)) {
                            Text("  ${result.updated} productos actualizados  ", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Medium)
                        }
                    }
                    if (result.skipped > 0) {
                        Text("${result.skipped} filas omitidas (categorias/sin precio)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (result.errors.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text("Errores:", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                        LazyColumn(Modifier.heightIn(max = 150.dp)) {
                            items(result.errors) { err ->
                                Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            },
            confirmButton = { FilledTonalButton(onClick = { importResult = null }) { Text("Cerrar") } }
        )
    }
}

@Composable
private fun ProductFormDialog(product: Product?, dollarRate: BigDecimal?, onDismiss: () -> Unit, onSave: (Product) -> Unit) {
    var code by remember { mutableStateOf(product?.code ?: "") }
    var name by remember { mutableStateOf(product?.name ?: "") }
    var description by remember { mutableStateOf(product?.description ?: "") }
    var purchasePriceText by remember { mutableStateOf(product?.purchasePrice?.toPlainString() ?: "") }
    var purchaseCurrency by remember { mutableStateOf(product?.purchaseCurrency ?: Currency.ARS) }
    var salePriceText by remember { mutableStateOf(product?.salePrice?.toPlainString() ?: "") }
    var salePriceManuallySet by remember { mutableStateOf(product != null) }

    // Auto-calculate sale price when purchase price or currency changes (only if not manually set)
    LaunchedEffect(purchasePriceText, purchaseCurrency) {
        if (salePriceManuallySet) return@LaunchedEffect
        val pp = purchasePriceText.toBigDecimalOrNull() ?: return@LaunchedEffect
        if (purchaseCurrency == Currency.USD && dollarRate == null) return@LaunchedEffect
        val rate = dollarRate ?: BigDecimal.ONE
        salePriceText = Product.defaultSalePrice(pp, purchaseCurrency, rate).toPlainString()
    }

    val noDollarForUsd = purchaseCurrency == Currency.USD && dollarRate == null

    FormDialog(if (product != null) "Editar Producto" else "Nuevo Producto", onDismiss, onConfirm = {
        val pp = purchasePriceText.toBigDecimalOrNull() ?: return@FormDialog
        val sp = salePriceText.toBigDecimalOrNull() ?: return@FormDialog
        if (noDollarForUsd) return@FormDialog
        onSave(Product(id = product?.id ?: 0, code = code, name = name, description = description, purchasePrice = pp, purchaseCurrency = purchaseCurrency, salePrice = sp, stock = product?.stock ?: 0))
    }) {
        OutlinedTextField(code, { code = it }, label = { Text("Codigo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(description, { description = it }, label = { Text("Descripcion") }, modifier = Modifier.fillMaxWidth())

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(purchasePriceText, { purchasePriceText = it; salePriceManuallySet = false }, label = { Text("Precio de compra") }, singleLine = true, modifier = Modifier.weight(1f))
            Column {
                Text("Moneda compra", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Currency.entries.forEach { c ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = purchaseCurrency == c, onClick = { purchaseCurrency = c; salePriceManuallySet = false })
                            Text(c.name)
                        }
                    }
                }
            }
        }

        if (noDollarForUsd) {
            Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.errorContainer) {
                Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Configure la cotizacion del dolar para calcular el precio de venta en ARS.", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        OutlinedTextField(salePriceText, { salePriceText = it; salePriceManuallySet = true }, label = { Text("Precio de venta (ARS)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            supportingText = { Text("Por defecto: costo + 30%") })
    }
}

@Composable
private fun StockDialog(product: Product, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var isAdd by remember { mutableStateOf(true) }

    FormDialog("Ajustar Stock — ${product.name}", onDismiss, onConfirm = {
        val qty = amount.toIntOrNull() ?: return@FormDialog
        onConfirm(if (isAdd) qty else -qty)
    }, confirmText = "Aplicar") {
        Text("Stock actual: ${product.stock}", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = isAdd, onClick = { isAdd = true }); Text("Entrada") }
            Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(selected = !isAdd, onClick = { isAdd = false }); Text("Salida") }
        }
        OutlinedTextField(amount, { amount = it }, label = { Text("Cantidad") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
}
