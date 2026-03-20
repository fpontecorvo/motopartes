package org.motopartes.desktop.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.datetime.LocalDateTime
import org.motopartes.desktop.component.SearchField
import org.motopartes.model.Product
import org.motopartes.repository.ProductRepository
import org.motopartes.service.PurchaseService
import java.math.BigDecimal

private fun now(): LocalDateTime {
    val j = java.time.LocalDateTime.now()
    return LocalDateTime(j.year, j.monthValue, j.dayOfMonth, j.hour, j.minute, j.second)
}

@Composable
fun PurchaseScreen(purchaseService: PurchaseService, productRepo: ProductRepository) {
    var allProducts by remember { mutableStateOf(productRepo.findAll()) }
    var searchQuery by remember { mutableStateOf("") }
    var purchaseItems by remember { mutableStateOf<List<Pair<Product, Int>>>(emptyList()) }
    var totalCostText by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    val filteredProducts = remember(searchQuery, allProducts) {
        if (searchQuery.isBlank()) allProducts
        else allProducts.filter { it.code.contains(searchQuery, ignoreCase = true) || it.name.contains(searchQuery, ignoreCase = true) }
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Registrar Compra al Proveedor", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(20.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            ElevatedCard(Modifier.weight(1f)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Buscar producto", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(8.dp))
                    SearchField(searchQuery, { searchQuery = it }, Modifier.fillMaxWidth(), placeholder = "Codigo o nombre...")
                    Spacer(Modifier.height(8.dp))
                    LazyColumn(Modifier.heightIn(max = 300.dp)) {
                        items(filteredProducts, key = { it.id }) { product ->
                            Surface(modifier = Modifier.fillMaxWidth(), onClick = {
                                if (purchaseItems.none { it.first.id == product.id }) purchaseItems = purchaseItems + (product to 1)
                            }) {
                                Row(Modifier.padding(10.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("${product.code} — ${product.name}")
                                    Text("Stock: ${product.stock}", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                        Text("Seleccione productos de la lista", color = MaterialTheme.colorScheme.outline)
                    } else {
                        LazyColumn(Modifier.heightIn(max = 230.dp)) {
                            items(purchaseItems, key = { it.first.id }) { (product, qty) ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("${product.code} — ${product.name}", Modifier.weight(2f))
                                    OutlinedTextField(qty.toString(), { n ->
                                        val q = n.toIntOrNull() ?: return@OutlinedTextField
                                        if (q > 0) purchaseItems = purchaseItems.map { if (it.first.id == product.id) it.first to q else it }
                                    }, modifier = Modifier.width(80.dp), singleLine = true)
                                    IconButton(onClick = { purchaseItems = purchaseItems.filter { it.first.id != product.id } }) {
                                        Icon(Icons.Default.Close, "Quitar", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(totalCostText, { totalCostText = it }, label = { Text("Costo total (ARS)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
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
            val totalCost = totalCostText.toBigDecimalOrNull()
            if (totalCost == null || totalCost <= BigDecimal.ZERO) { message = "Ingrese un costo total valido"; isError = true; return@Button }
            val items = purchaseItems.map { it.first.id to it.second }
            if (purchaseService.registerPurchase(items, totalCost, now())) {
                message = "Compra registrada. Stock actualizado."; isError = false; purchaseItems = emptyList(); totalCostText = ""; allProducts = productRepo.findAll()
            } else { message = "Error. Verifique que el proveedor este configurado."; isError = true }
        }, enabled = purchaseItems.isNotEmpty()) { Text("Registrar Compra") }
    }
}
