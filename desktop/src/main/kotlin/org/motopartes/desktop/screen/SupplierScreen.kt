package org.motopartes.desktop.screen

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.motopartes.desktop.component.FormDialog
import org.motopartes.model.Supplier
import org.motopartes.repository.SupplierRepository
import java.math.BigDecimal

@Composable
fun SupplierScreen(supplierRepo: SupplierRepository) {
    var supplier by remember { mutableStateOf(supplierRepo.get()) }
    var editing by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(24.dp)) {
        Text("Proveedor", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(20.dp))

        if (supplier == null) {
            ElevatedCard(Modifier.fillMaxWidth(0.5f)) {
                Column(Modifier.padding(24.dp)) {
                    Text("No hay proveedor configurado.", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { editing = true }) { Text("Configurar Proveedor") }
                }
            }
        } else {
            val s = supplier!!
            ElevatedCard(Modifier.fillMaxWidth(0.5f)) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column {
                        Text("Nombre", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(s.name, style = MaterialTheme.typography.titleLarge)
                    }
                    Column {
                        Text("Telefono", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(s.phone.ifBlank { "—" }, style = MaterialTheme.typography.bodyLarge)
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column {
                        Text("Saldo a pagar", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "$${s.balance.toPlainString()}",
                            style = MaterialTheme.typography.headlineSmall,
                            color = if (s.balance > BigDecimal.ZERO) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = { editing = true }) {
                Icon(Icons.Default.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Editar")
            }
        }
    }

    if (editing) {
        SupplierFormDialog(supplier, onDismiss = { editing = false }, onSave = { s ->
            if (supplier != null) supplierRepo.update(s) else supplierRepo.insert(s)
            supplier = supplierRepo.get(); editing = false
        })
    }
}

@Composable
private fun SupplierFormDialog(supplier: Supplier?, onDismiss: () -> Unit, onSave: (Supplier) -> Unit) {
    var name by remember { mutableStateOf(supplier?.name ?: "") }
    var phone by remember { mutableStateOf(supplier?.phone ?: "") }

    FormDialog(if (supplier != null) "Editar Proveedor" else "Configurar Proveedor", onDismiss, onConfirm = {
        if (name.isBlank()) return@FormDialog
        onSave(Supplier(id = supplier?.id ?: 0, name = name, phone = phone, balance = supplier?.balance ?: BigDecimal.ZERO))
    }) {
        OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(phone, { phone = it }, label = { Text("Telefono") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
}
