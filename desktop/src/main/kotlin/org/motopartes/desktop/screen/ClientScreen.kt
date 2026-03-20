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
import org.motopartes.model.Client
import org.motopartes.repository.ClientRepository
import java.math.BigDecimal

@Composable
fun ClientScreen(clientRepo: ClientRepository) {
    var clients by remember { mutableStateOf(clientRepo.findAll()) }
    var searchQuery by remember { mutableStateOf("") }
    var showForm by remember { mutableStateOf(false) }
    var editingClient by remember { mutableStateOf<Client?>(null) }
    var deletingClient by remember { mutableStateOf<Client?>(null) }
    var currentPage by remember { mutableStateOf(0) }
    var pageSize by remember { mutableStateOf(10) }

    fun refresh() {
        clients = if (searchQuery.isBlank()) clientRepo.findAll() else clientRepo.search(searchQuery)
        currentPage = currentPage.coerceAtMost(totalPages(clients.size, pageSize) - 1).coerceAtLeast(0)
    }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Clientes", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            SearchField(searchQuery, { searchQuery = it; refresh() }, Modifier.width(250.dp))
            Spacer(Modifier.width(12.dp))
            FilledTonalButton(onClick = { editingClient = null; showForm = true }) {
                Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Nuevo")
            }
        }
        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text("Nombre", Modifier.weight(2f), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Telefono", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Direccion", Modifier.weight(2f), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Saldo", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("", Modifier.weight(0.8f))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        val pagedClients = clients.paginate(currentPage, pageSize)
        LazyColumn(Modifier.weight(1f)) {
            items(pagedClients, key = { it.id }) { client ->
                Surface(modifier = Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.background, onClick = { editingClient = client; showForm = true }) {
                    Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(client.name, Modifier.weight(2f))
                        Text(client.phone, Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(client.address, Modifier.weight(2f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "$${client.balance.toPlainString()}", Modifier.weight(1f),
                            color = if (client.balance > BigDecimal.ZERO) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                        Row(Modifier.weight(0.8f), horizontalArrangement = Arrangement.End) {
                            IconButton(onClick = { editingClient = client; showForm = true }) { Icon(Icons.Default.Edit, "Editar", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
                            IconButton(onClick = { deletingClient = client }) { Icon(Icons.Default.Delete, "Eliminar", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp)) }
                        }
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        PaginationBar(currentPage, clients.size, pageSize, { currentPage = it }, { pageSize = it; currentPage = 0 })
    }

    if (showForm) {
        ClientFormDialog(editingClient, onDismiss = { showForm = false }, onSave = { c ->
            if (editingClient != null) clientRepo.update(c) else clientRepo.insert(c)
            showForm = false; refresh()
        })
    }
    deletingClient?.let { c ->
        ConfirmDialog("Eliminar cliente", "Eliminar ${c.name}?",
            onConfirm = { clientRepo.delete(c.id); deletingClient = null; refresh() },
            onDismiss = { deletingClient = null })
    }
}

@Composable
private fun ClientFormDialog(client: Client?, onDismiss: () -> Unit, onSave: (Client) -> Unit) {
    var name by remember { mutableStateOf(client?.name ?: "") }
    var phone by remember { mutableStateOf(client?.phone ?: "") }
    var address by remember { mutableStateOf(client?.address ?: "") }

    FormDialog(if (client != null) "Editar Cliente" else "Nuevo Cliente", onDismiss, onConfirm = {
        if (name.isBlank()) return@FormDialog
        onSave(Client(id = client?.id ?: 0, name = name, phone = phone, address = address, balance = client?.balance ?: BigDecimal.ZERO))
    }) {
        OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(phone, { phone = it }, label = { Text("Telefono") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(address, { address = it }, label = { Text("Direccion") }, modifier = Modifier.fillMaxWidth())
    }
}
