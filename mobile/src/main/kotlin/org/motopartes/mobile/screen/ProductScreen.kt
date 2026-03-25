package org.motopartes.mobile.screen

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.motopartes.mobile.api.ApiClient
import org.motopartes.mobile.api.ProductResponse

@Composable
fun ProductScreen(api: ApiClient) {
    var query by remember { mutableStateOf("") }
    var products by remember { mutableStateOf<List<ProductResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var stockProduct by remember { mutableStateOf<ProductResponse?>(null) }
    val scope = rememberCoroutineScope()

    fun search() {
        isLoading = true
        error = null
        scope.launch {
            val result = if (query.isBlank()) api.getProducts() else api.searchProducts(query)
            result.fold(
                onSuccess = { products = it; error = null },
                onFailure = { error = it.message }
            )
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { search() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Productos", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Buscar por codigo o nombre...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = { search() }, modifier = Modifier.fillMaxWidth(), enabled = !isLoading) {
            Text("Buscar")
        }
        Spacer(Modifier.height(12.dp))

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
        }

        if (isLoading) {
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Text(
                "${products.size} productos",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(products, key = { it.id }) { product ->
                    ProductCard(product, onStockTap = { stockProduct = product })
                }
            }
        }
    }

    stockProduct?.let { product ->
        StockDialog(
            product = product,
            onDismiss = { stockProduct = null },
            onAdjust = { delta ->
                scope.launch {
                    api.adjustStock(product.id, delta).fold(
                        onSuccess = { updated ->
                            products = products.map { if (it.id == updated.id) updated else it }
                            stockProduct = null
                        },
                        onFailure = { error = it.message; stockProduct = null }
                    )
                }
            }
        )
    }
}

@Composable
private fun ProductCard(product: ProductResponse, onStockTap: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Inventory2, null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    product.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    product.code,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "$${product.purchasePrice}",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    product.purchaseCurrency,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Surface(
                    onClick = onStockTap,
                    shape = MaterialTheme.shapes.small,
                    color = if (product.stock > 0) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Default.Inventory, null, Modifier.size(14.dp))
                        Text(
                            "${product.stock}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StockDialog(
    product: ProductResponse,
    onDismiss: () -> Unit,
    onAdjust: (Int) -> Unit
) {
    var deltaText by remember { mutableStateOf("") }
    var isAdding by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajustar stock") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "${product.name} (${product.code})",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
                Text("Stock actual: ${product.stock}", style = MaterialTheme.typography.bodyMedium)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = isAdding,
                        onClick = { isAdding = true },
                        label = { Text("Entrada") },
                        leadingIcon = if (isAdding) {{ Icon(Icons.Default.Add, null, Modifier.size(16.dp)) }} else null
                    )
                    FilterChip(
                        selected = !isAdding,
                        onClick = { isAdding = false },
                        label = { Text("Salida") },
                        leadingIcon = if (!isAdding) {{ Icon(Icons.Default.Remove, null, Modifier.size(16.dp)) }} else null
                    )
                }

                OutlinedTextField(
                    value = deltaText,
                    onValueChange = { deltaText = it },
                    label = { Text("Cantidad") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                val delta = deltaText.toIntOrNull()
                if (delta != null && delta > 0) {
                    val newStock = product.stock + if (isAdding) delta else -delta
                    Text(
                        "Stock resultante: $newStock",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (newStock >= 0) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            val delta = deltaText.toIntOrNull()
            val actualDelta = if (delta != null && delta > 0) {
                if (isAdding) delta else -delta
            } else null

            Button(
                onClick = { actualDelta?.let { onAdjust(it) } },
                enabled = actualDelta != null && (product.stock + actualDelta) >= 0
            ) { Text("Confirmar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
