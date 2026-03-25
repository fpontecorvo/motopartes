package org.motopartes.mobile.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.motopartes.mobile.api.ApiClient
import org.motopartes.mobile.api.ClientPaymentRequest
import org.motopartes.mobile.api.ClientResponse

@Composable
fun ClientScreen(api: ApiClient) {
    var query by remember { mutableStateOf("") }
    var clients by remember { mutableStateOf<List<ClientResponse>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showPayment by remember { mutableStateOf<ClientResponse?>(null) }
    val scope = rememberCoroutineScope()

    fun load() {
        isLoading = true
        error = null
        scope.launch {
            val result = if (query.isBlank()) api.getClients() else api.searchClients(query)
            result.fold(
                onSuccess = { clients = it },
                onFailure = { error = it.message }
            )
            isLoading = false
        }
    }

    LaunchedEffect(Unit) { load() }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Clientes", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Buscar cliente...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = { load() }, modifier = Modifier.fillMaxWidth(), enabled = !isLoading) {
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
                "${clients.size} clientes",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(clients, key = { it.id }) { client ->
                    ClientCard(client, onPayment = { showPayment = client })
                }
            }
        }
    }

    showPayment?.let { client ->
        PaymentDialog(
            clientName = client.name,
            currentBalance = client.balance,
            onDismiss = { showPayment = null },
            onConfirm = { amount, description ->
                scope.launch {
                    api.recordClientPayment(
                        ClientPaymentRequest(client.id, amount, description)
                    )
                    showPayment = null
                    load()
                }
            }
        )
    }
}

@Composable
private fun ClientCard(client: ClientResponse, onPayment: () -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.People, null,
                modifier = Modifier.size(32.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    client.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (client.phone.isNotBlank()) {
                    Text(client.phone, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                val balance = client.balance.toBigDecimalOrNull() ?: java.math.BigDecimal.ZERO
                Text(
                    "$${client.balance}",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (balance > java.math.BigDecimal.ZERO) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
                if (balance > java.math.BigDecimal.ZERO) {
                    TextButton(onClick = onPayment, contentPadding = PaddingValues(0.dp)) {
                        Text("Cobrar", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun PaymentDialog(
    clientName: String,
    currentBalance: String,
    onDismiss: () -> Unit,
    onConfirm: (amount: String, description: String) -> Unit
) {
    var amount by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cobro a $clientName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Deuda actual: $$currentBalance ARS", style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Monto") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripcion (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { if (amount.isNotBlank()) onConfirm(amount, description) },
                enabled = amount.toBigDecimalOrNull() != null && amount.toBigDecimalOrNull()!! > java.math.BigDecimal.ZERO
            ) { Text("Cobrar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
